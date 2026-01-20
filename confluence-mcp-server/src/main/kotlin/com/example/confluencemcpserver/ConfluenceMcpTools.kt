package com.example.confluencemcpserver

import org.springaicommunity.mcp.annotation.McpTool
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

class ConfluenceMcpTools(
    private val client: ConfluenceClient,
    private val properties: ConfluenceProperties
) {

    @Tool(
        name = "confluence_create_page",
        description = "Create a new Confluence page in the specified space with the given title and content (storage format HTML). Optionally specify a parent page ID to create a child page."
    )
    fun createPage(
        @ToolParam(required = true) spaceKey: String,
        @ToolParam(required = true) title: String,
        @ToolParam(required = true) contentHtml: String,
        @ToolParam(required = false) parentPageId: String?
    ): ConfluenceActionResult {
        val created = client.createPage(spaceKey, title, contentHtml, parentPageId)
        return ConfluenceActionResult.fromContent(created, properties)
    }

    @Tool(
        name = "confluence_update_page",
        description = "Update a Confluence page body (storage format) and optionally title. Automatically bumps the version."
    )
    fun updatePage(pageId: String, title: String?, contentHtml: String): ConfluenceActionResult {
        val updated = client.updatePage(pageId, title, contentHtml)
        return ConfluenceActionResult.fromContent(updated, properties)
    }

    @Tool(
        name = "confluence_get_page_content",
        description = "Get the content of a Confluence page in storage format (HTML)."
    )
    fun getPageContent(pageId: String): String? {
        return client.getPageContent(pageId)
    }

    @Tool(
        name = "confluence_search_pages",
        description = "Search for Confluence pages by title keyword in a given space. Returns a list of page IDs and titles."
    )
    fun searchPage(spaceKey: String, titleKeyword: String): List<ConfluenceContent> {
        return client.searchPages(spaceKey, titleKeyword)
    }

    @Tool(name = "confluence_add_comment", description = "Add a comment to a page. Body must be storage-format HTML.")
    fun addComment(pageId: String, commentHtml: String): ConfluenceActionResult {
        val created = client.addComment(pageId, commentHtml)
        return ConfluenceActionResult.fromContent(created, properties)
    }

    @Tool(name = "confluence_update_comment", description = "Update an existing comment. Automatically bumps the version.")
    fun updateComment(commentId: String, commentHtml: String): ConfluenceActionResult {
        val updated = client.updateComment(commentId, commentHtml)
        return ConfluenceActionResult.fromContent(updated, properties)
    }

}
