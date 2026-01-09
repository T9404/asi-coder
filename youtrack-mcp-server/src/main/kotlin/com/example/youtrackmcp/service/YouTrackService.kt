package com.example.youtrackmcp.service

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
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

@Service
class YouTrackService(
    private val client: YouTrackClient
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun searchIssues(query: String, offset: Int?, limit: Int?): IssueSearchResponse {
        val safeOffset = (offset ?: 0).coerceAtLeast(0)
        val safeLimit = (limit ?: 20).coerceIn(1, 200)
        log.debug("Searching issues '{}' offset {} limit {}", query, safeOffset, safeLimit)
        return client.searchIssues(query, safeOffset, safeLimit)
    }

    fun getIssue(issueId: String): IssueDetails = client.getIssue(issueId)

    fun getIssueFieldsSchema(projectIdOrKey: String): IssueFieldsSchema =
        client.getIssueFieldsSchema(projectIdOrKey)

    fun createIssue(
        projectIdOrKey: String,
        summary: String,
        description: String?,
        customFields: Map<String, Any?>?,
        tags: List<String>?,
        assignee: String?
    ): IssueCreated = client.createIssue(projectIdOrKey, summary, description, customFields, tags, assignee, draft = false)

    fun updateIssue(
        issueId: String,
        summary: String?,
        description: String?,
        customFields: Map<String, Any?>?,
        tags: List<String>?
    ): IssueUpdateResult = client.updateIssue(issueId, summary, description, customFields, tags)

    fun changeAssignee(issueId: String, assignee: String): IssueUpdateResult = client.changeAssignee(issueId, assignee)

    fun changeIssueStatus(issueId: String, status: String): IssueUpdateResult = client.changeIssueStatus(issueId, status)

    fun addComment(issueId: String, text: String): IssueComment {
        log.info("Adding comment '{}'", issueId)
        return client.addComment(issueId, text)
    }

    fun getComments(issueId: String, offset: Int?, limit: Int?): IssueCommentsResponse {
        val safeOffset = (offset ?: 0).coerceAtLeast(0)
        val safeLimit = (limit ?: 20).coerceIn(1, 200)
        return client.getComments(issueId, safeOffset, safeLimit)
    }

    fun getSavedIssueSearches(offset: Int?, limit: Int?): SavedSearchesResponse {
        val safeOffset = (offset ?: 0).coerceAtLeast(0)
        val safeLimit = (limit ?: 20).coerceIn(1, 200)
        return client.getSavedIssueSearches(safeOffset, safeLimit)
    }

    fun manageIssueTags(issueId: String, tag: String, action: TagAction): ManageTagsResult =
        client.manageIssueTags(issueId, tag, action)

    fun linkIssues(fromIssue: String, toIssue: String, linkType: String, direction: String): IssueLinkResult =
        client.linkIssues(fromIssue, toIssue, linkType, direction)

    fun findProjects(query: String, offset: Int?, limit: Int?): List<ProjectRef> {
        val safeOffset = (offset ?: 0).coerceAtLeast(0)
        val safeLimit = (limit ?: 20).coerceIn(1, 200)
        return client.findProjects(query, safeOffset, safeLimit)
    }

    fun getProject(projectIdOrKey: String): ProjectDetails = client.getProject(projectIdOrKey)

    fun findUsers(query: String, offset: Int?, limit: Int?): List<UserRef> {
        val safeOffset = (offset ?: 0).coerceAtLeast(0)
        val safeLimit = (limit ?: 20).coerceIn(1, 200)
        return client.findUsers(query, safeOffset, safeLimit)
    }

    fun getCurrentUser(): UserRef = client.getCurrentUser()
}
