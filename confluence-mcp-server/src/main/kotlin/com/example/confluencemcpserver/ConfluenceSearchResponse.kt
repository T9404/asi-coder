package com.example.confluencemcpserver

data class ConfluenceSearchResponse(
    val results: List<ConfluenceContent>? = null,
    val start: Int? = null,
    val limit: Int? = null,
    val size: Int? = null,
    val total: Int? = null
)