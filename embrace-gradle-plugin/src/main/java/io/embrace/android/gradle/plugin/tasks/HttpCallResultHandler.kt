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
        is Failure -> handleHttpFailure(endpoint, result)
        is Error -> "errored: ${endpoint.url}, stacktrace=${result.exception.stackTrace.joinToString("\n")}"
        is Success<*> -> null
    }
    if (msg != null && failBuildOnUploadErrors) {
        error("Embrace HTTP request $msg")
    }
}

private fun handleHttpFailure(
    endpoint: EmbraceEndpoint,
    result: Failure,
): String {
    val msg = "failed: ${endpoint.url}, status=${result.code}"

    if (result.code == 403) {
        return "$msg. Please check that your embrace-config.json contains an " +
            "app_id and api_token that match the settings page of your Embrace dashboard."
    }
    return msg
}
