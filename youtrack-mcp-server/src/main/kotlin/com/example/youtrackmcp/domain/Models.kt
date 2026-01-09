package com.example.youtrackmcp.domain

import java.time.Instant

data class ProjectRef(
    val id: String? = null,
    val name: String? = null,
    val shortName: String? = null
)

data class UserRef(
    val id: String? = null,
    val login: String? = null,
    val fullName: String? = null,
    val email: String? = null,
    val timeZone: String? = null
)

data class TagRef(
    val id: String? = null,
    val name: String? = null,
    val color: String? = null
)

data class IssueSummary(
    val id: String? = null,
    val idReadable: String? = null,
    val summary: String? = null,
    val project: ProjectRef? = null,
    val reporter: UserRef? = null,
    val resolved: Instant? = null,
    val created: Instant? = null,
    val updated: Instant? = null
)

data class IssueSearchResponse(
    val issues: List<IssueSummary>,
    val total: Int? = null,
    val nextOffset: Int? = null
)

data class IssueDetails(
    val id: String? = null,
    val idReadable: String? = null,
    val summary: String? = null,
    val description: String? = null,
    val project: ProjectRef? = null,
    val reporter: UserRef? = null,
    val tags: List<TagRef> = emptyList(),
    val votes: Int? = null,
    val created: Instant? = null,
    val updated: Instant? = null,
    val resolved: Instant? = null,
    val customFields: Map<String, Any?>? = null,
    val url: String? = null
)

data class IssueCreated(
    val id: String? = null,
    val idReadable: String? = null,
    val url: String? = null,
    val summary: String? = null
)

data class IssueUpdateResult(
    val id: String? = null,
    val idReadable: String? = null,
    val summary: String? = null,
    val fieldsUpdated: List<String> = emptyList(),
    val url: String? = null
)

data class IssueComment(
    val id: String? = null,
    val author: UserRef? = null,
    val text: String? = null,
    val created: Instant? = null,
    val updated: Instant? = null
)

data class IssueCommentsResponse(
    val issueId: String,
    val comments: List<IssueComment>,
    val nextOffset: Int? = null
)

data class SavedSearch(
    val id: String? = null,
    val name: String? = null,
    val query: String? = null,
    val owner: UserRef? = null
)

data class SavedSearchesResponse(
    val searches: List<SavedSearch>,
    val nextOffset: Int? = null
)

enum class TagAction { ADD, REMOVE }

data class ManageTagsResult(
    val issueId: String,
    val tags: List<TagRef>
)

data class IssueLinkResult(
    val fromIssue: String,
    val toIssue: String,
    val linkType: String,
    val direction: String,
    val linkCounts: Map<String, Int>? = null
)

data class ProjectDetails(
    val id: String? = null,
    val name: String? = null,
    val shortName: String? = null,
    val description: String? = null,
    val leader: UserRef? = null,
    val created: Instant? = null,
    val customFieldSchemas: List<CustomFieldSchema> = emptyList()
)

data class IssueFieldsSchema(
    val projectId: String,
    val fields: List<CustomFieldSchema>
)

data class CustomFieldSchema(
    val name: String? = null,
    val type: String? = null,
    val required: Boolean = false,
    val canBeEmpty: Boolean = true,
    val defaultValue: Any? = null,
    val possibleValues: List<CustomFieldOption> = emptyList()
)

data class CustomFieldOption(
    val id: String? = null,
    val name: String? = null,
    val color: String? = null
)
