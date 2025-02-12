package io.embrace.gradle.plugin.instrumentation

import io.embrace.android.gradle.plugin.instrumentation.visitor.OkHttpClassAdapter
import io.embrace.android.gradle.plugin.instrumentation.visitor.OnClickClassAdapter
import io.embrace.android.gradle.plugin.instrumentation.visitor.OnLongClickClassAdapter
import io.embrace.android.gradle.plugin.instrumentation.visitor.WebViewClientClassAdapter
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
import io.embrace.test.fixtures.VirtualMethodRefNamedOnClick
import okhttp3.OkHttpClient
import org.objectweb.asm.ClassVisitor

private val onClickFactory: ClassVisitorFactory = { visitor ->
    OnClickClassAdapter(ASM_API_VERSION, visitor) {}
}

private val onLongClickFactory: ClassVisitorFactory = { visitor ->
    OnLongClickClassAdapter(ASM_API_VERSION, visitor) {}
}

private val webviewFactory: ClassVisitorFactory = { visitor ->
    WebViewClientClassAdapter(ASM_API_VERSION, visitor) {}
}

private val okHttpFactory: ClassVisitorFactory = { visitor ->
    OkHttpClassAdapter(ASM_API_VERSION, visitor) {}
}

/**
 * Declares the test cases for bytecode in [InstrumentedBytecodeTest]. You should define the
 * input class, the expected output, and the [ClassVisitor] which will instrument the bytecode.
 *
 * After doing that, [InstrumentedBytecodeTest] will perform all the necessary checks automatically!
 */
internal fun instrumentedBytecodeTestCases(): List<BytecodeTestParams> {
    return onClickTestCases
        .plus(onClickInnerTestCases)
        .plus(onLongClickTestCases)
        .plus(onLongClickInnerTestCases)
        .plus(webclientTestCases)
        .plus(okHttpTestCases)
        .distinct() // filter out any unintentional duplicate test cases
        .sortedBy(BytecodeTestParams::simpleClzName)
}

private val okHttpTestCases = listOf(
    OkHttpClient.Builder::class
).map {
    BytecodeTestParams(it.java, factory = okHttpFactory)
}
private val webclientTestCases = listOf(
    CustomWebViewClient::class,
    ExtendedCustomWebViewClient::class,
    NoOverrideWebViewClient::class
).map {
    BytecodeTestParams(it.java, factory = webviewFactory)
}

private val onClickTestCases = listOf(
    ActivityOnClickListener::class,
    ControlObject::class,
    CustomOnClickListener::class,
    ExtendedOnClickListener::class,
    FragmentOnClickListener::class,
    JavaNested.JavaInnerListener::class,
    JavaNested.JavaStaticListener::class,
    KotlinNested.KotlinInnerListener::class,
    KotlinNested.KotlinStaticListener::class,
    MissingInterfaceOnClickListener::class,
    MissingOverrideOnClickListener::class,
    VirtualMethodRefNamedOnClick::class,
    JavaLambdaOnClickListener::class,
).map { clz ->
    BytecodeTestParams(clz.java, factory = onClickFactory)
}

private val onClickInnerTestCases = listOf(
    BytecodeTestParams.forInnerClass(
        AnonInnerClassOnClickListener::class,
        "$1",
        factory = onClickFactory
    ),
    BytecodeTestParams.forInnerClass(
        JavaAnonOnClickListener::class,
        "$1",
        factory = onClickFactory
    ),
    BytecodeTestParams.forInnerClass(
        KotlinObjectOnClickListener::class,
        "\$onCreateView$1",
        factory = onClickFactory
    )
)

private val onLongClickTestCases = listOf(
    CustomOnLongClickListener::class,
    ExtendedOnLongClickListener::class,
    JavaLambdaOnLongClickListener::class,
    KotlinNestedOnLongClick.OnLongClickInnerListener::class,
    KotlinNestedOnLongClick.OnLongClickStaticListener::class,
    MissingInterfaceOnLongClickListener::class,
    MissingOverrideOnLongClickListener::class,
).map { clz ->
    BytecodeTestParams(clz.java, factory = onLongClickFactory)
}

private val onLongClickInnerTestCases = listOf(
    BytecodeTestParams.forInnerClass(
        AnonInnerClassOnLongClickListener::class,
        "$1",
        factory = onLongClickFactory
    )
)
