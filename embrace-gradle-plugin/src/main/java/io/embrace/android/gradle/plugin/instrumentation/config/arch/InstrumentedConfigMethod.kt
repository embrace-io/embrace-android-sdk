package io.embrace.android.gradle.plugin.instrumentation.config.arch

import io.embrace.android.gradle.plugin.instrumentation.config.BooleanReturnValueMethodVisitor
import io.embrace.android.gradle.plugin.instrumentation.config.IntReturnValueMethodVisitor
import io.embrace.android.gradle.plugin.instrumentation.config.LongReturnValueMethodVisitor
import io.embrace.android.gradle.plugin.instrumentation.config.MapReturnValueMethodVisitor
import io.embrace.android.gradle.plugin.instrumentation.config.StringListReturnValueMethodVisitor
import io.embrace.android.gradle.plugin.instrumentation.config.StringReturnValueMethodVisitor
import org.objectweb.asm.MethodVisitor

/**
 * Holds data on how a SDK config method should be instrumented with a configuration value.
 */
class InstrumentedConfigMethod(
    private val functionName: String,
    private val returnType: ReturnType,
    private val valueProvider: () -> Any?
) {

    fun isConfigInstrumentationTarget(name: String, descriptor: String): Boolean {
        return functionName == name && descriptor == returnType.descriptor
    }

    @Suppress("UNCHECKED_CAST")
    fun getMethodVisitor(api: Int, visitor: MethodVisitor): MethodVisitor {
        val result = valueProvider() ?: return visitor
        return when (returnType) {
            ReturnType.BOOLEAN -> BooleanReturnValueMethodVisitor(
                result as Boolean,
                api,
                visitor
            )
            ReturnType.INT -> IntReturnValueMethodVisitor(
                result as Int,
                api,
                visitor
            )
            ReturnType.LONG -> LongReturnValueMethodVisitor(
                result as Long,
                api,
                visitor
            )
            ReturnType.STRING -> StringReturnValueMethodVisitor(
                result as String,
                api,
                visitor
            )
            ReturnType.STRING_LIST -> io.embrace.android.gradle.plugin.instrumentation.config.StringListReturnValueMethodVisitor(
                result as List<String>,
                api,
                visitor
            )
            ReturnType.MAP -> MapReturnValueMethodVisitor(
                result as Map<String, String>,
                api,
                visitor
            )
        }
    }
}
