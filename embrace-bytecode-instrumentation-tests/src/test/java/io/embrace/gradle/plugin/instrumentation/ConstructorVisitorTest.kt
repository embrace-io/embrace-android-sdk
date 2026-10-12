package io.embrace.gradle.plugin.instrumentation

import io.embrace.android.gradle.plugin.instrumentation.json.readBytecodeInstrumentationFeatures
import io.embrace.android.gradle.plugin.instrumentation.visitor.BytecodeClassInsertionParams
import io.embrace.android.gradle.plugin.instrumentation.visitor.BytecodeInstrumentationFeature
import io.embrace.android.gradle.plugin.instrumentation.visitor.BytecodeMethodInsertionParams
import io.embrace.android.gradle.plugin.instrumentation.visitor.InstrumentationTargetClassVisitor
import io.embrace.test.fixtures.JavaConstructorTarget
import io.embrace.test.fixtures.KotlinConstructorTarget
import org.junit.Test
import org.objectweb.asm.ClassVisitor

// visitStrategy is internal to the plugin and unused by InstrumentationTargetClassVisitor, so copy an existing feature
internal val constructorFeature: BytecodeInstrumentationFeature = readBytecodeInstrumentationFeatures()
    .single { it.name == "okhttp" }
    .copy(
        name = "constructor",
        targetParams = BytecodeClassInsertionParams(name = "<init>", descriptor = null),
        insertionParams = BytecodeMethodInsertionParams(
            owner = "io/embrace/gradle/plugin/instrumentation/ConstructorHookRecorder",
            name = "onConstructed",
            descriptor = "(Ljava/lang/Object;)V",
            operandStackIndices = listOf(0),
            insertAtEnd = true,
        ),
    )

internal fun createConstructorVisitor(nextVisitor: ClassVisitor): ClassVisitor =
    InstrumentationTargetClassVisitor(ASM_API_VERSION, nextVisitor, constructorFeature)

class ConstructorVisitorTest {

    @Test
    fun `instrument java constructors`() {
        val params = BytecodeTestParams(JavaConstructorTarget::class) { nextVisitor, _ ->
            createConstructorVisitor(nextVisitor)
        }
        InstrumentationRunner.runInstrumentationAndCompareOutput(params)
    }

    @Test
    fun `instrument java inner class constructor`() {
        val params = BytecodeTestParams(
            JavaConstructorTarget.Inner::class,
            expectedOutput = "JavaConstructorTarget_Inner_expected.txt",
        ) { nextVisitor, _ ->
            createConstructorVisitor(nextVisitor)
        }
        InstrumentationRunner.runInstrumentationAndCompareOutput(params)
    }

    @Test
    fun `instrument kotlin constructors`() {
        val params = BytecodeTestParams(KotlinConstructorTarget::class) { nextVisitor, _ ->
            createConstructorVisitor(nextVisitor)
        }
        InstrumentationRunner.runInstrumentationAndCompareOutput(params)
    }
}
