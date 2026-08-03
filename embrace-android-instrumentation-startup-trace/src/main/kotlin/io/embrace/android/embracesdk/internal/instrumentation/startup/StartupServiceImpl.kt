package io.embrace.android.embracesdk.internal.instrumentation.startup

import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.semconv.EmbAppAttributes

internal class StartupServiceImpl(
    private val destination: TelemetryDestination,
) : StartupService {

    @Volatile
    private var sdkInitStartMs: Long? = null

    @Volatile
    private var sdkInitEndMs: Long? = null

    @Volatile
    private var threadName: String = "unknown"

    /**
     * SDK startup time. Only set for cold start sessions.
     */
    @Volatile
    private var sdkStartupDurationMs: Long? = null

    @Volatile
    private var appVersionStartupCounter: Int? = null

    override fun setSdkStartupInfo(
        startTimeMs: Long,
        endTimeMs: Long,
        endState: AppState,
        threadName: String,
        appVersionStartupCounter: Int?,
    ) {
        val foregroundEnd = endState == AppState.FOREGROUND
        val validCounter = appVersionStartupCounter?.takeIf { it > 0 }
        if (sdkStartupDurationMs == null) {
            val attributes = mutableMapOf(
                "ended-in-foreground" to foregroundEnd.toString(),
                "thread-name" to threadName,
            )
            validCounter?.let {
                attributes[EmbAppAttributes.EMB_APP_VERSION_STARTUP_COUNTER] = it.toString()
            }
            destination.recordCompletedSpan(
                name = "sdk-init",
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                private = true,
                attributes = attributes,
            )
        }
        sdkInitStartMs = startTimeMs
        sdkInitEndMs = endTimeMs
        this.threadName = threadName
        this.appVersionStartupCounter = validCounter
        sdkStartupDurationMs = endTimeMs - startTimeMs
    }

    override fun getSdkStartupDuration(): Long? = sdkStartupDurationMs
    override fun getSdkInitStartMs(): Long? = sdkInitStartMs
    override fun getSdkInitEndMs(): Long? = sdkInitEndMs
    override fun getInitThreadName(): String = threadName
    override fun getAppVersionStartupCounter(): Int? = appVersionStartupCounter
}
