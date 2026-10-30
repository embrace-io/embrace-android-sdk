package io.embrace.bytecode

import io.embrace.bytecode.config.model.VariantConfig
import io.embrace.bytecode.config.model.VariantOutputInfo
import io.embrace.bytecode.fakes.TestClassVisitor
import io.embrace.bytecode.strategy.ClassInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BytecodeInstrumenterTest {

    private val allDisabled = InstrumentationFeatureFlags(
        shouldInstrumentFirebaseMessaging = false,
        shouldInstrumentWebview = false,
        shouldInstrumentAutoSdkInitialization = false,
        shouldInstrumentOkHttp = false,
        shouldInstrumentOnLongClick = false,
        shouldInstrumentOnClick = false,
        applicationInitTimingEnabled = false,
    )
    private var fileReads = 0

    @Test
    fun testEnabled() {
        val instrumenter = createInstrumenter()
        assertTrue(instrumenter.isInstrumentable("kotlin.String"))
        assertTrue(instrumenter.isInstrumentable("kotlin.Boolean"))
    }

    @Test
    fun testDisabled() {
        val instrumenter = createInstrumenter(disabled = true)
        assertFalse(instrumenter.isInstrumentable("kotlin.String"))
        assertFalse(instrumenter.isInstrumentable("kotlin.Boolean"))
    }

    @Test
    fun testClassFiltering() {
        val instrumenter = createInstrumenter(filter = ClassInstrumentationFilter(listOf("kotlin.Boolean")))
        assertTrue(instrumenter.isInstrumentable("kotlin.String"))
        assertFalse(instrumenter.isInstrumentable("kotlin.Boolean"))
    }

    @Test
    fun testConfigClassVisitor() {
        val visitor = TestClassVisitor()
        val observed = fetchClassVisitor(
            createInstrumenter(),
            "io.embrace.android.embracesdk.internal.config.instrumented.Base64SharedObjectFilesMapImpl",
            visitor,
        )
        assertNotSame(visitor, observed)
        assertEquals(2, fileReads)
    }

    @Test
    fun testNonConfigClassSkipsFileReads() {
        val visitor = TestClassVisitor()
        val observed = fetchClassVisitor(createInstrumenter(), "kotlin.String", visitor)
        assertSame(visitor, observed)
        assertEquals(0, fileReads)
    }

    @Test
    fun testFeatureEnabled() {
        val visitor = TestClassVisitor()
        val instrumenter = createInstrumenter(allDisabled.copy(shouldInstrumentOkHttp = true))
        assertNotSame(visitor, fetchClassVisitor(instrumenter, "okhttp3.OkHttpClient\$Builder", visitor))
    }

    @Test
    fun testFeaturesDisabled() {
        // on_click and on_long_click visit every class, so they stay off.
        val allEnabled = InstrumentationFeatureFlags(
            shouldInstrumentFirebaseMessaging = true,
            shouldInstrumentWebview = true,
            shouldInstrumentAutoSdkInitialization = true,
            shouldInstrumentOkHttp = true,
            shouldInstrumentOnLongClick = false,
            shouldInstrumentOnClick = false,
            applicationInitTimingEnabled = true,
        )
        mapOf(
            "android.webkit.WebViewClient" to allEnabled.copy(shouldInstrumentWebview = false),
            "android.app.Application" to allEnabled.copy(
                shouldInstrumentAutoSdkInitialization = false,
                applicationInitTimingEnabled = false,
            ),
            "okhttp3.OkHttpClient\$Builder" to allEnabled.copy(shouldInstrumentOkHttp = false),
            "com.google.firebase.messaging.FirebaseMessagingService" to allEnabled.copy(
                shouldInstrumentFirebaseMessaging = false,
            ),
        ).forEach { (className, flags) ->
            val visitor = TestClassVisitor()
            assertSame(visitor, fetchClassVisitor(createInstrumenter(flags), className, visitor))
        }
    }

    private fun fetchClassVisitor(
        instrumenter: BytecodeInstrumenter,
        className: String,
        visitor: TestClassVisitor,
    ) = instrumenter.createClassVisitor(ClassInfo(className, listOf(className)), ASM_API_VERSION, visitor)

    private fun createInstrumenter(
        flags: InstrumentationFeatureFlags = allDisabled,
        filter: ClassInstrumentationFilter = ClassInstrumentationFilter(emptyList()),
        disabled: Boolean = false,
    ) = BytecodeInstrumenter(
        config = VariantConfig("", "", null, null, null),
        variantOutputInfo = VariantOutputInfo("", "", ""),
        encodedSharedObjectFilesMap = ::readFile,
        reactNativeBundleId = ::readFile,
        flags = flags,
        filter = filter,
        disabled = disabled,
    )

    private fun readFile(): String? {
        fileReads++
        return null
    }
}
