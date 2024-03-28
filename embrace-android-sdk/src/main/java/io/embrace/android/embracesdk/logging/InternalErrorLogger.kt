package io.embrace.android.embracesdk.logging

internal class InternalErrorLogger(
    private val internalErrorService: InternalErrorService,
    private val logger: InternalEmbraceLogger.LoggerAction,
    private val logStrictMode: Boolean = false
) : InternalEmbraceLogger.LoggerAction {

    // TODO: in future we should queue these messages up and add them to the payload when the
    // exception service is ready,
    // so that early error messages don't get lost. We should create a clickup task for this
    // and add it to the Q2 stability work
    override fun log(
        msg: String,
        severity: InternalEmbraceLogger.Severity,
        throwable: Throwable?,
        logStacktrace: Boolean
    ) {
        val finalThrowable = when {
            logStrictMode && severity == InternalEmbraceLogger.Severity.ERROR && throwable == null -> LogStrictModeException(
                msg
            )
            else -> throwable
        }

        if (finalThrowable != null) {
            try {
                internalErrorService.handleInternalError(finalThrowable)
            } catch (exc: Exception) {
                logger.log(exc.localizedMessage ?: "", InternalEmbraceLogger.Severity.ERROR, null, false)
            }
        }
    }

    class LogStrictModeException(msg: String) : Exception(msg)
    class InternalError(msg: String) : Exception(msg)
    class NotAnException(msg: String) : Exception(msg)
}
