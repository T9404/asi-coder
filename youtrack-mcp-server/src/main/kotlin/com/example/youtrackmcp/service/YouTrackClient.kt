package com.example.youtrackmcp.service

import com.example.youtrackmcp.config.YouTrackProperties
import com.example.youtrackmcp.domain.CustomFieldOption
import com.example.youtrackmcp.domain.CustomFieldSchema
import com.example.youtrackmcp.domain.IssueComment
import com.example.youtrackmcp.domain.IssueCommentsResponse
import com.example.youtrackmcp.domain.IssueCreated
import com.example.youtrackmcp.domain.IssueDetails
import com.example.youtrackmcp.domain.IssueFieldsSchema
import com.example.youtrackmcp.domain.IssueLinkResult
import com.example.youtrackmcp.domain.IssueSearchResponse
import com.example.youtrackmcp.domain.IssueSummary
import com.example.youtrackmcp.domain.IssueUpdateResult
import com.example.youtrackmcp.domain.ManageTagsResult
import com.example.youtrackmcp.domain.ProjectDetails
import com.example.youtrackmcp.domain.ProjectRef
import com.example.youtrackmcp.domain.SavedSearch
import com.example.youtrackmcp.domain.SavedSearchesResponse
import com.example.youtrackmcp.domain.TagAction
import com.example.youtrackmcp.domain.TagRef
import com.example.youtrackmcp.domain.UserRef
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.RequestEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.exchange
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.time.Instant

@Component
class YouTrackClient(
    private val restTemplate: RestTemplate,
    private val props: YouTrackProperties
    ) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun searchIssues(query: String, offset: Int, limit: Int): IssueSearchResponse {
        val uri = uri("/api/issues") {
            queryParam("query", query)
            queryParam("offset", offset)
            queryParam("limit", limit)
            queryParam("fields", SEARCH_FIELDS)
        }
        val response = restTemplate.exchange(
            RequestEntity.get(uri).headers(authHeaders()).build(),
            object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
        )
        val issues = response.body.orEmpty().map { toIssueSummary(it) }
        val total = response.headers["X-Total-Count"]?.firstOrNull()?.toIntOrNull()
        val nextOffset = if (issues.size == limit) offset + limit else null
        return IssueSearchResponse(issues = issues, total = total, nextOffset = nextOffset)
    }

    fun getIssue(issueId: String): IssueDetails {
        val uri = uri("/api/issues/$issueId") {
            queryParam(
                "fields",
                ISSUE_FIELDS
            )
        }
        val response = restTemplate.exchange(
            RequestEntity.get(uri).headers(authHeaders()).build(),
            object : ParameterizedTypeReference<Map<String, Any?>>() {}
        )
        return toIssueDetails(response.body.orEmpty())
    }

    fun getIssueFieldsSchema(projectIdOrKey: String): IssueFieldsSchema {
        val uri = UriComponentsBuilder.fromHttpUrl(base())
            .path("/api/admin/projects/")
            .pathSegment(projectIdOrKey)
            .path("/customFields")
            .queryParam(
                "fields",
                "projectCustomField(field(name,localizedName,fieldType(id)),canBeEmpty,emptyFieldText,isRequired,defaultValue," +
                    "bundle(values(id,name,color(background))))"
            )
            .build(true)
            .toUri()

        val response = restTemplate.exchange(
            RequestEntity.get(uri).headers(authHeaders()).build(),
            object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
        )
        val fields = response.body.orEmpty().mapNotNull { toCustomFieldSchema(it) }
        return IssueFieldsSchema(projectId = projectIdOrKey, fields = fields)
    }

    fun createIssue(
        projectIdOrKey: String,
        summary: String,
        description: String?,
        customFields: Map<String, Any?>?,
        tags: List<String>?,
        assignee: String?,
        draft: Boolean = false
    ): IssueCreated {
        val body = LinkedHashMap<String, Any>()
        body["project"] = buildProjectRef(projectIdOrKey)
        body["summary"] = summary
        description?.let { body["description"] = it }

        val customFieldPayload = mutableListOf<Map<String, Any?>>()
        customFields?.let { customFieldPayload += buildCustomFields(it) }
        assignee?.let { customFieldPayload += mapOf("name" to "Assignee", "value" to mapOf("login" to assignee)) }
        if (customFieldPayload.isNotEmpty()) {
            body["customFields"] = customFieldPayload
        }
        tags?.takeIf { it.isNotEmpty() }?.let { names ->
            body["tags"] = names.map { mapOf("name" to it) }
        }

        val uri = uri("/api/issues") {
            if (draft) queryParam("draft", "true")
            queryParam("fields", "id,idReadable,summary,project(id,name,shortName),url")
        }

        val response = restTemplate.exchange(
            RequestEntity.post(uri).headers(authHeaders()).body(body),
            object : ParameterizedTypeReference<Map<String, Any?>>() {}
        )
        val payload = response.body ?: emptyMap()
        return IssueCreated(
            id = payload["id"] as? String,
            idReadable = payload["idReadable"] as? String,
            url = payload["url"] as? String,
            summary = payload["summary"] as? String
        )
    }

    fun updateIssue(
        issueId: String,
        summary: String?,
        description: String?,
        customFields: Map<String, Any?>?,
        tags: List<String>?
    ): IssueUpdateResult {
        val body = LinkedHashMap<String, Any>()
        summary?.let { body["summary"] = it }
        description?.let { body["description"] = it }
        customFields?.takeIf { it.isNotEmpty() }?.let { body["customFields"] = buildCustomFields(it) }
        tags?.takeIf { it.isNotEmpty() }?.let { body["tags"] = it.map { name -> mapOf("name" to name) } }

        val uri = uri("/api/issues/$issueId") {
            queryParam("fields", "id,idReadable,summary,updated,url")
        }

        val response = restTemplate.exchange(
            RequestEntity.post(uri).headers(authHeaders()).body(body),
            object : ParameterizedTypeReference<Map<String, Any?>>() {}
        )
        val payload = response.body ?: emptyMap()
        val fieldsUpdated = body.keys.toList()
        return IssueUpdateResult(
            id = payload["id"] as? String ?: issueId,
            idReadable = payload["idReadable"] as? String,
            summary = payload["summary"] as? String,
            fieldsUpdated = fieldsUpdated,
            url = payload["url"] as? String
        )
    }

    fun changeAssignee(issueId: String, assignee: String): IssueUpdateResult =
        updateIssue(
            issueId = issueId,
            summary = null,
            description = null,
            customFields = mapOf("Assignee" to mapOf("login" to assignee)),
            tags = null
        )

    fun changeIssueStatus(issueId: String, status: String): IssueUpdateResult {
        val body = linkedMapOf<String, Any>(
            "customFields" to listOf(
                linkedMapOf(
                    "name" to "Stage",
                    "\$type" to "StateIssueCustomField",
                    "value" to mapOf("name" to status)
                )
            )
        )

        val uri = uri("/api/issues/$issueId") {
            queryParam("fields", "id,idReadable,summary,updated,url")
        }

        val response = restTemplate.exchange(
            RequestEntity.post(uri).headers(authHeaders()).body(body),
            object : ParameterizedTypeReference<Map<String, Any?>>() {}
        )

        val payload = response.body ?: emptyMap()

        return IssueUpdateResult(
            id = payload["id"] as? String ?: issueId,
            idReadable = payload["idReadable"] as? String,
            summary = payload["summary"] as? String,
            fieldsUpdated = listOf("customFields"),
            url = payload["url"] as? String
        )
    }

    fun addComment(issueId: String, text: String): IssueComment {
        val uri = uri("/api/issues/$issueId/comments") {
            queryParam("fields", "id,text,author(login,fullName,email),created,updated")
        }
        val response = restTemplate.exchange(
            RequestEntity.post(uri).headers(authHeaders()).body(mapOf("text" to text)),
            object : ParameterizedTypeReference<Map<String, Any?>>() {}
        )
        return toIssueComment(response.body.orEmpty())
    }

    fun getComments(issueId: String, offset: Int, limit: Int): IssueCommentsResponse {
        val uri = uri("/api/issues/$issueId/comments") {
            queryParam("offset", offset)
            queryParam("limit", limit)
            queryParam("fields", "id,text,author(login,fullName,email),created,updated")
        }
        val response = restTemplate.exchange(
            RequestEntity.get(uri).headers(authHeaders()).build(),
            object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
        )
        val comments = response.body.orEmpty().map { toIssueComment(it) }
        val nextOffset = if (comments.size == limit) offset + limit else null
        return IssueCommentsResponse(issueId = issueId, comments = comments, nextOffset = nextOffset)
    }

    fun getSavedIssueSearches(offset: Int, limit: Int): SavedSearchesResponse {
        val uri = uri("/api/user/issueSearches") {
            queryParam("offset", offset)
            queryParam("limit", limit)
            queryParam("fields", "id,name,query,owner(login,fullName,email)")
        }
        val response = restTemplate.exchange(
            RequestEntity.get(uri).headers(authHeaders()).build(),
            object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
        )
        val searches = response.body.orEmpty().map { toSavedSearch(it) }
        val nextOffset = if (searches.size == limit) offset + limit else null
        return SavedSearchesResponse(searches = searches, nextOffset = nextOffset)
    }

    fun manageIssueTags(issueId: String, tag: String, action: TagAction): ManageTagsResult {
        when (action) {
            TagAction.ADD -> addTag(issueId, tag)
            TagAction.REMOVE -> removeTag(issueId, tag)
        }
        val tags = getIssueTags(issueId)
        return ManageTagsResult(issueId = issueId, tags = tags)
    }

    fun linkIssues(fromIssue: String, toIssue: String, linkType: String, direction: String): IssueLinkResult {
        val uri = uri("/api/issues/$fromIssue/links") {
            queryParam("fields", "linkType(name),direction,issues(idReadable),linkTypeAggregated(name,issues(size))")
        }
        val body = mapOf(
            "linkType" to mapOf("name" to linkType),
            "issues" to listOf(mapOf("idReadable" to toIssue)),
            "direction" to direction
        )
        val response = restTemplate.exchange(
            RequestEntity.post(uri).headers(authHeaders()).body(body),
            object : ParameterizedTypeReference<Map<String, Any?>>() {}
        )
        val linkCounts = parseLinkCounts(response.body)
        return IssueLinkResult(
            fromIssue = fromIssue,
            toIssue = toIssue,
            linkType = linkType,
            direction = direction,
            linkCounts = linkCounts
        )
    }

    fun findProjects(query: String, offset: Int, limit: Int): List<ProjectRef> {
        val uri = uri("/api/admin/projects") {
            queryParam("query", query)
            queryParam("offset", offset)
            queryParam("limit", limit)
            queryParam("fields", "id,name,shortName")
        }
        val response = restTemplate.exchange(
            RequestEntity.get(uri).headers(authHeaders()).build(),
            object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
        )
        return response.body.orEmpty().mapNotNull { toProjectRef(it) }
    }

    fun getProject(projectIdOrKey: String): ProjectDetails {
        val uri = UriComponentsBuilder.fromHttpUrl(base())
            .path("/api/admin/projects/")
            .pathSegment(projectIdOrKey)
            .queryParam(
                "fields",
                "id,name,shortName,description,leader(login,fullName,email),created," +
                    "customFields(projectCustomField(field(name,fieldType(id)),isRequired,canBeEmpty,emptyFieldText,defaultValue,bundle(values(id,name,color(background)))))"
            )
            .build(true)
            .toUri()
        val response = restTemplate.exchange(
            RequestEntity.get(uri).headers(authHeaders()).build(),
            object : ParameterizedTypeReference<Map<String, Any?>>() {}
        )
        val payload = response.body ?: emptyMap()
        val fieldSchemas = (payload["customFields"] as? List<*>)?.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            toCustomFieldSchema(map)
        } ?: emptyList()
        return ProjectDetails(
            id = payload["id"] as? String,
            name = payload["name"] as? String,
            shortName = payload["shortName"] as? String,
            description = payload["description"] as? String,
            leader = toUserRef(payload["leader"] as? Map<*, *>),
            created = toInstant(payload["created"]),
            customFieldSchemas = fieldSchemas
        )
    }

    fun findUsers(query: String, offset: Int, limit: Int): List<UserRef> {
        val uri = uri("/api/users") {
            queryParam("query", query)
            queryParam("offset", offset)
            queryParam("limit", limit)
            queryParam("fields", "id,login,fullName,email,timezone")
        }
        val response = restTemplate.exchange(
            RequestEntity.get(uri).headers(authHeaders()).build(),
            object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
        )
        return response.body.orEmpty().mapNotNull { toUserRef(it) }
    }

    fun getCurrentUser(): UserRef {
        val uri = uri("/api/users/me") {
            queryParam("fields", "id,login,fullName,email,timezone")
        }
        val response = restTemplate.exchange(
            RequestEntity.get(uri).headers(authHeaders()).build(),
            object : ParameterizedTypeReference<Map<String, Any?>>() {}
        )
        return toUserRef(response.body) ?: UserRef()
    }

    private fun addTag(issueId: String, tag: String) {
        val uri = uri("/api/issues/$issueId/tags") {
            queryParam("fields", "id,name,color(background)")
        }
        restTemplate.exchange<Void>(
            RequestEntity.post(uri).headers(authHeaders()).body(mapOf("name" to tag))
        )
    }

    private fun removeTag(issueId: String, tag: String) {
        val existing = getIssueTags(issueId).firstOrNull { it.name.equals(tag, ignoreCase = true) }
        val tagId = existing?.id ?: throw IllegalArgumentException("Tag '$tag' not found on $issueId")
        val uri = uri("/api/issues/$issueId/tags/$tagId")
        restTemplate.exchange<Void>(
            RequestEntity.delete(uri).headers(authHeaders()).build()
        )
    }

    private fun getIssueTags(issueId: String): List<TagRef> {
        val uri = uri("/api/issues/$issueId/tags") {
            queryParam("fields", "id,name,color(background)")
        }
        val response = restTemplate.exchange(
            RequestEntity.get(uri).headers(authHeaders()).build(),
            object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
        )
        return response.body.orEmpty().mapNotNull { toTagRef(it) }
    }

    private fun parseLinkCounts(body: Map<String, Any?>?): Map<String, Int>? {
        val aggregated = body?.get("linkTypeAggregated") as? List<*> ?: return null
        val result = linkedMapOf<String, Int>()
        aggregated.forEach { entry ->
            val map = entry as? Map<*, *> ?: return@forEach
            val name = map["name"] as? String ?: return@forEach
            val issues = (map["issues"] as? Map<*, *>)?.get("size") as? Number
            issues?.let { result[name] = it.toInt() }
        }
        return if (result.isEmpty()) null else result
    }

    private fun buildCustomFields(fields: Map<String, Any?>): List<Map<String, Any?>> =
        fields.map { (name, rawValue) ->
            val normalized = normalizeCustomFieldValue(rawValue)
            buildMap<String, Any?> {
                put("name", name)
                inferCustomFieldType(rawValue, normalized)?.let { put("\$type", it) }
                put("value", normalized)
            }
        }

    private fun normalizeCustomFieldValue(raw: Any?): Any? = when (raw) {
        null -> null
        is String -> mapOf("name" to raw)
        is Map<*, *> -> raw
        is List<*> -> raw
        else -> raw
    }

    private fun inferCustomFieldType(raw: Any?, normalized: Any?): String? = when (raw) {
        is List<*> -> "MultiEnumIssueCustomField"
        is Map<*, *> -> when {
            raw.containsKey("login") -> "SingleUserIssueCustomField"
            raw.containsKey("name") || raw.containsKey("id") -> "SingleEnumIssueCustomField"
            else -> null
        }
        is String -> "SingleEnumIssueCustomField"
        else -> when (normalized) {
            is Map<*, *> -> if (normalized.containsKey("login")) "SingleUserIssueCustomField" else "SingleEnumIssueCustomField"
            else -> null
        }
    }

    private fun toIssueSummary(data: Map<String, Any?>): IssueSummary = IssueSummary(
        id = data["id"] as? String,
        idReadable = data["idReadable"] as? String,
        summary = data["summary"] as? String,
        project = toProjectRef(data["project"]),
        reporter = toUserRef(data["reporter"] as? Map<*, *>),
        resolved = toInstant(data["resolved"]),
        created = toInstant(data["created"]),
        updated = toInstant(data["updated"])
    )

    private fun toIssueDetails(data: Map<String, Any?>): IssueDetails = IssueDetails(
        id = data["id"] as? String,
        idReadable = data["idReadable"] as? String,
        summary = data["summary"] as? String,
        description = data["description"] as? String,
        project = toProjectRef(data["project"]),
        reporter = toUserRef(data["reporter"] as? Map<*, *>),
        tags = (data["tags"] as? List<*>)?.mapNotNull { toTagRef(it as? Map<*, *>) } ?: emptyList(),
        votes = (data["votes"] as? Number)?.toInt(),
        created = toInstant(data["created"]),
        updated = toInstant(data["updated"]),
        resolved = toInstant(data["resolved"]),
        customFields = toCustomFields(data["customFields"]),
        url = data["url"] as? String
    )

    private fun toIssueComment(data: Map<String, Any?>): IssueComment = IssueComment(
        id = data["id"] as? String,
        author = toUserRef(data["author"] as? Map<*, *>),
        text = data["text"] as? String,
        created = toInstant(data["created"]),
        updated = toInstant(data["updated"])
    )

    private fun toSavedSearch(data: Map<String, Any?>): SavedSearch = SavedSearch(
        id = data["id"] as? String,
        name = data["name"] as? String,
        query = data["query"] as? String,
        owner = toUserRef(data["owner"] as? Map<*, *>)
    )

    private fun toProjectRef(raw: Any?): ProjectRef? {
        val data = raw as? Map<*, *> ?: return null
        return ProjectRef(
            id = data["id"] as? String,
            name = data["name"] as? String,
            shortName = data["shortName"] as? String
        )
    }

    private fun toUserRef(raw: Map<*, *>?): UserRef? {
        val data = raw ?: return null
        return UserRef(
            id = data["id"] as? String,
            login = data["login"] as? String,
            fullName = data["fullName"] as? String,
            email = data["email"] as? String,
            timeZone = data["timezone"] as? String ?: data["timeZone"] as? String
        )
    }

    private fun toTagRef(raw: Map<*, *>?): TagRef? {
        val data = raw ?: return null
        val color = (data["color"] as? Map<*, *>)?.get("background") as? String
        return TagRef(
            id = data["id"] as? String,
            name = data["name"] as? String,
            color = color
        )
    }

    private fun toCustomFields(raw: Any?): Map<String, Any?>? {
        val list = raw as? List<*> ?: return null
        val result = linkedMapOf<String, Any?>()
        list.forEach { entry ->
            val data = entry as? Map<*, *> ?: return@forEach
            val name = data["name"] as? String ?: return@forEach
            val value = data["value"]
            result[name] = when (value) {
                is Map<*, *> -> value["name"] ?: value["login"] ?: value["fullName"] ?: value["idReadable"] ?: value
                is List<*> -> value.map { (it as? Map<*, *>)?.get("name") ?: it }
                else -> value
            }
        }
        return result
    }

    private fun toCustomFieldSchema(data: Map<*, *>): CustomFieldSchema? {
        val fieldBlock = data["projectCustomField"] as? Map<*, *> ?: data
        val field = fieldBlock["field"] as? Map<*, *>
        val bundle = fieldBlock["bundle"] as? Map<*, *>
        val values = (bundle?.get("values") as? List<*>)?.mapNotNull { value ->
            val map = value as? Map<*, *> ?: return@mapNotNull null
            CustomFieldOption(
                id = map["id"] as? String,
                name = map["name"] as? String,
                color = (map["color"] as? Map<*, *>)?.get("background") as? String
            )
        } ?: emptyList()

        val required = when {
            (fieldBlock["isRequired"] as? Boolean) != null -> fieldBlock["isRequired"] as Boolean
            (fieldBlock["canBeEmpty"] as? Boolean) != null -> !(fieldBlock["canBeEmpty"] as Boolean)
            else -> false
        }

        return CustomFieldSchema(
            name = field?.get("name") as? String ?: fieldBlock["name"] as? String,
            type = (field?.get("fieldType") as? Map<*, *>)?.get("id") as? String
                ?: (fieldBlock["fieldType"] as? Map<*, *>)?.get("id") as? String,
            required = required,
            canBeEmpty = (fieldBlock["canBeEmpty"] as? Boolean) ?: !required,
            defaultValue = fieldBlock["defaultValue"],
            possibleValues = values
        )
    }

    private fun toInstant(value: Any?): Instant? = when (value) {
        null -> null
        is Number -> Instant.ofEpochMilli(value.toLong())
        is String -> value.toLongOrNull()?.let { Instant.ofEpochMilli(it) } ?: runCatching { Instant.parse(value) }.getOrNull()
        else -> null
    }

    private fun authHeaders(): HttpHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        accept = listOf(MediaType.APPLICATION_JSON)
        if (props.token.isNotBlank()) {
            setBearerAuth(props.token)
        }
    }

    private fun base(): String = props.baseUrl.trimEnd('/')

    private fun uri(path: String, block: UriComponentsBuilder.() -> Unit = {}): URI =
        UriComponentsBuilder.fromHttpUrl(base())
            .path(path)
            .apply(block)
            .build(true)
            .toUri()

    private fun buildProjectRef(projectIdOrKey: String): Map<String, String> {
        val looksLikeEntityId = projectIdOrKey.matches(Regex("\\d+-\\d+"))
        val resolved = if (looksLikeEntityId) null else runCatching { getProject(projectIdOrKey) }.getOrNull()
        val id = resolved?.id ?: projectIdOrKey.takeIf { looksLikeEntityId }
        val shortName = resolved?.shortName ?: projectIdOrKey.takeUnless { looksLikeEntityId }
        val name = resolved?.name

        return buildMap {
            id?.let { put("id", it) }
            shortName?.let { put("shortName", it) }
            name?.let { put("name", it) }
        }
    }

    companion object {
        private const val SEARCH_FIELDS = "id,idReadable,summary,project(id,name,shortName),resolved,reporter(login,fullName,email),created,updated"
        private const val ISSUE_FIELDS =
            "id,idReadable,summary,description,project(id,name,shortName),reporter(login,fullName,email)," +
                "tags(id,name,color(background)),votes,created,updated,resolved,url,customFields(name,value(name,login,fullName,idReadable,text,presentableName))"
    }
}
