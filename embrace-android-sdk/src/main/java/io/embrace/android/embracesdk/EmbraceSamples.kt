package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.annotation.InternalApi
import io.embrace.android.embracesdk.samples.EmbraceCrashSamples

/**
 * Helps to verify and test embrace SDK integration.
 * it allows users to execute code that automatically verifies the integration by calling the verifyIntegration method.
 * It also provides example code to generate ANR and JVM/NDK crashes
 *
 * @suppress
 */
@InternalApi
public object EmbraceSamples {

    private val embraceCrashSamples = EmbraceCrashSamples

    /**
     * Throw a custom JVM crash to be part of current session.
     *
     * It is recommended to implement this method call via a button press once the app has loaded.
     *
     * After a crash is sent, the app should be restarted in order to see the error in the dashboard.
     *
     * @throws EmbraceSampleCodeException
     */
    @JvmStatic
    public fun throwJvmException() {
        embraceCrashSamples.throwJvmException()
    }

    /**
     * Force a short ANR that lasts 4 seconds
     */
    @JvmStatic
    public fun triggerAnr() {
        embraceCrashSamples.blockMainThreadForShortInterval()
    }

    /**
     * Force a long ANR that lasts 30 seconds
     */
    @JvmStatic
    public fun triggerLongAnr() {
        embraceCrashSamples.triggerLongAnr()
    }

    // NDK Crashes sections

    /**
     * Throws a ndk SigIllegalInstruction
     */
    @JvmStatic
    public fun causeNdkIllegalInstruction() {
        embraceCrashSamples.triggerNdkSigIllegalInstruction()
    }
}
