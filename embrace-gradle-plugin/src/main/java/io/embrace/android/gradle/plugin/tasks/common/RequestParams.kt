package io.embrace.android.gradle.plugin.tasks.common

import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import java.io.Serializable

data class RequestParams(
    val appId: String,
    val apiToken: String,
    val endpoint: EmbraceEndpoint,
    val baseUrl: String,
    val fileName: String? = null,
    val buildId: String? = null,
) : Serializable {
    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
