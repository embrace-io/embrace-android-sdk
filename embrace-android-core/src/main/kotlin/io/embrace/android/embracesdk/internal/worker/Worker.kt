package io.embrace.android.embracesdk.internal.worker

/**
 * Key used to obtain a shared [BackgroundWorker] or [PriorityWorker] instance.
 */
sealed class Worker(internal val threadName: String) {

    /**
     * Workers that execute tasks by sorting them by priority.
     */
    sealed class Priority(threadName: String) : Worker(threadName) {

        /**
         * Saves/loads request information from files cached on disk.
         */
        object FileCacheWorker : Priority("file-cache")

        /**
         * All HTTP requests are performed on this executor.
         */
        object NetworkRequestWorker : Priority("network-request")
    }

    /**
     * Workers that execute tasks on a background thread using FIFO.
     */
    sealed class Background(threadName: String) : Worker(threadName) {

        /**
         * Used to perform miscellaneous tasks that don't involve I/O & don't require guarantees about
         * running on a specific thread or in a specific order.
         */
        object NonIoRegWorker : Background("non-io-reg")

        /**
         * Used to perform miscellaneous tasks that _do_ involve I/O & don't require guarantees about
         * running on a specific thread or in a specific order.
         */
        object IoRegWorker : Background("non-io-reg")

        /**
         * Used for periodic writing of session/background activity payloads to disk.
         */
        object PeriodicCacheWorker : Background("periodic-cache")

        /**
         * Used to construct log messages. Log messages are sent to the server on a separate thread -
         * the intention behind this is to offload unnecessary CPU work from the main thread.
         */
        object LogMessageWorker : Background("log-message")

        /**
         * Monitor thread that checks the main thread for ANRs.
         */
        object AnrWatchdogWorker : Background("anr-watchdog")

        /**
         * Delivery Worker
         */
        object DeliveryWorker : Background("delivery")
    }
}
