package io.embrace.android.embracesdk.anr.sigquit

import io.embrace.android.embracesdk.logging.InternalEmbraceLogger

// IMPORTANT: This class is referenced by anr.c. Move or rename both at the same time, or it will break.
internal class GoogleAnrHandlerNativeDelegate(
    private val googleAnrTimestampRepository: GoogleAnrTimestampRepository,
    private val logger: InternalEmbraceLogger
) {

    fun install(googleThreadId: Int): Int {
        return try {
            installGoogleAnrHandler(googleThreadId)
        } catch (exception: UnsatisfiedLinkError) {
            logger.logError("Could not install ANR Handler. Exception: $exception")
            1
        }
    }

    @Synchronized
    fun saveGoogleAnr(timestamp: Long) {
        logger.logInfo("got Google ANR timestamp $timestamp")
        googleAnrTimestampRepository.add(timestamp)
    }

    private external fun installGoogleAnrHandler(googleThreadId: Int): Int
}
