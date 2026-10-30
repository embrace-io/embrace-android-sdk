package io.embrace.android.gradle.plugin.instrumentation

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import io.embrace.android.gradle.plugin.instrumentation.config.ConfigClassVisitorFactory
import io.embrace.android.gradle.plugin.instrumentation.json.readBytecodeInstrumentationFeatures
import io.embrace.android.gradle.plugin.instrumentation.strategy.ClassInfo
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
        val classData = classContext.currentClassData
        val className = classData.className

        // Add a visitor if this is a config class provided by the SDK. Only config classes need the inputs read
        // from files, so skip reading them for every other class.
        val params = parameters.get()
        if (ConfigClassVisitorFactory.isConfigClass(className)) {
            ConfigClassVisitorFactory.createClassVisitor(
                className,
                params.config.get(),
                readTextFromFile(params.encodedSharedObjectFilesMap),
                params.variantOutputInfo.get(),
                readTextFromFile(params.reactNativeBundleId),
                api,
                visitor,
            )?.let {
                visitor = it
            }
        }

        val features = readBytecodeInstrumentationFeatures()
        val flags = InstrumentationFeatureFlags(
            shouldInstrumentFirebaseMessaging = params.shouldInstrumentFirebaseMessaging.get(),
            shouldInstrumentWebview = params.shouldInstrumentWebview.get(),
            shouldInstrumentAutoSdkInitialization = params.shouldInstrumentAutoSdkInitialization.get(),
            shouldInstrumentOkHttp = params.shouldInstrumentOkHttp.get(),
            shouldInstrumentOnLongClick = params.shouldInstrumentOnLongClick.get(),
            shouldInstrumentOnClick = params.shouldInstrumentOnClick.get(),
            applicationInitTimingEnabled = params.applicationInitTimingEnabled.get(),
        )
        val factory = VisitorFactoryImpl(api, flags)
        val classInfo = ClassInfo(className, classData.superClasses)

        features.forEach { feature ->
            visitor = factory.createClassVisitor(feature, classInfo, visitor)
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
