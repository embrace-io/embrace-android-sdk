package io.embrace.android.gradle.plugin.instrumentation

import com.android.build.api.instrumentation.ClassContext
import io.embrace.android.gradle.plugin.instrumentation.visitor.BytecodeInstrumentationFeature
import io.embrace.android.gradle.plugin.instrumentation.visitor.InstrumentationTargetClassVisitor
import org.objectweb.asm.ClassVisitor

class VisitorFactoryImpl(
    private val api: Int,
    private val params: BytecodeInstrumentationParams,
) {

    fun createClassVisitor(
        feature: BytecodeInstrumentationFeature,
        classContext: ClassContext,
        visitor: ClassVisitor,
    ): ClassVisitor {
        var nextVisitor = visitor
        if (feature.isEnabled(params, classContext)) {
            nextVisitor = InstrumentationTargetClassVisitor(
                api = api,
                nextClassVisitor = nextVisitor,
                feature = feature,
            )
        }
        return nextVisitor
    }

    private fun BytecodeInstrumentationFeature.isEnabled(
        params: BytecodeInstrumentationParams,
        ctx: ClassContext
    ): Boolean {
        val enabledViaDsl = when (name) {
            "fcm_push_notifications" -> params.shouldInstrumentFirebaseMessaging.get()
            "okhttp" -> params.shouldInstrumentOkHttp.get()
            "webview_page_start" -> params.shouldInstrumentWebview.get()
            "on_click" -> params.shouldInstrumentOnClick.get()
            "on_long_click" -> params.shouldInstrumentOnLongClick.get()
            else -> true
        }
        return enabledViaDsl && visitStrategy.shouldVisit(ctx)
    }
}
