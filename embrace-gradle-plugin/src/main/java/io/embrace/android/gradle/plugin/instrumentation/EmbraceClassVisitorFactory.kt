package io.embrace.android.gradle.plugin.instrumentation

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import io.embrace.android.gradle.plugin.Logger
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
        return createClassVisitorImpl(
            classContext,
            nextClassVisitor,
            instrumentationContext,
            parameters
        ) {
            logger.info(it())
        }
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
        // cost of Swazzling 100% of onClick implementations.
        return true
    }
}

private val logger = Logger(EmbraceClassVisitorFactory::class.java)
