package io.embrace.android.embracesdk.worker

import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService

/**
 * A set of shared executors to be used throughout the SDK
 */
internal interface WorkerThreadModule : Closeable {

    /**
     * Return the [ExecutorService] given the [executorName]
     */
    fun backgroundExecutor(executorName: ExecutorName): ExecutorService

    /**
     * Return the [ScheduledExecutorService] given the [executorName]
     */
    fun scheduledExecutor(executorName: ExecutorName): ScheduledExecutorService

    /**
     * This should only be invoked when the SDK is shutting down. Closing all the worker threads in production means the
     * SDK will not be functional afterwards.
     */
    override fun close()
}

/**
 * The key used to reference a specific shared [ExecutorService] or the [ScheduledExecutorService] that uses it
 */
internal enum class ExecutorName(internal val threadName: String) {
    BACKGROUND_REGISTRATION("background-reg"),
    SCHEDULED_REGISTRATION("scheduled-reg"),
    CACHED_SESSIONS("cached-sessions"),
    SEND_SESSIONS("send-sessions"),
    DELIVERY_CACHE("delivery-cache"),
    API_RETRY("api-retry"),
    NATIVE_CRASH_CLEANER("native-crash-cleaner"),
    NATIVE_STARTUP("native-startup"),
    SESSION_CACHE_EXECUTOR("session-cache"),
    REMOTE_LOGGING("remote-logging"),
    SESSION_CLOSER("session-closer"),
    SESSION_CACHING("session-caching"),
}
