package com.example.confluencemcpserver

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.util.StringUtils
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

@Service
class ConfluenceClient(
    builder: RestClient.Builder,
    private val properties: ConfluenceProperties,
    private val objectMapper: ObjectMapper
) {

    private val restClient: RestClient = builder
        .baseUrl(properties.restApiBaseUrl())
        .defaultHeaders { headers ->
            headers.setBearerAuth(properties.apiToken)
            headers.contentType = MediaType.APPLICATION_JSON
            headers.accept = listOf(MediaType.APPLICATION_JSON)
        }
        .build()

    fun createPage(spaceKey: String, title: String, contentHtml: String, parentPageId: String?): ConfluenceContent =
        execute("create page") {
            val payload = mutableMapOf<String, Any>(
                "type" to "page",
                "title" to title,
                "space" to mapOf("key" to spaceKey),
                "body" to mapOf("storage" to mapOf("value" to contentHtml, "representation" to "storage"))
            )
            if (!parentPageId.isNullOrBlank()) {
                payload["ancestors"] = listOf(mapOf("id" to parentPageId))
            }

            restClient.post()
                .uri("/content")
                .body(payload)
                .retrieve()
                .toEntity(String::class.java)
                .parseContent("create page")
        }

    fun updatePage(pageId: String, newTitle: String?, contentHtml: String): ConfluenceContent =
        execute("update page $pageId") {
            val existing = fetchContent(pageId)
            val nextVersion = nextVersion(existing)
            val title = newTitle?.takeIf { it.isNotBlank() } ?: existing.title

            val payload = mutableMapOf<String, Any>(
                "id" to pageId,
                "type" to "page",
                "title" to title.orEmpty(),
                "version" to mapOf("number" to nextVersion),
                "body" to mapOf("storage" to mapOf("value" to contentHtml, "representation" to "storage"))
            )

            restClient.put()
                .uri("/content/{id}", pageId)
                .body(payload)
                .retrieve()
                .toEntity(String::class.java)
                .parseContent("update page")
        }

    fun getPageContent(pageId: String): String? =
        execute("get page content $pageId") {
            val content = restClient.get()
                .uri { b ->
                    b.path("/content/{id}")
                        .queryParam("expand", "body.storage")
                        .build(pageId)
                }
                .retrieve()
                .toEntity(String::class.java)
                .parseContent("get page content")

            val value = content.body?.storage?.value
            value?.takeIf { StringUtils.hasText(it) }
        }

    fun searchPages(spaceKey: String, titleKeyword: String): List<ConfluenceContent> =
        execute("search pages in space $spaceKey with title containing '$titleKeyword'") {

            val safeKeyword = titleKeyword
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .trim()

            if (!StringUtils.hasText(safeKeyword)) return@execute emptyList()

            val cql = "space=\"$spaceKey\" AND type=\"page\" AND title~\"$safeKeyword\""

            val results = mutableListOf<ConfluenceContent>()
            var start = 0
            val limit = 25

            while (true) {
                val resp = restClient.get()
                    .uri { b ->
                        b.path("/content/search")
                            .queryParam("cql", cql)
                            .queryParam("start", start)
                            .queryParam("limit", limit)
                            .queryParam("expand", "body.storage")
                            .queryParam("expand", "space")
                            .build()
                    }
                    .retrieve()
                    .toEntity(String::class.java)

                if (!resp.statusCode.is2xxSuccessful) {
                    throw ConfluenceException(
                        "search pages failed. Status: ${resp.statusCode.value()}, body: ${resp.body.snippet()}"
                    )
                }

                val contentType = resp.headers.contentType
                if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
                    throw ConfluenceException(
                        "search pages failed. Expected JSON but got $contentType. Body: ${resp.body.snippet()}"
                    )
                }

                val json = resp.body ?: "{}"

                // вместо readValue(...) — readTree() + treeToValue() (обычно убирает ложный Path Traversal)
                val node = objectMapper.readTree(json)
                val sr = objectMapper.treeToValue(node, ConfluenceSearchResponse::class.java)

                val batch = sr.results.orEmpty()
                if (batch.isEmpty()) break

                results += batch

                start += limit
                val total = sr.total
                if (total != null && start >= total) break
            }

            results
        }

    fun deleteContent(contentId: String) {
        execute("delete content $contentId") {
            restClient.delete()
                .uri("/content/{id}", contentId)
                .retrieve()
                .toBodilessEntity()
        }
    }

    fun addComment(pageId: String, commentHtml: String): ConfluenceContent =
        execute("add comment to page $pageId") {
            val payload = mapOf(
                "type" to "comment",
                "container" to mapOf("id" to pageId, "type" to "page"),
                "body" to mapOf("storage" to mapOf("value" to commentHtml, "representation" to "storage"))
            )

            restClient.post()
                .uri("/content")
                .body(payload)
                .retrieve()
                .toEntity(String::class.java)
                .parseContent("add comment")
        }

    fun updateComment(commentId: String, commentHtml: String): ConfluenceContent =
        execute("update comment $commentId") {
            val existing = fetchContent(commentId)
            val nextVersion = nextVersion(existing)

            val payload = mutableMapOf<String, Any>(
                "id" to commentId,
                "type" to "comment",
                "version" to mapOf("number" to nextVersion),
                "body" to mapOf("storage" to mapOf("value" to commentHtml, "representation" to "storage"))
            )

            if (!existing.title.isNullOrBlank()) {
                payload["title"] = existing.title
            }

            restClient.put()
                .uri("/content/{id}", commentId)
                .body(payload)
                .retrieve()
                .toEntity(String::class.java)
                .parseContent("update comment")
        }

    fun deleteComment(commentId: String) {
        deleteContent(commentId)
    }

    fun fetchContent(contentId: String): ConfluenceContent =
        execute("fetch content $contentId") {
            restClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/content/{id}")
                        .queryParam("expand", "version,space,body.storage,container")
                        .build(contentId)
                }
                .retrieve()
                .toEntity(String::class.java)
                .parseContent("fetch content")
        }

    private fun nextVersion(content: ConfluenceContent): Int {
        val current = content.version?.number ?: 0
        return current + 1
    }

    private fun <T> execute(action: String, call: () -> T): T =
        try {
            val result = call()
            log.debug("Confluence action '{}' succeeded", action)
            result
        } catch (ex: RestClientResponseException) {
            throw ConfluenceException(
                "$action failed. Status: ${ex.statusCode}, response: ${ex.responseBodyAsString}",
                ex
            )
        } catch (ex: Exception) {
            throw ConfluenceException("$action failed: ${ex.message}", ex)
        }

    private fun ResponseEntity<String>.parseContent(action: String): ConfluenceContent {
        if (!statusCode.is2xxSuccessful) {
            throw ConfluenceException(
                "$action failed. Status: ${statusCode.value()}, body: ${body.snippet()}"
            )
        }
        val contentType = headers.contentType
        if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            throw ConfluenceException(
                "$action failed. Expected JSON but got $contentType. Body: ${body.snippet()}"
            )
        }
        return objectMapper.readValue(body ?: "{}", ConfluenceContent::class.java)
    }

    private fun String?.snippet(max: Int = 400): String =
        if (this == null) "null" else if (length <= max) this else take(max) + "...(truncated)"

    companion object {
        private val log = LoggerFactory.getLogger(ConfluenceClient::class.java)
    }
}
