package com.example.confluencemcpserver

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty

data class ConfluenceContent(
    val id: String?,
    val type: String?,
    val title: String?,
    val version: Version?,
    val space: Space?,
    val container: Container?,
    val body: Body?,
    @JsonAlias("links")
    @JsonProperty("_links")
    val links: Map<String, Any?>?
) {
    data class Version(val number: Int = 0)
    data class Space(val key: String?)
    data class Container(val id: String?, val type: String?)
    data class Body(val storage: Storage?)
    data class Storage(val value: String?, val representation: String?)
}
