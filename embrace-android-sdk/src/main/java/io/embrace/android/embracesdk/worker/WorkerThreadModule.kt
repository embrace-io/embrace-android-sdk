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

    /**
     * Used primarily to perform short-lived tasks that need to execute only once, or
     * recurring tasks that don't use I/O or block for long periods of time.
     */
    BACKGROUND_REGISTRATION("background-reg"),

    /**
     * Reads any sessions that are cached on disk & loads then sends them to the server.
     * Runnables are only added to this during SDK initialization.
     */
    CACHED_SESSIONS("cached-sessions"),

    /**
     * Loads background activities & moments from disk.
     */
    SEND_SESSIONS("send-sessions"),

    /**
     * Saves/loads request information from files cached on disk.
     */
    DELIVERY_CACHE("delivery-cache"),

    /**
     * All HTTP requests are performed on this executor.
     */
    NETWORK_REQUEST("network-request"),

    /**
     * Used for periodic writing of session/background activity payloads to disk.
     */
    PERIODIC_CACHE("periodic-cache"),

    /**
     * Used to construct log messages. Log messages are sent to the server on a separate thread -
     * the intention behind this is to offload unnecessary CPU work from the main thread.
     */
    REMOTE_LOGGING("remote-logging"),
}
