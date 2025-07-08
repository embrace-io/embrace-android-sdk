package io.embrace.gradle.plugin.instrumentation

import io.embrace.android.gradle.plugin.instrumentation.VisitorFactoryImpl
import io.embrace.android.gradle.plugin.instrumentation.json.readBytecodeInstrumentationFeatures
import io.embrace.test.fixtures.ActivityOnClickListener
import io.embrace.test.fixtures.AnonInnerClassOnClickListener
import io.embrace.test.fixtures.AnonInnerClassOnLongClickListener
import io.embrace.test.fixtures.ControlObject
import io.embrace.test.fixtures.CustomOnClickListener
import io.embrace.test.fixtures.CustomOnLongClickListener
import io.embrace.test.fixtures.CustomWebViewClient
import io.embrace.test.fixtures.ExtendedCustomWebViewClient
import io.embrace.test.fixtures.ExtendedOnClickListener
import io.embrace.test.fixtures.ExtendedOnLongClickListener
import io.embrace.test.fixtures.FragmentOnClickListener
import io.embrace.test.fixtures.JavaAnonOnClickListener
import io.embrace.test.fixtures.JavaLambdaOnClickListener
import io.embrace.test.fixtures.JavaLambdaOnLongClickListener
import io.embrace.test.fixtures.JavaNested
import io.embrace.test.fixtures.KotlinNested
import io.embrace.test.fixtures.KotlinNestedOnLongClick
import io.embrace.test.fixtures.KotlinObjectOnClickListener
import io.embrace.test.fixtures.MissingInterfaceOnClickListener
import io.embrace.test.fixtures.MissingInterfaceOnLongClickListener
import io.embrace.test.fixtures.MissingOverrideOnClickListener
import io.embrace.test.fixtures.MissingOverrideOnLongClickListener
import io.embrace.test.fixtures.NoOverrideWebViewClient
import io.embrace.test.fixtures.TestApplication
import io.embrace.test.fixtures.VirtualMethodRefNamedOnClick
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.util.TraceClassVisitor

private val features = readBytecodeInstrumentationFeatures()
private val factory = VisitorFactoryImpl(ASM_API_VERSION, FakeBytecodeInstrumentationParams())

private fun createFactory(
    params: BytecodeTestParams,
    visitor: ClassVisitor,
    name: String,
    superClassName: String? = null,
): ClassVisitor {
    val superClasses = when (superClassName) {
        null -> emptyList()
        else -> listOf(superClassName)
    }
    return factory.createClassVisitor(
        feature = features.single { it.name == name },
        classContext = FakeClassContext(FakeClassData(className = params.qualifiedClzName, superClasses = superClasses)),
        nextVisitor = visitor
    )
}

private val onClickFactory: ClassVisitorFactory = { visitor, params ->
    createFactory(params, visitor, "on_click")
}

private val onLongClickFactory: ClassVisitorFactory = { visitor, params ->
    createFactory(params, visitor, "on_long_click")
}

private val webviewFactory: ClassVisitorFactory = { visitor, params ->
    createFactory(params, visitor, "webview_page_start", "android.webkit.WebViewClient")
}

private val autoSdkInitializationFactory: ClassVisitorFactory = { visitor, params ->
    createFactory(params, visitor, "auto_sdk_initialization", "android.app.Application")
}

private val applicationInitTimeStartFactory: ClassVisitorFactory = { visitor, params ->
    createFactory(params, visitor, "application_init_time_start", "android.app.Application")
}

private val applicationInitTimeEndFactory: ClassVisitorFactory = { visitor, params ->
    createFactory(params, visitor, "application_init_time_end", "android.app.Application")
}

/**
 * Verifies that a [ClassVisitor] produces the correct bytecode output for a given class.
 *
 * For example, if a class implements View.OnClickListener then the embrace gradle plugin should
 * instrument the bytecode so that the first line of the onClick method contains a call to our API
 * If a class does not implement the interface, then its
 * bytecode should remain the same.
 *
 * The test achieves this verification using the following approach:
 *
 * 1. Define a class and load it via the default ClassLoader
 * 2. Create a [ClassReader] instance that reads the class in WebObject ASM
 * 3. Process the class using the [ClassVisitor] instance that instruments the bytecode
 * 4. Compare the bytecode representation obtained from a [TraceClassVisitor] with a known output
 *
 * For more information on WebObject ASM, see https://asm.ow2.io/
 */
@RunWith(Parameterized::class)
class InstrumentedBytecodeTest(
    private val params: BytecodeTestParams,
) {

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "{index}: {0}")
        fun testCases() = listOf(
            BytecodeTestParams(clz = ActivityOnClickListener::class, factory = onClickFactory),
            BytecodeTestParams(clz = ControlObject::class, factory = onClickFactory),
            BytecodeTestParams(clz = CustomOnClickListener::class, factory = onClickFactory),
            BytecodeTestParams(clz = ExtendedOnClickListener::class, factory = onClickFactory),
            BytecodeTestParams(clz = FragmentOnClickListener::class, factory = onClickFactory),
            BytecodeTestParams(clz = JavaNested.JavaInnerListener::class, factory = onClickFactory),
            BytecodeTestParams(clz = JavaNested.JavaStaticListener::class, factory = onClickFactory),
            BytecodeTestParams(clz = KotlinNested.KotlinInnerListener::class, factory = onClickFactory),
            BytecodeTestParams(clz = KotlinNested.KotlinStaticListener::class, factory = onClickFactory),
            BytecodeTestParams(clz = MissingInterfaceOnClickListener::class, factory = onClickFactory),
            BytecodeTestParams(clz = MissingOverrideOnClickListener::class, factory = onClickFactory),
            BytecodeTestParams(clz = VirtualMethodRefNamedOnClick::class, factory = onClickFactory),
            BytecodeTestParams(clz = JavaLambdaOnClickListener::class, factory = onClickFactory),
            BytecodeTestParams.forInnerClass(clz = AnonInnerClassOnClickListener::class, innerClzName = "$1", factory = onClickFactory),
            BytecodeTestParams.forInnerClass(clz = JavaAnonOnClickListener::class, innerClzName = "$1", factory = onClickFactory),
            BytecodeTestParams.forInnerClass(
                clz = KotlinObjectOnClickListener::class,
                innerClzName = "\$onCreateView\$1",
                factory = onClickFactory
            ),
            BytecodeTestParams(clz = CustomOnLongClickListener::class, factory = onLongClickFactory),
            BytecodeTestParams(clz = ExtendedOnLongClickListener::class, factory = onLongClickFactory),
            BytecodeTestParams(clz = JavaLambdaOnLongClickListener::class, factory = onLongClickFactory),
            BytecodeTestParams(clz = KotlinNestedOnLongClick.OnLongClickInnerListener::class, factory = onLongClickFactory),
            BytecodeTestParams(clz = KotlinNestedOnLongClick.OnLongClickStaticListener::class, factory = onLongClickFactory),
            BytecodeTestParams(clz = MissingInterfaceOnLongClickListener::class, factory = onLongClickFactory),
            BytecodeTestParams(clz = MissingOverrideOnLongClickListener::class, factory = onLongClickFactory),
            BytecodeTestParams.forInnerClass(
                clz = AnonInnerClassOnLongClickListener::class,
                innerClzName = "$1",
                factory = onLongClickFactory
            ),
            BytecodeTestParams(clz = CustomWebViewClient::class, factory = webviewFactory),
            BytecodeTestParams(clz = ExtendedCustomWebViewClient::class, factory = webviewFactory),
            BytecodeTestParams(clz = TestApplication::class, factory = autoSdkInitializationFactory),
            BytecodeTestParams(
                clz = TestApplication::class,
                factory = applicationInitTimeStartFactory,
                expectedOutput = "TestApplication_application_init_time_start_expected.txt"
            ),
            BytecodeTestParams(
                clz = TestApplication::class,
                factory = applicationInitTimeEndFactory,
                expectedOutput = "TestApplication_application_init_time_end_expected.txt"
            ),
            BytecodeTestParams(clz = NoOverrideWebViewClient::class, factory = webviewFactory),
        )
    }

    @Test
    fun testInstrumentedBytecode() {
        InstrumentationRunner.runInstrumentationAndCompareOutput(params)
    }
}
