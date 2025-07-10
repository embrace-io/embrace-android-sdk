package io.embrace.gradle.plugin.instrumentation

import io.embrace.android.gradle.plugin.instrumentation.visitor.BytecodeMethodInsertionParams
import io.embrace.android.gradle.plugin.instrumentation.visitor.InstrumentationTargetMethodEndVisitor
import io.embrace.test.fixtures.MethodReturnValueVisitorObj
import io.embrace.test.fixtures.TargetMethodEndVisitorObj
import org.junit.Test
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor

class InstrumentationTargetMethodEndVisitorTest {

    @Test
    fun `instrument bytecode`() {
        val params = BytecodeTestParams(TargetMethodEndVisitorObj::class) { nextVisitor, _ ->
            TestClassVisitor(ASM_API_VERSION, nextVisitor)
        }
        InstrumentationRunner.runInstrumentationAndCompareOutput(params)
    }

    /**
     * Visits the [MethodReturnValueVisitorObj] class and alters the return values for each function.
     */
    class TestClassVisitor(
        api: Int,
        nextClassVisitor: ClassVisitor?
    ) : ClassVisitor(api, nextClassVisitor) {

        private val testMethods = listOf(
            "instrumentAtEndWithReturn",
            "instrumentAtEndWithoutReturn",
            "instrumentAtEndWithThrow",
            "instrumentAtEndWithMultipleReturns",
        )

        private val methodDescriptor = "()V"

        override fun visitMethod(
            access: Int,
            name: String?,
            descriptor: String?,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor {
            val nextVisitor = super.visitMethod(access, name, descriptor, signature, exceptions)

            if (name in testMethods && descriptor == methodDescriptor) {
                return InstrumentationTargetMethodEndVisitor(
                    api,
                    nextVisitor,
                    BytecodeMethodInsertionParams(
                        "io/embrace/gradle/plugin/instrumentation/InjectedClass",
                        "injectedMethod",
                        "()V",
                        emptyList(),
                        true
                    )
                )
            }
            return nextVisitor
        }
    }
}
