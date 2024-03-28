package io.embrace.android.embracesdk.anr.sigquit

import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.session.MemoryCleanerListener
import io.embrace.android.embracesdk.utils.ThreadUtils
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

internal class SigquitDetectionService(
    private val sharedObjectLoader: SharedObjectLoader,
    private val findGoogleThread: FindGoogleThread,
    private val googleAnrHandlerNativeDelegate: GoogleAnrHandlerNativeDelegate,
    private val googleAnrTimestampRepository: GoogleAnrTimestampRepository,
    var configService: ConfigService,
    private val logger: InternalEmbraceLogger
) : MemoryCleanerListener {

    private val googleAnrTrackerInstalled = AtomicBoolean(false)

    private fun installGoogleAnrHandler(googleThreadId: Int) {
        val res = googleAnrHandlerNativeDelegate.install(googleThreadId)
        if (res > 0) {
            googleAnrTrackerInstalled.set(false)
            logger.logError(
                String.format(
                    Locale.US,
                    "Could not initialize Google ANR tracking {code=%d}",
                    res
                )
            )
        } else {
            logger.logInfo("Google Anr Tracker handler installed successfully")
        }
    }

    fun initializeGoogleAnrTracking() {
        if (configService.anrBehavior.isGoogleAnrCaptureEnabled()) {
            setupGoogleAnrTracking()
        } else {
            // always install the handler. if config subsequently changes we won't install the tracker twice, nor
            // we will install it if it's disabled.
            configService.addListener { setupGoogleAnrTracking() }
        }
    }

    private fun setupGoogleAnrTracking() {
        if (configService.anrBehavior.isGoogleAnrCaptureEnabled() && !googleAnrTrackerInstalled.getAndSet(true)) {
            ThreadUtils.runOnMainThread(logger) { setupGoogleAnrHandler() }
        }
    }

    fun setupGoogleAnrHandler() {
        // TODO: split up the ANR tracking and NDK crash reporter libs
        if (!sharedObjectLoader.loadEmbraceNative()) {
            googleAnrTrackerInstalled.set(false)
            return
        }

        // we must find the Google watcher thread in order to install the Google ANR handle.
        val googleThreadId = findGoogleThread.invoke()
        if (googleThreadId <= 0) {
            logger.logError("Could not initialize Google ANR tracking: Google thread not found.")
            googleAnrTrackerInstalled.set(false)
            return
        }
        // run the JNI call from main thread since JNI calls return to the thread where
        // they were called.
        installGoogleAnrHandler(googleThreadId)
    }

    override fun cleanCollections() {
        googleAnrTimestampRepository.clear()
    }
}
