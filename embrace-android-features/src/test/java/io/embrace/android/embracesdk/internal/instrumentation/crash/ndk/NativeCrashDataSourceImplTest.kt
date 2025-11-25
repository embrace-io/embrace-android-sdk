package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.FakeNativeCrashProcessor
import io.embrace.android.embracesdk.internal.arch.attrs.embCrashNumber
import io.embrace.android.embracesdk.internal.arch.attrs.embState
import io.embrace.android.embracesdk.internal.arch.attrs.toEmbraceAttributeName
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.NativeCrash.embNativeCrashException
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.NativeCrash.embNativeCrashSymbols
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.utils.toUTF8String
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.semconv.SessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalApi::class, IncubatingApi::class)
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
        nativeCrashDataSource.sendNativeCrash(
            nativeCrash = testNativeCrashData,
            sessionProperties = mapOf("prop" to "value"),
            metadata = mapOf(embState.name to "background")
        )

        with(args.destination.logEvents.single()) {
            val attributes = schemaType.attributes()
            assertEquals(EmbType.System.NativeCrash, schemaType.telemetryType)
            assertEquals("value", attributes["prop".toEmbraceAttributeName()])
            assertEquals("background", attributes[embState.name])
            assertEquals(
                testNativeCrashData.sessionId,
                attributes[SessionAttributes.SESSION_ID]
            )
            assertEquals("1", attributes[embCrashNumber.name])
            assertEquals(testNativeCrashData.crash, attributes[embNativeCrashException.name])
            val json = args.serializer.toJson(testNativeCrashData.symbols, Map::class.java)
            assertEquals(
                json.toByteArray().toUTF8String(),
                attributes[embNativeCrashSymbols.name]
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
            sessionProperties = emptyMap(),
            metadata = emptyMap(),
        )

        with(args.destination.logEvents.single()) {
            val attributes = schemaType.attributes()
            assertEquals(EmbType.System.NativeCrash, schemaType.telemetryType)
            assertEquals("1", attributes[embCrashNumber.name])
            assertNull(attributes[embNativeCrashException.name])
            assertNull(attributes[embNativeCrashSymbols.name])
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
            sessionProperties = emptyMap(),
            metadata = emptyMap(),
        )

        with(args.destination.logEvents.single()) {
            val attributes = schemaType.attributes()
            assertEquals(EmbType.System.NativeCrash, schemaType.telemetryType)
            assertEquals("1", attributes[embCrashNumber.name])
            assertNull(attributes[embState.name])
            assertNull(attributes[embNativeCrashException.name])
            assertNull(attributes[embNativeCrashSymbols.name])
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
