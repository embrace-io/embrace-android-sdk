package io.embrace.android.gradle.plugin.instrumentation.config.arch.sdk

import io.embrace.android.gradle.plugin.instrumentation.ASM_API_VERSION
import io.embrace.android.gradle.plugin.instrumentation.config.BooleanReturnValueMethodVisitor
import io.embrace.android.gradle.plugin.instrumentation.config.IntReturnValueMethodVisitor
import io.embrace.android.gradle.plugin.instrumentation.config.LongReturnValueMethodVisitor
import io.embrace.android.gradle.plugin.instrumentation.config.MapReturnValueMethodVisitor
import io.embrace.android.gradle.plugin.instrumentation.config.StringListReturnValueMethodVisitor
import io.embrace.android.gradle.plugin.instrumentation.config.StringReturnValueMethodVisitor
import io.embrace.android.gradle.plugin.instrumentation.config.arch.InstrumentedConfigClass
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.objectweb.asm.MethodVisitor

private val nextVisitor: MethodVisitor = mockk(relaxed = true)

fun verifyConfigMethodVisitor(
    instrumentation: InstrumentedConfigClass,
    method: ConfigMethod,
) {
    val visitor = instrumentation.getMethodVisitor(
        method.name,
        method.descriptor,
        ASM_API_VERSION,
        nextVisitor
    )
    assertEquals(
        "Expected ${method.name} to return ${method.result}",
        method.result,
        when (visitor) {
            is BooleanReturnValueMethodVisitor -> visitor.replacedValue
            is IntReturnValueMethodVisitor -> visitor.replacedValue
            is LongReturnValueMethodVisitor -> visitor.replacedValue
            is StringReturnValueMethodVisitor -> visitor.replacedValue
            is StringListReturnValueMethodVisitor -> visitor.replacedValue
            is MapReturnValueMethodVisitor -> visitor.replacedValue
            else -> null
        }
    )
}
