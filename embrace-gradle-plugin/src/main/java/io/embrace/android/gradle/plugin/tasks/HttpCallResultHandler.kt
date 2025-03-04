package io.embrace.android.gradle.plugin.tasks

import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import io.embrace.android.gradle.plugin.network.HttpCallResult
import io.embrace.android.gradle.plugin.network.HttpCallResult.Error
import io.embrace.android.gradle.plugin.network.HttpCallResult.Failure
import io.embrace.android.gradle.plugin.network.HttpCallResult.Success
import io.embrace.android.gradle.plugin.tasks.common.RequestParams

/**
 * Handles a HTTP call result by throwing an error that stops the build (if configured) _or_ logging the error.
 */
fun handleHttpCallResult(
    result: HttpCallResult,
    params: RequestParams,
) = handleHttpCallResult(result, params.endpoint, params.failBuildOnUploadErrors)

fun handleHttpCallResult(
    result: HttpCallResult,
    endpoint: EmbraceEndpoint,
    failBuildOnUploadErrors: Boolean,
) {
    val msg = when (result) {
        is Failure -> "failed: ${endpoint.url}, status=${result.code}"
        is Error -> "errored: ${endpoint.url}, stacktrace=${result.exception.stackTrace.joinToString("\n")}"
        is Success<*> -> null
    }
    if (msg != null && failBuildOnUploadErrors) {
        error("Embrace HTTP request $msg")
    }
}
