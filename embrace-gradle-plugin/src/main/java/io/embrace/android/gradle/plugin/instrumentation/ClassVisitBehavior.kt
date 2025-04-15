package io.embrace.android.gradle.plugin.instrumentation

import com.android.build.api.instrumentation.ClassContext
import io.embrace.android.gradle.plugin.instrumentation.visitor.FirebaseMessagingServiceClassAdapter
import io.embrace.android.gradle.plugin.instrumentation.visitor.OkHttpClassAdapter.Companion.CLASS_NAME
import io.embrace.android.gradle.plugin.instrumentation.visitor.WebViewClientClassAdapter

internal class ClassVisitBehavior(private val params: BytecodeInstrumentationParams) {

    fun shouldInstrumentFirebasePushNotifications(classContext: ClassContext): Boolean {
        return params.shouldInstrumentFirebaseMessaging.get() &&
            classContext.currentClassData.superClasses.contains(FirebaseMessagingServiceClassAdapter.CLASS_NAME)
    }

    fun shouldInstrumentWebview(classContext: ClassContext): Boolean {
        return params.shouldInstrumentWebview.get() &&
            classContext.currentClassData.superClasses.contains(WebViewClientClassAdapter.CLASS_NAME)
    }

    fun shouldInstrumentOkHttp(classContext: ClassContext): Boolean {
        return params.shouldInstrumentOkHttp.get() && classContext.currentClassData.className == CLASS_NAME
    }
}
