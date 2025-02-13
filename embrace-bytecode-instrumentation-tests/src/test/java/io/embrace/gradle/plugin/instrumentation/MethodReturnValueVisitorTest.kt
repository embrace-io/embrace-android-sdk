package io.embrace.gradle.plugin.instrumentation

import io.embrace.android.gradle.plugin.instrumentation.config.BooleanReturnValueMethodVisitor
import io.embrace.android.gradle.plugin.instrumentation.config.IntReturnValueMethodVisitor
import io.embrace.android.gradle.plugin.instrumentation.config.LongReturnValueMethodVisitor
import io.embrace.android.gradle.plugin.instrumentation.config.MapReturnValueMethodVisitor
import io.embrace.android.gradle.plugin.instrumentation.config.StringListReturnValueMethodVisitor
import io.embrace.android.gradle.plugin.instrumentation.config.StringReturnValueMethodVisitor
import io.embrace.test.fixtures.MethodReturnValueVisitorObj
import org.junit.Test
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor

class MethodReturnValueVisitorTest {

    companion object {
        private const val BOOL_METHOD_NAME = "getSomeBool"
        private const val BOOL_METHOD_DESCRIPTOR = "()Z"
        private const val LONG_METHOD_NAME = "getSomeLong"
        private const val LONG_METHOD_DESCRIPTOR = "()J"
        private const val INT_METHOD_NAME = "getSomeInt"
        private const val INT_METHOD_DESCRIPTOR = "()I"
        private const val STRING_METHOD_NAME = "getSomeStr"
        private const val STRING_METHOD_DESCRIPTOR = "()Ljava/lang/String;"
        private const val LIST_METHOD_NAME = "getSomeList"
        private const val LIST_METHOD_DESCRIPTOR = "()Ljava/util/List;"
        private const val MAP_METHOD_NAME = "getSomeMap"
        private const val MAP_METHOD_DESCRIPTOR = "()Ljava/util/Map;"
    }

    @Test
    fun `instrument bytecode`() {
        val params = BytecodeTestParams(MethodReturnValueVisitorObj::class.java) { nextVisitor ->
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

        override fun visitMethod(
            access: Int,
            name: String?,
            descriptor: String?,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor {
            val nextVisitor = super.visitMethod(access, name, descriptor, signature, exceptions)

            if (name == BOOL_METHOD_NAME && descriptor == BOOL_METHOD_DESCRIPTOR) {
                return BooleanReturnValueMethodVisitor(
                    true,
                    api,
                    nextVisitor
                )
            } else if (name == LONG_METHOD_NAME && descriptor == LONG_METHOD_DESCRIPTOR) {
                return LongReturnValueMethodVisitor(
                    150900202020202L,
                    api,
                    nextVisitor
                )
            } else if (name == INT_METHOD_NAME && descriptor == INT_METHOD_DESCRIPTOR) {
                return IntReturnValueMethodVisitor(
                    520,
                    api,
                    nextVisitor
                )
            } else if (name == STRING_METHOD_NAME && descriptor == STRING_METHOD_DESCRIPTOR) {
                return StringReturnValueMethodVisitor("Hello world! I'm a string.", api, nextVisitor)
            } else if (name == LIST_METHOD_NAME && descriptor == LIST_METHOD_DESCRIPTOR) {
                return io.embrace.android.gradle.plugin.instrumentation.config.StringListReturnValueMethodVisitor(
                    listOf("adam aardvark", "bob banana"),
                    api,
                    nextVisitor
                )
            } else if (name == MAP_METHOD_NAME && descriptor == MAP_METHOD_DESCRIPTOR) {
                return MapReturnValueMethodVisitor(
                    mapOf("adam" to "1", "bob" to "2"),
                    api,
                    nextVisitor
                )
            }
            return nextVisitor
        }
    }
}
