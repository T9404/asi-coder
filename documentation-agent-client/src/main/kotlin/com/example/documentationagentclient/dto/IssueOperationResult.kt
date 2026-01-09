package com.example.documentationagentclient.dto

data class IssueOperationResult(
    val issueId: String,
    val action: String,        // "show" | "status" | "comment" | "assign" | "transition"
    val success: Boolean,
    val message: String,
    val data: Any? = null
)