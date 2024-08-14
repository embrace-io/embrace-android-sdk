package io.embrace.android.embracesdk.internal.capture.startup

import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker

internal class StartupServiceImpl(
    private val spanService: SpanService,
    @Suppress("UnusedPrivateMember")
    private val backgroundWorker: BackgroundWorker
) : StartupService {

    @Volatile
    private var sdkInitStartMs: Long? = null

    @Volatile
    private var sdkInitEndMs: Long? = null

    @Volatile
    private var endedInForeground: Boolean? = null

    @Volatile
    private var threadName: String = "unknown"

    /**
     * SDK startup time. Only set for cold start sessions.
     */
    @Volatile
    private var sdkStartupDurationMs: Long? = null

    override fun setSdkStartupInfo(
        startTimeMs: Long,
        endTimeMs: Long,
        endedInForeground: Boolean,
        threadName: String
    ) {
        if (sdkStartupDurationMs == null) {
            spanService.recordCompletedSpan(
                name = "sdk-init",
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                private = true,
                attributes = mapOf(
                    "ended-in-foreground" to endedInForeground.toString(),
                    "thread-name" to threadName,
                ),
            )
        }
        sdkInitStartMs = startTimeMs
        sdkInitEndMs = endTimeMs
        this.endedInForeground = endedInForeground
        this.threadName = threadName
        sdkStartupDurationMs = endTimeMs - startTimeMs
    }

    override fun getSdkStartupDuration(coldStart: Boolean): Long? = when (coldStart) {
        true -> sdkStartupDurationMs
        false -> null
    }

    override fun getSdkInitStartMs(): Long? = sdkInitStartMs

    override fun getSdkInitEndMs(): Long? = sdkInitEndMs

    override fun endedInForeground(): Boolean? = endedInForeground

    override fun getInitThreadName(): String? = threadName
}
