package io.embrace.android.embracesdk.internal.payload

public data class AppExitInfoData(
    public val sessionId: String?,

    public val sessionIdError: String?,

    // the importance of the process that it used to have before the death.
    public val importance: Int?,

    // Last proportional set size of the memory that the process had used in Bytes.
    public val pss: Long?,

    public val reason: Int?,

    // Last resident set size of the memory that the process had used in Bytes.
    public val rss: Long?,

    // The exit status argument of exit() if the application calls it,
    // or the signal number if the application is signaled.
    public val status: Int?,

    public val timestamp: Long?,

    // file with ANR/CRASH traces compressed as string
    public val trace: String?,

    public val description: String?,

    // Error or Exception if the traces couldn't be collected
    public val traceStatus: String?
)
