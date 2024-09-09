package io.embrace.android.embracesdk.internal.worker

/**
 * The key used to reference a specific shared [BackgroundWorker] or the [ScheduledWorker] that uses it
 */
enum class WorkerName(internal val threadName: String) {

    /**
     * Used primarily to perform short-lived tasks that need to execute only once, or
     * recurring tasks that don't use I/O or block for long periods of time.
     */
    BACKGROUND_REGISTRATION("background-reg"),

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

    /**
     * Monitor thread that checks the main thread for ANRs.
     */
    ANR_MONITOR("anr-monitor"),

    /**
     * Initialize services asynchronously
     */
    SERVICE_INIT("service-init"),
}
