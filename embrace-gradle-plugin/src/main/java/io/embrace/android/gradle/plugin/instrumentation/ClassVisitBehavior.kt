package io.embrace.android.gradle.plugin.instrumentation

import com.android.build.api.instrumentation.ClassContext
import io.embrace.android.gradle.plugin.instrumentation.visitor.WebViewClientClassAdapter

internal class ClassVisitBehavior(private val params: BytecodeInstrumentationParams) {

    fun shouldInstrumentFirebasePushNotifications(classContext: ClassContext): Boolean {
        return params.shouldInstrumentFirebaseMessaging.get() &&
            classContext.currentClassData.superClasses.contains("com.google.firebase.messaging.FirebaseMessagingService")
    }

    fun shouldInstrumentWebview(classContext: ClassContext): Boolean {
        return params.shouldInstrumentWebview.get() &&
            classContext.currentClassData.superClasses.contains(WebViewClientClassAdapter.CLASS_NAME)
    }

    fun shouldInstrumentOkHttp(classContext: ClassContext): Boolean {
        return params.shouldInstrumentOkHttp.get() && classContext.currentClassData.className == "okhttp3.OkHttpClient\$Builder"
    }
}
