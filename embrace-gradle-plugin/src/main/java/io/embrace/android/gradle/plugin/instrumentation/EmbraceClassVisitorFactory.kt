package io.embrace.android.gradle.plugin.instrumentation

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import io.embrace.android.gradle.plugin.instrumentation.config.ConfigClassVisitorFactory
import io.embrace.android.gradle.plugin.instrumentation.json.readBytecodeInstrumentationFeatures
import org.gradle.api.file.RegularFileProperty
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
        val reactNativeBundleId = readTextFromFile(params.reactNativeBundleId)
        val variantOutputInfo = params.variantOutputInfo.get()
        val encodedSharedObjectFilesMap = readTextFromFile(params.encodedSharedObjectFilesMap)
        ConfigClassVisitorFactory.createClassVisitor(
            className,
            cfg,
            encodedSharedObjectFilesMap,
            variantOutputInfo,
            reactNativeBundleId,
            api,
            visitor
        )?.let {
            visitor = it
        }

        val features = readBytecodeInstrumentationFeatures()
        val factory = VisitorFactoryImpl(api, params)

        features.forEach { feature ->
            visitor = factory.createClassVisitor(feature, classContext, visitor)
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
        return true
    }

    private fun readTextFromFile(file: RegularFileProperty) =
        file.orNull?.asFile
            ?.takeIf { it.exists() }
            ?.bufferedReader()
            ?.use { it.readText() }
}
