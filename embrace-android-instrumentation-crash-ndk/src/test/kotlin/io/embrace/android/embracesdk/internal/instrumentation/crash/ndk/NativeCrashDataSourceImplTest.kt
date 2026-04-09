package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.FakeNativeCrashProcessor
import io.embrace.android.embracesdk.internal.arch.attrs.toEmbraceAttributeName
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.NativeCrash.embNativeCrashException
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.NativeCrash.embNativeCrashSymbols
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.utils.toUTF8String
import io.embrace.android.embracesdk.semconv.EmbAndroidAttributes
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.semconv.SessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class NativeCrashDataSourceImplTest {

    private lateinit var crashProcessor: FakeNativeCrashProcessor
    private lateinit var args: FakeInstrumentationArgs
    private lateinit var nativeCrashDataSource: NativeCrashDataSourceImpl

    @Before
    fun setUp() {
        crashProcessor = FakeNativeCrashProcessor()
        args = FakeInstrumentationArgs(ApplicationProvider.getApplicationContext())
        nativeCrashDataSource = NativeCrashDataSourceImpl(
            nativeCrashProcessor = crashProcessor,
            args = args,
        )
    }

    @Test
    fun `native crash sent when there is one to be found`() {
        crashProcessor.addNativeCrashData(testNativeCrashData)
        assertNotNull(nativeCrashDataSource.getAndSendNativeCrash())
        assertEquals(1, args.destination.logEvents.size)
    }

    @Test
    fun `native crash sent with session properties and metadata`() {
        val sessionPropertyName = "prop".toEmbraceAttributeName()
        nativeCrashDataSource.sendNativeCrash(
            nativeCrash = testNativeCrashData,
            userSessionProperties = mapOf(sessionPropertyName to "value"),
            metadata = mapOf(EmbSessionAttributes.EMB_STATE to "background")
        )

        with(args.destination.logEvents.single()) {
            val attributes = schemaType.attributes()
            assertEquals(EmbType.System.NativeCrash, schemaType.telemetryType)
            assertEquals("value", attributes[sessionPropertyName])
            assertEquals("background", attributes[EmbSessionAttributes.EMB_STATE])
            assertEquals(
                testNativeCrashData.sessionId,
                attributes[SessionAttributes.SESSION_ID]
            )
            assertEquals("1", attributes[EmbAndroidAttributes.EMB_ANDROID_CRASH_NUMBER])
            assertEquals(testNativeCrashData.crash, attributes[embNativeCrashException])
            val json = args.serializer.toJson(testNativeCrashData.symbols, Map::class.java)
            assertEquals(
                json.toByteArray().toUTF8String(),
                attributes[embNativeCrashSymbols]
            )
        }
    }

    @Test
    fun `native crash sent without attributes that are null`() {
        nativeCrashDataSource.sendNativeCrash(
            nativeCrash = NativeCrashData(
                nativeCrashId = "nativeCrashId",
                sessionId = "null",
                timestamp = 1700000000000,
                crash = null,
                symbols = null,
            ),
            userSessionProperties = emptyMap(),
            metadata = emptyMap(),
        )

        with(args.destination.logEvents.single()) {
            val attributes = schemaType.attributes()
            assertEquals(EmbType.System.NativeCrash, schemaType.telemetryType)
            assertEquals("1", attributes[EmbAndroidAttributes.EMB_ANDROID_CRASH_NUMBER])
            assertNull(attributes[embNativeCrashException])
            assertNull(attributes[embNativeCrashSymbols])
        }
    }

    @Test
    fun `native crash sent without attributes that are blankish`() {
        nativeCrashDataSource.sendNativeCrash(
            nativeCrash = NativeCrashData(
                nativeCrashId = "nativeCrashId",
                sessionId = "",
                timestamp = 1700000000000,
                crash = "",
                symbols = emptyMap(),
            ),
            userSessionProperties = emptyMap(),
            metadata = emptyMap(),
        )

        with(args.destination.logEvents.single()) {
            val attributes = schemaType.attributes()
            assertEquals(EmbType.System.NativeCrash, schemaType.telemetryType)
            assertEquals("1", attributes[EmbAndroidAttributes.EMB_ANDROID_CRASH_NUMBER])
            assertNull(attributes[EmbSessionAttributes.EMB_STATE])
            assertNull(attributes[embNativeCrashException])
            assertNull(attributes[embNativeCrashSymbols])
        }
    }

    private val testNativeCrashData: NativeCrashData = NativeCrashData(
        nativeCrashId = "nativeCrashId",
        sessionId = "sessionId",
        timestamp = 1700000000000,
        crash = "base64binarystring",
        symbols = mapOf("key" to "value"),
    )
}
