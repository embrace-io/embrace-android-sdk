package io.embrace.android.gradle.plugin.instrumentation

import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.InstrumentationContext
import io.embrace.android.gradle.plugin.instrumentation.config.ConfigClassVisitorFactory
import io.embrace.android.gradle.plugin.instrumentation.visitor.FirebaseMessagingServiceClassAdapter
import io.embrace.android.gradle.plugin.instrumentation.visitor.OkHttpClassAdapter
import io.embrace.android.gradle.plugin.instrumentation.visitor.OnClickClassAdapter
import io.embrace.android.gradle.plugin.instrumentation.visitor.OnLongClickClassAdapter
import io.embrace.android.gradle.plugin.instrumentation.visitor.WebViewClientClassAdapter
import org.gradle.api.provider.Property
import org.objectweb.asm.ClassVisitor

internal fun createClassVisitorImpl(
    classContext: ClassContext,
    nextClassVisitor: ClassVisitor,
    instrumentationContext: InstrumentationContext,
    parameters: Property<BytecodeInstrumentationParams>,
    logger: (() -> String) -> Unit
): ClassVisitor {
    val api = instrumentationContext.apiVersion.get()
    var visitor = nextClassVisitor
    val className = classContext.currentClassData.className

    // Add a visitor if this is a config class provided by the SDK
    val cfg = parameters.get().config.get()
    ConfigClassVisitorFactory.createClassVisitor(className, cfg, api, visitor)?.let {
        visitor = it
    }

    // chain our own visitors to avoid unlikely (but possible) cases such as a custom
    // WebViewClient implementing an OnClickListener
    if (parameters.get().shouldInstrumentFirebaseMessaging.get() &&
        FirebaseMessagingServiceClassAdapter.accept(classContext)
    ) {
        visitor = FirebaseMessagingServiceClassAdapter(api, visitor, logger)
        logger { "Added FirebaseMessagingServiceClassAdapter for $className." }
    }

    if (parameters.get().shouldInstrumentWebview.get() && WebViewClientClassAdapter.accept(classContext)) {
        visitor = WebViewClientClassAdapter(api, visitor, logger)
        logger { "Added WebViewClientClassAdapter for $className." }
    }
    if (parameters.get().shouldInstrumentOkHttp.get() && OkHttpClassAdapter.accept(classContext)) {
        visitor = OkHttpClassAdapter(api, visitor, logger)
        logger { "Added OkHttpClassAdapter for $className." }
    }
    if (parameters.get().shouldInstrumentOnLongClick.get() && OnLongClickClassAdapter.accept(classContext)) {
        visitor = OnLongClickClassAdapter(api, visitor, logger)
        logger { "Added OnLongClickClassAdapter for $className." }
    }
    if (parameters.get().shouldInstrumentOnClick.get() && OnClickClassAdapter.accept(classContext)) {
        visitor = OnClickClassAdapter(api, visitor, logger)
        logger { "Added OnClickClassAdapter for $className." }
    }
    return visitor
}
