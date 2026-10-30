package io.embrace.bytecode.config.arch.sdk

import io.embrace.bytecode.ASM_API_VERSION
import io.embrace.bytecode.config.BooleanReturnValueMethodVisitor
import io.embrace.bytecode.config.EnumReturnValueMethodVisitor
import io.embrace.bytecode.config.IntReturnValueMethodVisitor
import io.embrace.bytecode.config.LongReturnValueMethodVisitor
import io.embrace.bytecode.config.MapReturnValueMethodVisitor
import io.embrace.bytecode.config.StringListReturnValueMethodVisitor
import io.embrace.bytecode.config.StringReturnValueMethodVisitor
import io.embrace.bytecode.config.arch.InstrumentedConfigClass
import org.junit.Assert.assertEquals
import org.objectweb.asm.MethodVisitor

private val nextVisitor: MethodVisitor = object : MethodVisitor(ASM_API_VERSION) {}

fun verifyConfigMethodVisitor(
    instrumentation: InstrumentedConfigClass,
    method: ConfigMethod,
) {
    val visitor = instrumentation.getMethodVisitor(
        method.name,
        method.descriptor,
        ASM_API_VERSION,
        nextVisitor,
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
            is EnumReturnValueMethodVisitor -> visitor.replacedValue
            else -> null
        },
    )
}
