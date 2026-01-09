package com.example.confluencemcpserver

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.util.Assert
import org.springframework.util.StringUtils

@ConfigurationProperties(prefix = "confluence")
data class ConfluenceProperties(
    var baseUrl: String = "",
    var username: String = "",
    var apiToken: String = ""
) {

    fun restApiBaseUrl(): String {
        Assert.hasText(baseUrl, "confluence.base-url must not be empty")
        val normalized = trimTrailingSlash(baseUrl)

        if (normalized.contains("/rest/api")) {
            return normalized
        }

        if (normalized.endsWith("/wiki")) {
            return "$normalized/rest/api"
        }

        return "$normalized/wiki/rest/api"
    }

    private fun trimTrailingSlash(value: String?): String {
        if (!StringUtils.hasText(value)) {
            return ""
        }
        val actual = value?.trim() ?: return ""
        return if (actual.endsWith("/")) actual.dropLast(1) else actual
    }
}
