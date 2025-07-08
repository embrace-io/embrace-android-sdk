package io.embrace.android.gradle.plugin.instrumentation

import com.android.build.api.instrumentation.ClassContext
import io.embrace.android.gradle.plugin.instrumentation.visitor.BytecodeInstrumentationFeature
import io.embrace.android.gradle.plugin.instrumentation.visitor.InsertSuperOverrideClassVisitor
import io.embrace.android.gradle.plugin.instrumentation.visitor.InstrumentationTargetClassVisitor
import org.objectweb.asm.ClassVisitor

class VisitorFactoryImpl(
    private val api: Int,
    private val params: BytecodeInstrumentationParams,
) {

    fun createClassVisitor(
        feature: BytecodeInstrumentationFeature,
        classContext: ClassContext,
        nextVisitor: ClassVisitor,
    ): ClassVisitor {
        if (feature.isEnabled(params, classContext)) {
            return if (feature.addOverrideParams != null) {
                // if specified, add an override for a method if it doesn't already exist in the class
                InsertSuperOverrideClassVisitor(
                    api = api,
                    nextClassVisitor = nextVisitor,
                    feature = feature,
                )
            } else {
                InstrumentationTargetClassVisitor(
                    api = api,
                    nextClassVisitor = nextVisitor,
                    feature = feature,
                )
            }
        }
        return nextVisitor
    }

    private fun BytecodeInstrumentationFeature.isEnabled(
        params: BytecodeInstrumentationParams,
        ctx: ClassContext,
    ): Boolean {
        val enabledViaDsl = when (name) {
            "fcm_push_notifications" -> params.shouldInstrumentFirebaseMessaging.get()
            "okhttp" -> params.shouldInstrumentOkHttp.get()
            "webview_page_start" -> params.shouldInstrumentWebview.get()
            "auto_sdk_initialization" -> params.shouldInstrumentAutoSdkInitialization.get()
            "on_click" -> params.shouldInstrumentOnClick.get()
            "on_long_click" -> params.shouldInstrumentOnLongClick.get()
            "application_init_time_start" -> params.shouldInstrumentApplicationInitTimeStart.get()
            "application_init_time_end" -> params.shouldInstrumentApplicationInitTimeEnd.get()
            else -> error("Unknown feature: $name. Please add a property that enables/disables it on EmbraceBytecodeInstrumentation.")
        }
        return enabledViaDsl && visitStrategy.shouldVisit(ctx)
    }
}
