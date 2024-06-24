package io.embrace.android.embracesdk.payload

internal data class AppExitInfoData(
    internal val sessionId: String?,

    internal val sessionIdError: String?,

    // the importance of the process that it used to have before the death.
    internal val importance: Int?,

    // Last proportional set size of the memory that the process had used in Bytes.
    internal val pss: Long?,

    internal val reason: Int?,

    // Last resident set size of the memory that the process had used in Bytes.
    internal val rss: Long?,

    // The exit status argument of exit() if the application calls it,
    // or the signal number if the application is signaled.
    internal val status: Int?,

    internal val timestamp: Long?,

    // file with ANR/CRASH traces compressed as string
    internal val trace: String?,

    internal val description: String?,

    // Error or Exception if the traces couldn't be collected
    internal val traceStatus: String?
)
