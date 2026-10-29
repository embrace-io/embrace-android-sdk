package io.embrace.android.gradle.plugin.instrumentation

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import io.embrace.bytecode.BytecodeInstrumenter
import io.embrace.bytecode.InstrumentationFeatureFlags
import io.embrace.bytecode.strategy.ClassInfo
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
        val classData = classContext.currentClassData
        return createInstrumenter().createClassVisitor(
            ClassInfo(classData.className, classData.superClasses),
            instrumentationContext.apiVersion.get(),
            nextClassVisitor,
        )
    }

    override fun isInstrumentable(classData: ClassData): Boolean =
        createInstrumenter().isInstrumentable(classData.className)

    private fun createInstrumenter(): BytecodeInstrumenter {
        val params = parameters.get()
        return BytecodeInstrumenter(
            config = params.config.get(),
            variantOutputInfo = params.variantOutputInfo.get(),
            encodedSharedObjectFilesMap = { readTextFromFile(params.encodedSharedObjectFilesMap) },
            reactNativeBundleId = { readTextFromFile(params.reactNativeBundleId) },
            flags = InstrumentationFeatureFlags(
                shouldInstrumentFirebaseMessaging = params.shouldInstrumentFirebaseMessaging.get(),
                shouldInstrumentWebview = params.shouldInstrumentWebview.get(),
                shouldInstrumentAutoSdkInitialization = params.shouldInstrumentAutoSdkInitialization.get(),
                shouldInstrumentOkHttp = params.shouldInstrumentOkHttp.get(),
                shouldInstrumentOnLongClick = params.shouldInstrumentOnLongClick.get(),
                shouldInstrumentOnClick = params.shouldInstrumentOnClick.get(),
                applicationInitTimingEnabled = params.applicationInitTimingEnabled.get(),
            ),
            filter = params.classInstrumentationFilter.get(),
            disabled = params.disabled.get(),
        )
    }

    private fun readTextFromFile(file: RegularFileProperty) =
        file.orNull?.asFile
            ?.takeIf { it.exists() }
            ?.bufferedReader()
            ?.use { it.readText() }
}
