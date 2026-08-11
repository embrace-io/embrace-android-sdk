package io.embrace.android.embracesdk.internal.instrumentation.startup

import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.semconv.EmbAppAttributes

internal class StartupServiceImpl(
    private val destination: TelemetryDestination,
    appVersionStartupCounterProvider: () -> Int?,
) : StartupService {

    private val startupCounter: Int? by lazy { appVersionStartupCounterProvider()?.takeIf { it > 0 } }

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
    private var sdkInitDurations: Map<String, Long> = emptyMap()

    override fun setSdkStartupInfo(
        startTimeMs: Long,
        endTimeMs: Long,
        endState: ProcessState,
        threadName: String,
        sdkInitDurations: Map<String, Long>,
    ) {
        val foregroundEnd = endState == ProcessState.FOREGROUND
        if (sdkStartupDurationMs == null) {
            val attributes = buildMap(sdkInitDurations.size + 3) {
                put("ended-in-foreground", foregroundEnd.toString())
                put("thread-name", threadName)
                startupCounter?.let { counter ->
                    put(EmbAppAttributes.EMB_APP_VERSION_STARTUP_COUNTER, counter.toString())
                }
                putSdkInitDurations(sdkInitDurations)
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
        sdkStartupDurationMs = endTimeMs - startTimeMs
        this.sdkInitDurations = sdkInitDurations
    }

    override fun getSdkStartupDuration(): Long? = sdkStartupDurationMs
    override fun getSdkInitStartMs(): Long? = sdkInitStartMs
    override fun getSdkInitEndMs(): Long? = sdkInitEndMs
    override fun getInitThreadName(): String = threadName
    override fun getAppVersionStartupCounter(): Int? = startupCounter
    override fun getSdkInitDurations(): Map<String, Long> = sdkInitDurations
}
