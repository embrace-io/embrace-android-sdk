package io.embrace.android.embracesdk.minified

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

private const val JNI_DELEGATE = "io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.jni.JniDelegateImpl"

/**
 * JNI_OnLoad in libembrace-native calls FindClass + RegisterNatives on [JNI_DELEGATE] with the
 * method names and signatures hardcoded in emb_ndk_manager.c. If R8 renames the class or any of its
 * native methods, registration silently fails and the first native call throws UnsatisfiedLinkError.
 */
@RunWith(AndroidJUnit4::class)
internal class JniRegistrationTest {

    @Test
    fun nativeMethodsAreKept() {
        assertAllKept(
            listOf(
                ExpectedName.Method(JNI_DELEGATE, "installSignalHandlers", 3),
                ExpectedName.Method(JNI_DELEGATE, "getCrashReport", 1),
                ExpectedName.Method(JNI_DELEGATE, "onSessionChange", 3),
                ExpectedName.Method(JNI_DELEGATE, "checkForOverwrittenHandlers", 0),
                ExpectedName.Method(JNI_DELEGATE, "reinstallSignalHandlers", 0),
            )
        )
    }

    @Test
    fun nativeMethodsAreRegistered() {
        System.loadLibrary("embrace-native")
        val cls = Class.forName(JNI_DELEGATE, true, appClassLoader)
        val delegate = cls.getDeclaredConstructor().newInstance()

        // harmless without signal handlers installed: it only inspects the current handlers.
        // Throws UnsatisfiedLinkError if RegisterNatives did not bind the method.
        cls.getDeclaredMethod("checkForOverwrittenHandlers").invoke(delegate)
    }
}
