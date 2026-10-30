package io.embrace.android.embracesdk.testframework.server

import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.RecordedRequest
import okio.ByteString
import org.junit.Assert.fail

/**
 * Opt-in second sender for [FakeApiServer]: re-POSTs each data upload (spans, logs; not config or attachments)
 * the SDK delivered to the mock server to a real ingest, so a payload built by the real SDK can be followed
 * through a DevEnv's pipeline. A test opts in by passing one to `SdkIntegrationTestRule.runTest`, usually from
 * [fromEnvironment], which reads the ingest's base URL (e.g. `http://data.<name>.<owner>.dev.emb-eng.com`) from
 * [ENV_VAR]. Tests that don't pass one never forward, whatever the environment holds.
 *
 * The body bytes and the headers the SDK sent (`Content-Encoding`, `X-EM-AID`, `X-EM-DID`,
 * `X-EM-PAYLOAD-TYPES`, ...) are sent unchanged, to the path the SDK would use against a real base URL
 * (`/v2/spans`, `/v2/logs`) rather than the mock server's `/api/v2/...`. The SDK itself always gets a 200
 * from the mock server, so a slow or failing ingest never makes it retry; the outcome is kept in
 * [getResults] and turned into a test failure by [assertAllSucceeded]. Redirects are not followed: OkHttp would
 * turn a redirected POST into a GET that can succeed without anything being ingested.
 */
internal class DataUploadForwarder(
    baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build(),
) {

    /**
     * The outcome of forwarding one upload. [status] is -1 when no response was received, in which case
     * [error] says why.
     */
    data class ForwardResult(
        val url: String,
        val status: Int,
        val appId: String?,
        val deviceId: String?,
        val userSessionId: String?,
        val sessionPartId: String?,
        val payloadTypes: String?,
        val error: String?,
    ) {
        val isSuccessful: Boolean
            get() = status in HTTP_OK_RANGE
    }

    // a malformed URL fails the test at setup rather than inside the mock server's dispatcher
    private val targetBaseUrl = baseUrl.trim().trimEnd('/').also { it.toHttpUrl() }
    private val results = CopyOnWriteArrayList<ForwardResult>()

    /**
     * Forwards [body], the bytes of [request] before the test framework consumed them, to the ingest.
     * [userSessionId] and [sessionPartId] are only for the log line and the result: they are null for
     * uploads that are not sessions.
     */
    fun forward(
        request: RecordedRequest,
        endpoint: Endpoint,
        body: ByteString,
        userSessionId: String?,
        sessionPartId: String?,
    ): ForwardResult {
        val url = "$targetBaseUrl/${endpoint.version}/${endpoint.path}"
        val outgoing = Request.Builder().url(url).apply {
            for ((name, value) in request.headers) {
                if (name.lowercase() !in NOT_FORWARDED_HEADERS) {
                    addHeader(name, value)
                }
            }
            post(body.toRequestBody(request.getHeader("Content-Type")?.toMediaTypeOrNull()))
        }.build()

        var status = -1
        var error: String? = null
        try {
            client.newCall(outgoing).execute().use { status = it.code }
        } catch (exc: Exception) {
            // anything thrown here would escape the mock server's dispatcher and make the SDK retry the upload
            error = exc.toString()
        }
        val result = ForwardResult(
            url = url,
            status = status,
            appId = request.getHeader("X-EM-AID"),
            deviceId = request.getHeader("X-EM-DID"),
            userSessionId = userSessionId,
            sessionPartId = sessionPartId,
            payloadTypes = request.getHeader("X-EM-PAYLOAD-TYPES"),
            error = error,
        )
        println(
            "EMBRACE_IT_FORWARD status=${result.status} url=$url app_id=${result.appId} device_id=${result.deviceId} " +
                "user_session_id=$userSessionId session_part_id=$sessionPartId payload_types=${result.payloadTypes}" +
                (error?.let { " error=$it" } ?: "")
        )
        results.add(result)
        return result
    }

    /**
     * The forwarded uploads, in the order the mock server received them.
     */
    fun getResults(): List<ForwardResult> = results.toList()

    /**
     * Fails the test if any upload got a non-2xx response or none at all.
     */
    fun assertAllSucceeded() {
        val failed = results.filterNot { it.isSuccessful }
        if (failed.isNotEmpty()) {
            fail(
                "Forwarding to $targetBaseUrl failed for ${failed.size} of ${results.size} uploads: " +
                    failed.joinToString { "${it.url} -> ${it.status}${it.error?.let { err -> " ($err)" } ?: ""}" }
            )
        }
    }

    companion object {
        /**
         * Environment variable holding the base URL of the ingest to forward to. Unset or blank disables forwarding.
         */
        const val ENV_VAR = "EMBRACE_IT_FORWARD_DATA_URL"

        // the whole call, well below the SDK's own 60s read timeout, so the SDK never retries an upload that is
        // still being forwarded, and below the DevEnv test's wait for the session
        private const val CALL_TIMEOUT_SECONDS = 30L

        private val HTTP_OK_RANGE = 200..299

        // set by OkHttp from the new body and URL
        private val NOT_FORWARDED_HEADERS = setOf(
            "host",
            "content-length",
            "content-type",
            "connection",
            "accept-encoding",
            "transfer-encoding",
        )

        fun fromEnvironment(): DataUploadForwarder? =
            System.getenv(ENV_VAR)?.trim()?.takeIf { it.isNotEmpty() }?.let { DataUploadForwarder(it) }
    }
}
