package io.embrace.android.embracesdk.benchmark.scenario

import io.embrace.android.embracesdk.internal.api.SdkApi
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod

/**
 * A named block of telemetry-producing work, written against the public Embrace API.
 */
class ScenarioSpec(
    val id: String,
    val description: String,
    val action: ScenarioScope.() -> Unit,
)

/**
 * What a scenario is given to work with: the SDK under test, which it calls directly, and control over the
 * passage of time and the app's foreground state, which each scope implements for where it runs. Its other
 * members simulate what an app does that takes more than a single call to the SDK.
 */
interface ScenarioScope {

    /**
     * The SDK under test.
     */
    val embrace: SdkApi

    /**
     * The current scenario time.
     */
    val nowMs: Long

    /**
     * Moves the scenario clock forward.
     */
    fun advanceTime(ms: Long)

    /**
     * Backgrounds the app for [ms], which ends the current session part, then returns to the
     * foreground and starts a new one.
     */
    fun backgroundAndReturn(ms: Long)

    /**
     * Records a network request that the app made without the SDK instrumenting it, as an app does through
     * the SDK's manual network API. The request takes 80 ms of scenario time.
     */
    fun recordRequest(
        url: String,
        method: HttpMethod = DEFAULT_METHOD,
        statusCode: Int = DEFAULT_STATUS_CODE,
        bytesReceived: Long = DEFAULT_BYTES_RECEIVED,
    ) {
        val startMs = nowMs
        advanceTime(REQUEST_DURATION_MS)
        recordRequest(url, startMs, nowMs, method, statusCode, bytesReceived)
    }

    /**
     * Records a network request that the app made from [startTimeMs] to [endTimeMs] without the SDK instrumenting
     * it, as an app does through the SDK's manual network API. The scenario clock does not move, so a scenario can
     * give each request a time of its own without waiting for it.
     */
    fun recordRequest(
        url: String,
        startTimeMs: Long,
        endTimeMs: Long,
        method: HttpMethod = DEFAULT_METHOD,
        statusCode: Int = DEFAULT_STATUS_CODE,
        bytesReceived: Long = DEFAULT_BYTES_RECEIVED,
    ) {
        require(endTimeMs >= startTimeMs) {
            "a request to $url cannot end at $endTimeMs, before it starts at $startTimeMs"
        }
        embrace.recordNetworkRequest(
            EmbraceNetworkRequest.fromCompletedRequest(
                url = url,
                httpMethod = method,
                startTime = startTimeMs,
                endTime = endTimeMs,
                bytesSent = REQUEST_BYTES_SENT,
                bytesReceived = bytesReceived,
                statusCode = statusCode,
            ),
        )
    }
}

private val DEFAULT_METHOD = HttpMethod.GET
private const val DEFAULT_STATUS_CODE = 200
private const val DEFAULT_BYTES_RECEIVED = 4_096L
private const val REQUEST_DURATION_MS = 80L
private const val REQUEST_BYTES_SENT = 512L
