package io.embrace.android.embracesdk.samples

import io.embrace.android.embracesdk.EmbraceAutomaticVerification
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger

/**
 * Exception Handler that verifies if a VerifyIntegrationException was received,
 * in order to execute restartAppFromPendingIntent
 */
internal class AutomaticVerificationExceptionHandler constructor(
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) :

    Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, exception: Throwable) {
        if (exception.cause?.cause?.javaClass == VerifyIntegrationException::class.java) {
            EmbraceAutomaticVerification.instance.restartAppFromPendingIntent()
        }
        InternalStaticEmbraceLogger.logDebug(
            "Finished handling exception. Delegating to default handler.",
            exception
        )
        defaultHandler?.uncaughtException(thread, exception)
    }
}
