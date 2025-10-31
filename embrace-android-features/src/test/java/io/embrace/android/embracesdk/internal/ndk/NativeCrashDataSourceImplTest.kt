package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.fakes.FakeInstrumentationInstallArgs
import io.embrace.android.embracesdk.fakes.FakeNativeCrashProcessor
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fixtures.testNativeCrashData
import io.embrace.android.embracesdk.internal.arch.attrs.embCrashNumber
import io.embrace.android.embracesdk.internal.arch.attrs.embState
import io.embrace.android.embracesdk.internal.arch.attrs.toEmbraceAttributeName
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.NativeCrash.embNativeCrashException
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.NativeCrash.embNativeCrashSymbols
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.utils.toUTF8String
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.semconv.SessionAttributes
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalApi::class, IncubatingApi::class)
internal class NativeCrashDataSourceImplTest {

    private lateinit var crashProcessor: FakeNativeCrashProcessor
    private lateinit var serializer: EmbraceSerializer
    private lateinit var args: FakeInstrumentationInstallArgs
    private lateinit var nativeCrashDataSource: NativeCrashDataSourceImpl

    @Before
    fun setUp() {
        crashProcessor = FakeNativeCrashProcessor()
        val preferencesService = FakePreferenceService()
        args = FakeInstrumentationInstallArgs(mockk())
        serializer = EmbraceSerializer()
        nativeCrashDataSource = NativeCrashDataSourceImpl(
            nativeCrashProcessor = crashProcessor,
            preferencesService = preferencesService,
            args = args,
            serializer = serializer,
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
            assertEquals(
                serializer.toJson(testNativeCrashData.symbols, Map::class.java).toByteArray().toUTF8String(),
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
}
