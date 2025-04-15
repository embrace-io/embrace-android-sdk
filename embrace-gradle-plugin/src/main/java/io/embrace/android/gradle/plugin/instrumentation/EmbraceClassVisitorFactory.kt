package io.embrace.android.gradle.plugin.instrumentation

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import io.embrace.android.gradle.plugin.instrumentation.config.ConfigClassVisitorFactory
import io.embrace.android.gradle.plugin.instrumentation.visitor.FirebaseMessagingServiceClassAdapter
import io.embrace.android.gradle.plugin.instrumentation.visitor.OkHttpClassAdapter
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
        nextClassVisitor: ClassVisitor
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

        // chain our own visitors to avoid unlikely (but possible) cases such as a custom
        // WebViewClient implementing an OnClickListener
        if (behavior.shouldInstrumentFirebasePushNotifications(classContext)) {
            visitor = FirebaseMessagingServiceClassAdapter(api, visitor)
        }
        if (behavior.shouldInstrumentWebview(classContext)) {
            visitor = WebViewClientClassAdapter(api, visitor)
        }
        if (behavior.shouldInstrumentOkHttp(classContext)) {
            visitor = OkHttpClassAdapter(api, visitor)
        }
        if (params.shouldInstrumentOnLongClick.get()) {
            visitor = OnLongClickClassAdapter(api, visitor)
        }
        if (params.shouldInstrumentOnClick.get()) {
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
