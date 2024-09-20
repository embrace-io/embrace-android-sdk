package io.embrace.android.embracesdk.internal.worker

/**
 * The key used to reference a specific shared [BackgroundWorker] or [PrioritizedWorker] that uses it
 */
enum class Worker(internal val threadName: String) {

    /**
     * Used to perform miscellaneous tasks that don't involve I/O & don't require guarantees about
     * running on a specific thread or in a specific order.
     */
    NonIoRegWorker("non-io-reg"),

    /**
     * Used to perform miscellaneous tasks that _do_ involve I/O & don't require guarantees about
     * running on a specific thread or in a specific order.
     */
    IoRegWorker("non-io-reg"),

    /**
     * Saves/loads request information from files cached on disk.
     */
    FileCacheWorker("file-cache"),

    /**
     * All HTTP requests are performed on this executor.
     */
    NetworkRequestWorker("network-request"),

    /**
     * Used for periodic writing of session/background activity payloads to disk.
     */
    PeriodicCacheWorker("periodic-cache"),

    /**
     * Used to construct log messages. Log messages are sent to the server on a separate thread -
     * the intention behind this is to offload unnecessary CPU work from the main thread.
     */
    LogMessageWorker("log-message"),

    /**
     * Monitor thread that checks the main thread for ANRs.
     */
    AnrWatchdogWorker("anr-watchdog")
}
