package io.embrace.android.gradle.plugin.instrumentation

import io.embrace.android.gradle.plugin.instrumentation.strategy.ClassInfo
import io.embrace.android.gradle.plugin.instrumentation.visitor.BytecodeInstrumentationFeature
import io.embrace.android.gradle.plugin.instrumentation.visitor.InsertSuperOverrideClassVisitor
import io.embrace.android.gradle.plugin.instrumentation.visitor.InstrumentationTargetClassVisitor
import org.objectweb.asm.ClassVisitor

class VisitorFactoryImpl(
    private val api: Int,
    private val flags: InstrumentationFeatureFlags,
) {

    fun createClassVisitor(
        feature: BytecodeInstrumentationFeature,
        classInfo: ClassInfo,
        nextVisitor: ClassVisitor,
    ): ClassVisitor {
        if (feature.isEnabled(flags, classInfo)) {
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
        flags: InstrumentationFeatureFlags,
        classInfo: ClassInfo,
    ): Boolean {
        val enabledViaDsl = when (name) {
            "fcm_push_notifications" -> flags.shouldInstrumentFirebaseMessaging
            "okhttp" -> flags.shouldInstrumentOkHttp
            "webview_page_start" -> flags.shouldInstrumentWebview
            "auto_sdk_initialization" -> flags.shouldInstrumentAutoSdkInitialization
            "on_click" -> flags.shouldInstrumentOnClick
            "on_long_click" -> flags.shouldInstrumentOnLongClick
            "application_init_time_start" -> flags.applicationInitTimingEnabled
            "application_init_time_end" -> flags.applicationInitTimingEnabled
            else -> error("Unknown feature: $name. Please add a property that enables/disables it on EmbraceBytecodeInstrumentation.")
        }
        return enabledViaDsl && visitStrategy.shouldVisit(classInfo)
    }
}
