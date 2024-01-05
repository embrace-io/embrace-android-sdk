package io.embrace.android.embracesdk.samples

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger

/**
 * Encapsulates the logic to trigger different type of crashes for testing purpose.
 * It is recommended to implement every method call via a button press once the app has loaded.
 * After a crash sent, the app should be restarted in order to see the error in the dashboard.
 */
internal object EmbraceCrashSamples {

    private val logger = InternalEmbraceLogger()

    private val ndkCrashSamplesNdkDelegate = EmbraceCrashSamplesNdkDelegateImpl()

    /**
     * Verifies if Embrace is initialized
     */

    fun isSdkStarted() {
        if (!Embrace.getInstance().isStarted) {
            val e = EmbraceSampleCodeException(
                "Embrace SDK not initialized. Please ensure you have included " +
                    "Embrace.getInstance().start(this) in Application#onCreate()\n" +
                    "and then trigger these crash samples via a button press once the app has loaded."
            )
            logger.logError("Embrace SDK is not initialized", e)
            throw e
        }
    }

    /**
     * verifies if ANR detection is enabled
     */

    fun checkAnrDetectionEnabled() {
        if (!Embrace.getInstance().internalInterface.isAnrCaptureEnabled()) {
            val e = EmbraceSampleCodeException(
                "ANR capture disabled - you need to enable it to test Embrace's ANR functionality:\n" +
                    " - add [\"anr\":{\"pct_enabled\": 100 }]" +
                    " inside the configuration file to enable ANR detection"
            )
            logger.logError("ANR detection disabled", e)
            throw e
        }
    }

    /**
     * Throws a custom JVM exception: EmbraceCrashException
     */
    fun throwJvmException() {
        isSdkStarted()
        throw EmbraceSampleCodeException("Custom JVM Exception")
    }

    /**
     * Block the app's main thread for 4 seconds.
     * Embrace detects this and samples the main thread stacktraces, so you can better debug ANRs.
     */
    fun blockMainThreadForShortInterval() {
        isSdkStarted()
        checkAnrDetectionEnabled()
        try {
            Thread.sleep(SHORT_ANR_4_SEC)
        } catch (e: InterruptedException) {
            logger.logError("Short ANR failed", e)
        }
    }

    /**
     * Force a long ANR that lasts 30 seconds
     */
    fun triggerLongAnr() {
        isSdkStarted()
        checkAnrDetectionEnabled()
        val embrace = Embrace.getInstance()
        val start = embrace.internalInterface.getSdkCurrentTime()
        while (true) {
            if (embrace.internalInterface.getSdkCurrentTime() - start >= LONG_ANR_LENGTH) {
                break
            }
        }
    }

    /**
     * verifies if NDK detection is enabled
     */

    fun checkNdkDetectionEnabled() {
        // First verifies is Embrace SDK is initialized
        isSdkStarted()

        if (!Embrace.getInstance().internalInterface.isNdkEnabled()) {
            val e = EmbraceSampleCodeException(
                "NDK crash capture is disabled - you need to enable it to test Embrace's NDK functionality" +
                    " - To enable it, add [\"ndk_enabled\": true] inside the configuration file"
            )
            logger.logError("NDK detection disabled", e)
            throw e
        }
    }

    fun triggerNdkSigIllegalInstruction() {
        checkNdkDetectionEnabled()
        ndkCrashSamplesNdkDelegate.sigIllegalInstruction()
    }

    fun triggerNdkThrowCppException() {
        checkNdkDetectionEnabled()
        ndkCrashSamplesNdkDelegate.throwException()
    }

    fun triggerNdkSigAbort() {
        checkNdkDetectionEnabled()
        ndkCrashSamplesNdkDelegate.sigAbort()
    }

    fun triggerNdkSigfpe() {
        checkNdkDetectionEnabled()
        ndkCrashSamplesNdkDelegate.sigfpe()
    }

    fun triggerNdkSigsegv() {
        checkNdkDetectionEnabled()
        ndkCrashSamplesNdkDelegate.sigsegv()
    }

    private const val LONG_ANR_LENGTH = 30000
    private const val SHORT_ANR_4_SEC = 4000L
}
