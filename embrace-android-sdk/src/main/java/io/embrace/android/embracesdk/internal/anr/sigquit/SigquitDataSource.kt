package io.embrace.android.embracesdk.internal.anr.sigquit

import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.NoInputValidation
import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.config.behavior.AnrBehavior
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.utils.ThreadUtils
import java.util.concurrent.atomic.AtomicBoolean

// IMPORTANT: This class is referenced by anr.c. Move or rename both at the same time, or it will break.
internal class SigquitDataSource(
    private val sharedObjectLoader: SharedObjectLoader,
    private val anrThreadIdDelegate: AnrThreadIdDelegate,
    private val anrBehavior: AnrBehavior,
    private val logger: EmbLogger,
    writer: SessionSpanWriter
) : DataSourceImpl<SessionSpanWriter>(
    writer,
    logger,
    UpToLimitStrategy { 50 }
) {

    private val googleAnrTrackerInstalled = AtomicBoolean(false)

    override fun enableDataCapture() {
        if (!googleAnrTrackerInstalled.getAndSet(true)) {
            ThreadUtils.runOnMainThread(logger) { setupGoogleAnrHandler() }
        }
    }

    fun saveSigquit(timestamp: Long) {
        if (anrBehavior.isGoogleAnrCaptureEnabled()) {
            captureData(NoInputValidation) {
                addEvent(SchemaType.Sigquit, timestamp)
            }
        }
    }

    private fun install(googleThreadId: Int): Int {
        return try {
            val res = installGoogleAnrHandler(googleThreadId)
            if (res > 0) {
                googleAnrTrackerInstalled.set(false)
                logger.logError("Could not initialize Google ANR tracking {code=$res}")
            } else {
                logger.logInfo("Google Anr Tracker handler installed successfully")
            }
            res
        } catch (exception: UnsatisfiedLinkError) {
            logger.logError("Could not install ANR Handler. Exception: $exception")
            1
        }
    }

    private fun setupGoogleAnrHandler() {
        if (!sharedObjectLoader.loadEmbraceNative()) {
            googleAnrTrackerInstalled.set(false)
            return
        }

        // we must find the Google watcher thread in order to install the Google ANR handle.
        val googleThreadId = anrThreadIdDelegate.findGoogleAnrThread()
        if (googleThreadId <= 0) {
            logger.logError("Could not initialize Google ANR tracking: Google thread not found.")
            googleAnrTrackerInstalled.set(false)
            return
        }
        // run the JNI call from main thread since JNI calls return to the thread where
        // they were called.
        install(googleThreadId)
    }

    private external fun installGoogleAnrHandler(googleThreadId: Int): Int
}
