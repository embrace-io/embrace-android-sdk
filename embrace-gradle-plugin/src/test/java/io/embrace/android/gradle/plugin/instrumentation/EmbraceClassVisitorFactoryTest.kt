package io.embrace.android.gradle.plugin.instrumentation

import com.android.build.api.instrumentation.ClassContext
import io.embrace.android.gradle.plugin.instrumentation.config.visitor.ConfigInstrumentationClassVisitor
import io.embrace.android.gradle.plugin.instrumentation.fakes.TestBytecodeInstrumentationParams
import io.embrace.android.gradle.plugin.instrumentation.fakes.TestClassContext
import io.embrace.android.gradle.plugin.instrumentation.fakes.TestClassData
import io.embrace.android.gradle.plugin.instrumentation.fakes.TestClassVisitor
import io.embrace.android.gradle.plugin.instrumentation.fakes.TestVisitorFactoryImpl
import io.embrace.android.gradle.plugin.instrumentation.visitor.FirebaseMessagingServiceClassAdapter
import io.embrace.android.gradle.plugin.instrumentation.visitor.OkHttpClassAdapter
import io.embrace.android.gradle.plugin.instrumentation.visitor.OnClickClassAdapter
import io.embrace.android.gradle.plugin.instrumentation.visitor.OnLongClickClassAdapter
import io.embrace.android.gradle.plugin.instrumentation.visitor.WebViewClientClassAdapter
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbraceClassVisitorFactoryTest {

    private val clzDataString = TestClassData(checkNotNull(String::class.qualifiedName))
    private val clzDataBool = TestClassData(checkNotNull(Boolean::class.qualifiedName))

    /**
     * Verifies that the OnClick visitor should be returned if there is nothing to instrument,
     * and that it should chain the original visitor.
     */
    @Test
    fun testDefaultClassVisitorReturned() {
        val visitor = TestClassVisitor()
        val ctx = TestClassContext(clzDataString)
        val returningVisitor = TestVisitorFactoryImpl().createClassVisitor(ctx, visitor)
        check(returningVisitor is OnClickClassAdapter)
        check(returningVisitor.nextClassVisitor is OnLongClickClassAdapter)
        assertSame(visitor, returningVisitor.nextClassVisitor.nextClassVisitor)
    }

    @Test
    fun testWebViewClassVisitorReturned() {
        val visitor = TestClassVisitor()
        val ctx = createMockClassContext("android.webkit.WebViewClient")
        val returningVisitor = TestVisitorFactoryImpl().createClassVisitor(ctx, visitor)
        check(returningVisitor is OnClickClassAdapter)
        check(returningVisitor.nextClassVisitor is OnLongClickClassAdapter)
        check(returningVisitor.nextClassVisitor.nextClassVisitor is WebViewClientClassAdapter)
        assertSame(visitor, returningVisitor.nextClassVisitor.nextClassVisitor.nextClassVisitor)
    }

    @Test
    fun testConfigClassVisitorReturned() {
        val visitor = TestClassVisitor()
        val ctx =
            createMockClassContext(
                "io.embrace.android.embracesdk.internal.config.instrumented.EnabledFeatureConfigImpl"
            )
        val returningVisitor = TestVisitorFactoryImpl().createClassVisitor(ctx, visitor)
        check(returningVisitor is OnClickClassAdapter)
        check(returningVisitor.nextClassVisitor is OnLongClickClassAdapter)
        check(returningVisitor.nextClassVisitor.nextClassVisitor is ConfigInstrumentationClassVisitor)
    }

    @Test
    fun testOkHttpClassVisitorReturned() {
        val visitor = TestClassVisitor()
        val ctx = createMockClassContext("okhttp3.OkHttpClient\$Builder")
        val returningVisitor = TestVisitorFactoryImpl().createClassVisitor(ctx, visitor)
        check(returningVisitor is OnClickClassAdapter)
        check(returningVisitor.nextClassVisitor is OnLongClickClassAdapter)
        check(returningVisitor.nextClassVisitor.nextClassVisitor is OkHttpClassAdapter)
        assertSame(visitor, returningVisitor.nextClassVisitor.nextClassVisitor.nextClassVisitor)
    }

    @Test
    fun testFCMClassVisitorReturned() {
        val visitor = TestClassVisitor()
        val ctx = createMockClassContext("com.google.firebase.messaging.FirebaseMessagingService")
        val params = TestBytecodeInstrumentationParams(instrumentFirebaseMessaging = true)
        val returningVisitor = TestVisitorFactoryImpl(params = params).createClassVisitor(ctx, visitor)
        check(returningVisitor is OnClickClassAdapter)
        check(returningVisitor.nextClassVisitor is OnLongClickClassAdapter)
        check(returningVisitor.nextClassVisitor.nextClassVisitor is FirebaseMessagingServiceClassAdapter)
        assertSame(visitor, returningVisitor.nextClassVisitor.nextClassVisitor.nextClassVisitor)
    }

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
        val params = TestBytecodeInstrumentationParams(disabled = false, classInstrumentationFilter = filter)
        val factory = TestVisitorFactoryImpl(params = params)
        assertTrue(factory.isInstrumentable(clzDataString))
        assertFalse(factory.isInstrumentable(clzDataBool))
    }

    @Test
    fun testOnClickVisitorDisabled() {
        val visitor = TestClassVisitor()
        val ctx = TestClassContext(clzDataString)
        val config = createInstrumentationConfig(instrumentOnClick = false)
        val observed = fetchClassVisitor(config, ctx, visitor)
        assertTrue(observed is OnLongClickClassAdapter)
    }

    @Test
    fun testOnLongClickVisitorDisabled() {
        val visitor = TestClassVisitor()
        val ctx = TestClassContext(clzDataString)
        val config = createInstrumentationConfig(instrumentOnLongClick = false)
        val observed = fetchClassVisitor(config, ctx, visitor)
        check(observed is OnClickClassAdapter)
        check(observed.nextClassVisitor is TestClassVisitor)
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
        instrumentFirebaseMessaging: Boolean = true
    ): TestBytecodeInstrumentationParams {
        return TestBytecodeInstrumentationParams(
            instrumentOkHttp = instrumentOkHttp,
            instrumentOnClick = instrumentOnClick,
            instrumentOnLongClick = instrumentOnLongClick,
            instrumentWebview = instrumentWebview,
            instrumentFirebaseMessaging = instrumentFirebaseMessaging
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
