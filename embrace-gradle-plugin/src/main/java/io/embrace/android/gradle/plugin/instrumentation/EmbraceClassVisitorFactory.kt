package io.embrace.android.gradle.plugin.instrumentation

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import io.embrace.android.gradle.plugin.instrumentation.config.ConfigClassVisitorFactory
import io.embrace.android.gradle.plugin.instrumentation.json.readBytecodeInstrumentationFeatures
import io.embrace.android.gradle.plugin.instrumentation.visitor.InstrumentationTargetClassVisitor
import io.embrace.android.gradle.plugin.instrumentation.visitor.WebViewClientOverrideClassAdapter
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

        val features = readBytecodeInstrumentationFeatures()
        val fcmFeature = features.single { it.name == "fcm_push_notifications" }
        val okhttpFeature = features.single { it.name == "okhttp" }
        val webviewFeature = features.single { it.name == "webview_page_start" }
        val onClickFeature = features.single { it.name == "on_click" }
        val onLongClickFeature = features.single { it.name == "on_long_click" }
        val factory = VisitorFactoryImpl(api, params)

        // We take the approach of chaining 1 visitor per feature, if a feature is enabled/necessary
        // for a given class.
        visitor = factory.createClassVisitor(fcmFeature, classContext, visitor)
        if (params.shouldInstrumentWebview.get() && webviewFeature.visitStrategy.shouldVisit(classContext)) {
            // first, add override for onPageStarted if class doesn't contain it already
            visitor = WebViewClientOverrideClassAdapter(api, visitor)

            // then, instrument the onPageStarted method
            visitor = InstrumentationTargetClassVisitor(
                api = api,
                nextClassVisitor = visitor,
                feature = webviewFeature,
            )
        }
        visitor = factory.createClassVisitor(okhttpFeature, classContext, visitor)
        visitor = factory.createClassVisitor(onClickFeature, classContext, visitor)
        visitor = factory.createClassVisitor(onLongClickFeature, classContext, visitor)
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
        return true
    }
}
