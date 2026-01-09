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
        description = "Create a Confluence page. Accepts storage-format HTML for the body."
    )
    fun createPage(spaceKey: String, title: String, contentHtml: String, parentPageId: String?): ConfluenceActionResult {
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

    @Tool(name = "confluence_delete_page", description = "Delete a Confluence page by id.")
    fun deletePage(pageId: String): String {
        client.deleteContent(pageId)
        return "Page $pageId deleted"
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

    @Tool(name = "confluence_delete_comment", description = "Delete a comment by id.")
    fun deleteComment(@ToolParam(required = true, description = "CommentId") commentId: String): String {
        client.deleteComment(commentId)
        return "Comment $commentId deleted"
    }
}
