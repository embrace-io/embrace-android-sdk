package io.embrace.android.embracesdk.internal.instrumentation.startup

import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.semconv.EmbAppAttributes
import java.util.concurrent.atomic.AtomicBoolean

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

    @Volatile
    private var endedInForeground: Boolean = false

    private val sdkInitSpanRecorded = AtomicBoolean(false)

    override fun setSdkStartupInfo(
        startTimeMs: Long,
        endTimeMs: Long,
        endState: ProcessState,
        threadName: String,
        sdkInitDurations: Map<String, Long>,
    ) {
        sdkInitStartMs = startTimeMs
        sdkInitEndMs = endTimeMs
        this.threadName = threadName
        endedInForeground = endState == ProcessState.FOREGROUND
        sdkStartupDurationMs = endTimeMs - startTimeMs
        this.sdkInitDurations = sdkInitDurations
    }

    override fun recordSdkInitSpan() {
        val startTimeMs = sdkInitStartMs ?: return
        val endTimeMs = sdkInitEndMs ?: return
        if (sdkInitSpanRecorded.compareAndSet(false, true)) {
            val initDurations = sdkInitDurations
            val attributes = buildMap(initDurations.size + 3) {
                put("ended-in-foreground", endedInForeground.toString())
                put("thread-name", threadName)
                startupCounter?.let { counter ->
                    put(EmbAppAttributes.EMB_APP_VERSION_STARTUP_COUNTER, counter.toString())
                }
                putSdkInitDurations(initDurations)
            }
            destination.recordCompletedSpan(
                name = "sdk-init",
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                private = true,
                attributes = attributes,
            )
        }
    }

    override fun getSdkStartupDuration(): Long? = sdkStartupDurationMs
    override fun getSdkInitStartMs(): Long? = sdkInitStartMs
    override fun getSdkInitEndMs(): Long? = sdkInitEndMs
    override fun getInitThreadName(): String = threadName
    override fun getAppVersionStartupCounter(): Int? = startupCounter
    override fun getSdkInitDurations(): Map<String, Long> = sdkInitDurations
}
