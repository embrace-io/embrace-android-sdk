package io.embrace.android.gradle.plugin.instrumentation

import com.android.build.api.instrumentation.ClassContext
import io.embrace.android.gradle.plugin.instrumentation.fakes.TestBytecodeInstrumentationParams
import io.embrace.android.gradle.plugin.instrumentation.fakes.TestClassData
import io.embrace.android.gradle.plugin.instrumentation.fakes.TestClassVisitor
import io.embrace.android.gradle.plugin.instrumentation.fakes.TestVisitorFactoryImpl
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbraceClassVisitorFactoryTest {

    private val clzDataString = TestClassData(checkNotNull(String::class.qualifiedName))
    private val clzDataBool = TestClassData(checkNotNull(Boolean::class.qualifiedName))

    @Test
    fun testAsmApiEnabled() {
        val factory = TestVisitorFactoryImpl(params = TestBytecodeInstrumentationParams(disabled = false))
        assertTrue(factory.isInstrumentable(clzDataString))
        assertTrue(factory.isInstrumentable(clzDataBool))
    }

    @Test
    fun testAsmApiDisabled() {
        val factory = TestVisitorFactoryImpl(params = TestBytecodeInstrumentationParams(disabled = true))
        assertFalse(factory.isInstrumentable(clzDataString))
        assertFalse(factory.isInstrumentable(clzDataBool))
    }

    @Test
    fun testClassFiltering() {
        val filter = ClassInstrumentationFilter(mutableListOf("kotlin.Boolean"))
        val params = TestBytecodeInstrumentationParams(
            disabled = false,
            classInstrumentationFilter = filter
        )
        val factory = TestVisitorFactoryImpl(params = params)
        assertTrue(factory.isInstrumentable(clzDataString))
        assertFalse(factory.isInstrumentable(clzDataBool))
    }

    @Test
    fun testWebViewClassVisitorDisabled() {
        val visitor = TestClassVisitor()
        val ctx = createMockClassContext("android.webkit.WebViewClient")
        val config = createInstrumentationConfig(
            instrumentOnClick = false,
            instrumentOnLongClick = false,
            instrumentWebview = false
        )
        val observed = fetchClassVisitor(config, ctx, visitor)
        assertSame(visitor, observed)
    }

    @Test
    fun testAutoSdkInitializationClassVisitorDisabled() {
        val visitor = TestClassVisitor()
        val ctx = createMockClassContext("android.app.Application")
        val config = createInstrumentationConfig(
            instrumentOnClick = false,
            instrumentOnLongClick = false,
            instrumentAutoSdkInitialization = false,
            applicationInitTimingEnabled = false,
        )
        val observed = fetchClassVisitor(config, ctx, visitor)
        assertSame(visitor, observed)
    }

    @Test
    fun testOkHttpClassVisitorDisabled() {
        val visitor = TestClassVisitor()
        val ctx = createMockClassContext("okhttp3.OkHttpClient\$Builder")
        val config = createInstrumentationConfig(
            instrumentOnClick = false,
            instrumentOnLongClick = false,
            instrumentOkHttp = false
        )
        val observed = fetchClassVisitor(config, ctx, visitor)
        assertSame(visitor, observed)
    }

    @Test
    fun testFcmClassVisitorDisabled() {
        val visitor = TestClassVisitor()
        val ctx = createMockClassContext("com.google.firebase.messaging.FirebaseMessagingService")
        val config = createInstrumentationConfig(
            instrumentOnClick = false,
            instrumentOnLongClick = false,
            instrumentFirebaseMessaging = false,
        )
        val observed = fetchClassVisitor(config, ctx, visitor)
        assertSame(visitor, observed)
    }

    private fun fetchClassVisitor(
        testParams: TestBytecodeInstrumentationParams,
        ctx: ClassContext,
        visitor: TestClassVisitor
    ) = TestVisitorFactoryImpl(params = testParams).createClassVisitor(
        ctx,
        visitor
    )

    private fun createInstrumentationConfig(
        instrumentOkHttp: Boolean = true,
        instrumentOnClick: Boolean = true,
        instrumentOnLongClick: Boolean = true,
        instrumentWebview: Boolean = true,
        instrumentAutoSdkInitialization: Boolean = true,
        instrumentFirebaseMessaging: Boolean = true,
        applicationInitTimingEnabled: Boolean = false,
    ): TestBytecodeInstrumentationParams {
        return TestBytecodeInstrumentationParams(
            instrumentFirebaseMessaging = instrumentFirebaseMessaging,
            instrumentWebview = instrumentWebview,
            instrumentAutoSdkInitialization = instrumentAutoSdkInitialization,
            instrumentOkHttp = instrumentOkHttp,
            instrumentOnLongClick = instrumentOnLongClick,
            instrumentOnClick = instrumentOnClick,
            applicationInitTimingEnabled = applicationInitTimingEnabled,
        )
    }

    private fun createMockClassContext(clzName: String): ClassContext {
        val ctx = mockk<ClassContext> {
            every { currentClassData } returns mockk {
                every { className } returns clzName
                every { superClasses } returns listOf(clzName)
            }
        }
        return ctx
    }
}
