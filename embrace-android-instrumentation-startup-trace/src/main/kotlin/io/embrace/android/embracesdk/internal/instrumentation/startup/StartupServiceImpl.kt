package io.embrace.android.embracesdk.internal.instrumentation.startup

import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.semconv.EmbAppAttributes
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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
    private var endedInForeground: Boolean = false

    @Volatile
    private var attributesProvider: (() -> Map<String, String>)? = null

    /**
     * The built attributes, memoized on first use so the provider is only ever invoked once
     * and every consumer attaches an identical set.
     */
    private val sdkInitAttributes = AtomicReference<Map<String, String>?>(null)

    private val sdkInitSpanRecorded = AtomicBoolean(false)

    override fun setSdkStartupInfo(
        startTimeMs: Long,
        endTimeMs: Long,
        endState: ProcessState,
        threadName: String,
        attributesProvider: (() -> Map<String, String>)?,
    ) {
        sdkInitStartMs = startTimeMs
        sdkInitEndMs = endTimeMs
        this.threadName = threadName
        endedInForeground = endState == ProcessState.FOREGROUND
        sdkStartupDurationMs = endTimeMs - startTimeMs
        this.attributesProvider = attributesProvider
        sdkInitAttributes.set(null)
    }

    override fun recordSdkInitSpan() {
        val startTimeMs = sdkInitStartMs ?: return
        val endTimeMs = sdkInitEndMs ?: return
        if (sdkInitSpanRecorded.compareAndSet(false, true)) {
            val attributes = buildMap {
                put("ended-in-foreground", endedInForeground.toString())
                put("thread-name", threadName)
                startupCounter?.let { counter ->
                    put(EmbAppAttributes.EMB_APP_VERSION_STARTUP_COUNTER, counter.toString())
                }
                putAll(getSdkInitAttributes())
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
    override fun getSdkInitAttributes(): Map<String, String> {
        sdkInitAttributes.get()?.let { return it }
        val computed = attributesProvider?.invoke() ?: emptyMap()
        sdkInitAttributes.compareAndSet(null, computed)
        return sdkInitAttributes.get() ?: computed
    }
}
