package com.example.documentationagentclient.core

import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.ObjectProvider
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class ConfluenceSaveService(
    private val chatClient: ObjectProvider<ChatClient>
) {

    @Async("confluenceExecutor")
    fun save(issueId: String, description: String, imageUrl: String) {
        val chat = chatClient.getIfAvailable() ?: return

        val title = "Generated Image for Issue $issueId"
        val spaceKey = "ASI"

        val safeUrl = escapeXmlAttr(imageUrl)
        val safeDesc = escapeXmlText(description)

        val contentHtml = """
          <h1>$title</h1>
          <p>$safeDesc</p>
          <p><img src="$safeUrl" alt="Generated image for $issueId" /></p>
        """.trimIndent()

        val response = chat.prompt()
            .system(SYSTEM_PROMPT)
            .user(
                """
        SPACE_KEY: $spaceKey
        TITLE: $title
        CONTENT_HTML:
        $contentHtml
        """.trimIndent()
            )
            .call()

        log.info("save-to-confluence final response: {}", response.content())
    }

    fun escapeXmlAttr(s: String): String =
        s.replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("'", "&apos;")

    private fun escapeXmlText(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(ConfluenceSaveService::class.java)
        private val SYSTEM_PROMPT = """
        You are a deterministic Confluence saver.

        Goal: ensure exactly one page exists with title exactly equal to TITLE in SPACE_KEY.

        Rules:
        1) First call confluence_search_pages with:
           - spaceKey = SPACE_KEY
           - titleKeyword = TITLE
        2) Inspect results. If there is a page whose title equals TITLE exactly:
           - call confluence_update_page with that pageId, title=TITLE, contentHtml=CONTENT_HTML
        3) Otherwise:
           - call confluence_create_page with spaceKey=SPACE_KEY, title=TITLE, contentHtml=CONTENT_HTML
        4) Never create pages with suffixes like "(2)" or modified titles.
        5) Call tools only. No normal text output.
        """.trimIndent()
    }

}
