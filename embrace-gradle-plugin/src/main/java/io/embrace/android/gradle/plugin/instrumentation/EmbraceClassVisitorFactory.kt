package io.embrace.android.gradle.plugin.instrumentation

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import io.embrace.android.gradle.plugin.instrumentation.config.ConfigClassVisitorFactory
import io.embrace.android.gradle.plugin.instrumentation.json.readBytecodeInstrumentationFeatures
import io.embrace.android.gradle.plugin.instrumentation.visitor.InstrumentationTargetClassVisitor
import io.embrace.android.gradle.plugin.instrumentation.visitor.OnClickClassAdapter
import io.embrace.android.gradle.plugin.instrumentation.visitor.OnLongClickClassAdapter
import io.embrace.android.gradle.plugin.instrumentation.visitor.WebViewClientClassAdapter
import org.objectweb.asm.ClassVisitor

/**
 * A factory which creates ClassVisitor objects for instrumenting classes. This effectively
 * determines which classes should go through the instrumentation process, and returns the necessary
 * object to instrument the bytecode.
 *
 * This class should not contain any fields/direct state. Everything needs to be passed through
 * [BytecodeInstrumentationParams] and marked as an input as this is used to support up-to-date checks.
 */
abstract class EmbraceClassVisitorFactory : AsmClassVisitorFactory<BytecodeInstrumentationParams> {

    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor,
    ): ClassVisitor {
        val api = instrumentationContext.apiVersion.get()
        var visitor = nextClassVisitor
        val className = classContext.currentClassData.className

        // Add a visitor if this is a config class provided by the SDK
        val params = parameters.get()
        val cfg = params.config.get()
        val encodedSharedObjectFilesMap = parameters.get().encodedSharedObjectFilesMap.orNull
        ConfigClassVisitorFactory.createClassVisitor(className, cfg, encodedSharedObjectFilesMap, api, visitor)?.let {
            visitor = it
        }

        val behavior = ClassVisitBehavior(params)
        val features = readBytecodeInstrumentationFeatures()
        val fcmFeature = features.single { it.name == "fcm_push_notifications" }
        val okhttpFeature = features.single { it.name == "okhttp" }

        // We take the approach of chaining 1 visitor per feature, if a feature is enabled/necessary
        // for a given class.
        if (params.shouldInstrumentFirebaseMessaging.get() && fcmFeature.visitStrategy.shouldVisit(classContext)) {
            visitor = InstrumentationTargetClassVisitor(
                api = api,
                nextClassVisitor = visitor,
                feature = fcmFeature,
            )
        }
        if (behavior.shouldInstrumentWebview(classContext)) {
            visitor = WebViewClientClassAdapter(api, visitor)
        }
        if (params.shouldInstrumentOkHttp.get() && okhttpFeature.visitStrategy.shouldVisit(classContext)) {
            visitor = InstrumentationTargetClassVisitor(
                api = api,
                nextClassVisitor = visitor,
                feature = okhttpFeature,
            )
        }
        if (behavior.shouldInstrumentOnClick(classContext)) {
            visitor = OnLongClickClassAdapter(api, visitor)
        }
        if (behavior.shouldInstrumentOnLongClick(classContext)) {
            visitor = OnClickClassAdapter(api, visitor)
        }
        return visitor
    }

    override fun isInstrumentable(classData: ClassData): Boolean {
        val params = parameters.get()

        if (params.disabled.get()) {
            return false
        }
        if (params.classInstrumentationFilter.get().shouldSkip(classData.className)) {
            return false
        }

        // any class could implement OnClickListener, so we need to search everything.
        // in future we could confine this search to Activity/Fragment/View implementations at the
        // cost of instrumenting 100% of onClick implementations.
        return true
    }
}
