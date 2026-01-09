package com.example.youtrackmcp.mcp

import com.example.youtrackmcp.domain.IssueComment
import com.example.youtrackmcp.domain.IssueCommentsResponse
import com.example.youtrackmcp.domain.IssueCreated
import com.example.youtrackmcp.domain.IssueDetails
import com.example.youtrackmcp.domain.IssueFieldsSchema
import com.example.youtrackmcp.domain.IssueLinkResult
import com.example.youtrackmcp.domain.IssueSearchResponse
import com.example.youtrackmcp.domain.IssueUpdateResult
import com.example.youtrackmcp.domain.ManageTagsResult
import com.example.youtrackmcp.domain.ProjectDetails
import com.example.youtrackmcp.domain.ProjectRef
import com.example.youtrackmcp.domain.SavedSearchesResponse
import com.example.youtrackmcp.domain.TagAction
import com.example.youtrackmcp.domain.UserRef
import com.example.youtrackmcp.service.YouTrackService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam

class YouTrackTools(
    private val youTrackService: YouTrackService
) {

    @Tool(
        name = "search_issues",
        description = "Searches for issues using YouTrack query language. Returns id, summary, project, resolved, reporter, created, updated.",
    )
    fun searchIssues(
        @ToolParam(required = true, description = "YouTrack search query (e.g. `project:APP #Unresolved assignee:me`).") query: String,
        @ToolParam(required = false, description = "Offset for pagination (default 0).") offset: Int?,
        @ToolParam(required = false, description = "Page size/limit (default 20).") limit: Int?
    ): IssueSearchResponse = youTrackService.searchIssues(query, offset, limit)

    @Tool(
        name = "get_issue_fields_schema",
        description = "Returns JSON schema for custom fields in the specified project.",
    )
    fun getIssueFieldsSchema(
        @ToolParam(required = true, description = "Project ID or key.") projectIdOrKey: String
    ): IssueFieldsSchema = youTrackService.getIssueFieldsSchema(projectIdOrKey)

    @Tool(
        name = "create_issue",
        description = "Creates a new issue in the specified project. Use get_issue_fields_schema to discover customFields and their possible values for the target project. Important: some fields may be required for issue creation. Returns the ID of the created issue and URL that opens the issue in a web browser. Use get_issue to get the full issue details.",
    )
    fun createIssue(
        @ToolParam(required = true, description = "Project ID or key.") projectIdOrKey: String,
        @ToolParam(required = true, description = "Issue summary/title.") summary: String,
        @ToolParam(required = false, description = "Optional issue description (Markdown supported).") description: String?,
        @ToolParam(required = false, description = "Map of custom fields: {\"Priority\":\"High\",\"State\":\"Open\"}.") customFields: Map<String, Any?>?,
        @ToolParam(required = false, description = "List of tag names to apply.") tags: List<String>?,
        @ToolParam(required = false, description = "Assignee login (will set Assignee field).") assignee: String?
    ): IssueCreated = youTrackService.createIssue(projectIdOrKey, summary, description, customFields, tags, assignee)

    @Tool(
        name = "update_issue",
        description = "Updates an existing issue and its fields. Can also be used to star issues and add votes.",
    )
    fun updateIssue(
        @ToolParam(required = true, description = "Issue id or readable id (e.g. WEB-132).") issueId: String,
        @ToolParam(required = false, description = "New summary.") summary: String?,
        @ToolParam(required = false, description = "New description.") description: String?,
        @ToolParam(required = false, description = "Map of custom fields to update.") customFields: Map<String, Any?>?,
        @ToolParam(required = false, description = "Complete list of tags to set on the issue.") tags: List<String>?
    ): IssueUpdateResult = youTrackService.updateIssue(issueId, summary, description, customFields, tags)

    @Tool(
        name = "get_issue",
        description = "Returns detailed information for an issue or issue draft, including the summary, description, URL, project, reporter (username), tags, votes, and custom fields. The customFields output property provides more important issue details, including Type, State, Assignee, Priority, Subsystem, and so on. Use get_issue_fields_schema for the full list of custom fields and their possible values.",
    )
    fun getIssue(
        @ToolParam(required = true, description = "Issue id or readable id.") issueId: String
    ): IssueDetails = youTrackService.getIssue(issueId)

    @Tool(
        name = "change_issue_assignee",
        description = "Sets the value for the Assignee field.",
    )
    fun changeIssueAssignee(
        @ToolParam(required = true, description = "Issue id or readable id.") issueId: String,
        @ToolParam(required = true, description = "Target user login.") assignee: String
    ): IssueUpdateResult = youTrackService.changeAssignee(issueId, assignee)

    @Tool(
        name = "change_issue_status",
        description = "Changes the status of the specified issue to the target status.",
    )
    fun changeIssueStatus(
        @ToolParam(required = true, description = "Issue id or readable id.") issueId: String,
        @ToolParam(required = true, description = "Target status name (e.g. Develop, Review, Test, Done).") status: String
    ): IssueUpdateResult = youTrackService.changeIssueStatus(issueId, status)

    @Tool(
        name = "add_issue_comment",
        description = "Adds a new comment to the specified issue. Markdown supported.",
    )
    fun addIssueComment(
        @ToolParam(required = true, description = "Issue id or readable id.") issueId: String,
        @ToolParam(required = true, description = "Comment text (Markdown).") text: String
    ): IssueComment = youTrackService.addComment(issueId, text)

    @Tool(
        name = "get_issue_comments",
        description = "Returns a list of comments for an issue.",
    )
    fun getIssueComments(
        @ToolParam(required = true, description = "Issue id or readable id.") issueId: String,
        @ToolParam(required = false, description = "Offset for pagination (default 0).") offset: Int?,
        @ToolParam(required = false, description = "Limit for pagination (default 20).") limit: Int?
    ): IssueCommentsResponse = youTrackService.getComments(issueId, offset, limit)

    @Tool(
        name = "get_saved_issue_searches",
        description = "Returns saved searches marked as favorites by the current user.",
    )
    fun getSavedIssueSearches(
        @ToolParam(required = false, description = "Offset for pagination (default 0).") offset: Int?,
        @ToolParam(required = false, description = "Limit for pagination (default 20).") limit: Int?
    ): SavedSearchesResponse = youTrackService.getSavedIssueSearches(offset, limit)

    @Tool(
        name = "manage_issue_tags",
        description = "Adds or removes a tag from an issue.",
    )
    fun manageIssueTags(
        @ToolParam(required = true, description = "Issue id or readable id.") issueId: String,
        @ToolParam(required = true, description = "Tag name.") tag: String,
        @ToolParam(required = true, description = "Action: ADD or REMOVE.") action: TagAction
    ): ManageTagsResult = youTrackService.manageIssueTags(issueId, tag, action)

    @Tool(
        name = "link_issues",
        description = "Links two issues with the specified link type.",
    )
    fun linkIssues(
        @ToolParam(required = true, description = "Source issue id or readable id.") fromIssue: String,
        @ToolParam(required = true, description = "Target issue id or readable id.") toIssue: String,
        @ToolParam(required = true, description = "Link type name (e.g. relates to, duplicates).") linkType: String,
        @ToolParam(required = false, description = "Direction: OUTWARD or INWARD.") direction: String?
    ): IssueLinkResult = youTrackService.linkIssues(fromIssue, toIssue, linkType, direction ?: "OUTWARD")

    @Tool(
        name = "find_projects",
        description = "Finds projects whose names contain the specified substring (case-insensitive).",
    )
    fun findProjects(
        @ToolParam(required = true, description = "Substring to search in project names.") query: String,
        @ToolParam(required = false, description = "Offset for pagination (default 0).") offset: Int?,
        @ToolParam(required = false, description = "Limit for pagination (default 20).") limit: Int?
    ): List<ProjectRef> = youTrackService.findProjects(query, offset, limit)

    @Tool(
        name = "get_project",
        description = "Retrieves full details for a specific project.",
    )
    fun getProject(
        @ToolParam(required = true, description = "Project ID or key.") projectIdOrKey: String
    ): ProjectDetails = youTrackService.getProject(projectIdOrKey)

    @Tool(
        name = "find_user",
        description = "Finds users by username or email.",
    )
    fun findUser(
        @ToolParam(required = true, description = "Username or email query.") query: String,
        @ToolParam(required = false, description = "Offset for pagination (default 0).") offset: Int?,
        @ToolParam(required = false, description = "Limit for pagination (default 20).") limit: Int?
    ): List<UserRef> = youTrackService.findUsers(query, offset, limit)

    @Tool(
        name = "get_current_user",
        description = "Returns details about the currently authenticated user.",
    )
    fun getCurrentUser(): UserRef = youTrackService.getCurrentUser()
}
