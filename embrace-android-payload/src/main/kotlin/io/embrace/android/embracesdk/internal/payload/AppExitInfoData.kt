package io.embrace.android.embracesdk.internal.payload

data class AppExitInfoData(
    val sessionId: String?,

    val sessionIdError: String?,

    // the importance of the process that it used to have before the death.
    val importance: Int?,

    // Last proportional set size of the memory that the process had used in Bytes.
    val pss: Long?,

    val reason: Int?,

    // Last resident set size of the memory that the process had used in Bytes.
    val rss: Long?,

    // The exit status argument of exit() if the application calls it,
    // or the signal number if the application is signaled.
    val status: Int?,

    val timestamp: Long?,

    // file with ANR/CRASH traces compressed as string
    val trace: String?,

    val description: String?,

    // Error or Exception if the traces couldn't be collected
    val traceStatus: String?
)
