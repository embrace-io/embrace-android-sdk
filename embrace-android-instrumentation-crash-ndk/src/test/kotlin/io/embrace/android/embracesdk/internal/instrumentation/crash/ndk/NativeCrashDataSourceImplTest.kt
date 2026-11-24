package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.FakeNativeCrashHandlerInstaller
import io.embrace.android.embracesdk.fakes.FakeNativeCrashProcessor
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.internal.arch.attrs.toEmbraceAttributeName
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.NativeCrash.embNativeCrashException
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.NativeCrash.embNativeCrashSymbols
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.utils.toUTF8String
import io.embrace.android.embracesdk.semconv.EmbAndroidAttributes
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class NativeCrashDataSourceImplTest {

    private lateinit var crashProcessor: FakeNativeCrashProcessor
    private lateinit var args: FakeInstrumentationArgs
    private lateinit var nativeCrashDataSource: NativeCrashDataSourceImpl
    private lateinit var installer: FakeNativeCrashHandlerInstaller

    @Before
    fun setUp() {
        crashProcessor = FakeNativeCrashProcessor()
        args = FakeInstrumentationArgs()
        installer = FakeNativeCrashHandlerInstaller()
        nativeCrashDataSource = NativeCrashDataSourceImpl(
            nativeCrashProcessor = crashProcessor,
            nativeCrashHandlerInstaller = installer,
            args = args,
        )
    }

    @Test
    fun `disabling data capture disables the native crash handler`() {
        assertFalse(installer.disabled)
        nativeCrashDataSource.onDataCaptureDisabled()
        assertTrue(installer.disabled)
    }

    @Test
    fun `native crash sent with session properties and metadata`() {
        val sessionPropertyName = "prop".toEmbraceAttributeName()
        crashProcessor.addNativeCrashData(testNativeCrashData)
        nativeCrashDataSource.sendNativeCrash(
            nativeCrash = testNativeCrashData,
            userSessionProperties = mapOf(sessionPropertyName to "value"),
            metadata = mapOf(EmbSessionAttributes.EMB_STATE to "background"),
        )

        with(args.destination.logEvents.single()) {
            val attributes = schemaType.attributes()
            assertEquals(EmbType.System.NativeCrash, schemaType.telemetryType)
            assertEquals("value", attributes[sessionPropertyName])
            assertEquals("background", attributes[EmbSessionAttributes.EMB_STATE])
            assertEquals(
                testNativeCrashData.sessionPartId,
                attributes[EmbSessionAttributes.EMB_SESSION_PART_ID],
            )
            assertEquals(
                testNativeCrashData.userSessionId,
                attributes[EmbSessionAttributes.EMB_USER_SESSION_ID],
            )
            assertEquals("1", attributes[EmbAndroidAttributes.EMB_ANDROID_CRASH_NUMBER])
            assertEquals(testNativeCrashData.crash, attributes[embNativeCrashException])
            val json = args.serializer.toJson(
                testNativeCrashData.symbols,
                MapSerializer(String.serializer(), String.serializer()).nullable,
            )
            assertEquals(
                json.toByteArray().toUTF8String(),
                attributes[embNativeCrashSymbols],
            )
        }
    }

    @Test
    fun `native crash sent without attributes that are null`() {
        val crash = NativeCrashData(
            nativeCrashId = "nativeCrashId",
            sessionPartId = "",
            userSessionId = "",
            timestamp = 1700000000000,
            crash = null,
            symbols = null,
        )
        crashProcessor.addNativeCrashData(crash)
        nativeCrashDataSource.sendNativeCrash(
            nativeCrash = crash,
            userSessionProperties = emptyMap(),
            metadata = emptyMap(),
        )

        with(args.destination.logEvents.single()) {
            val attributes = schemaType.attributes()
            assertEquals(EmbType.System.NativeCrash, schemaType.telemetryType)
            assertEquals("1", attributes[EmbAndroidAttributes.EMB_ANDROID_CRASH_NUMBER])
            assertEquals("", attributes[EmbSessionAttributes.EMB_SESSION_PART_ID])
            assertEquals("", attributes[EmbSessionAttributes.EMB_USER_SESSION_ID])
            assertNull(attributes[embNativeCrashException])
            assertNull(attributes[embNativeCrashSymbols])
        }
    }

    @Test
    fun `native crash sent without attributes that are blankish`() {
        val crash = NativeCrashData(
            nativeCrashId = "nativeCrashId",
            sessionPartId = "",
            userSessionId = "",
            timestamp = 1700000000000,
            crash = "",
            symbols = emptyMap(),
        )
        crashProcessor.addNativeCrashData(crash)
        nativeCrashDataSource.sendNativeCrash(
            nativeCrash = crash,
            userSessionProperties = emptyMap(),
            metadata = emptyMap(),
        )

        with(args.destination.logEvents.single()) {
            val attributes = schemaType.attributes()
            assertEquals(EmbType.System.NativeCrash, schemaType.telemetryType)
            assertEquals("1", attributes[EmbAndroidAttributes.EMB_ANDROID_CRASH_NUMBER])
            assertEquals("", attributes[EmbSessionAttributes.EMB_SESSION_PART_ID])
            assertEquals("", attributes[EmbSessionAttributes.EMB_USER_SESSION_ID])
            assertNull(attributes[EmbSessionAttributes.EMB_STATE])
            assertNull(attributes[embNativeCrashException])
            assertNull(attributes[embNativeCrashSymbols])
        }
    }

    @Test
    fun `symbols shared between native crashes are serialized once`() {
        var symbolSerializations = 0
        val delegate = TestPlatformSerializer()
        args = FakeInstrumentationArgs(
            serializer = object : PlatformSerializer by delegate {
                override fun <T> toJson(value: T, serializer: SerializationStrategy<T>): String {
                    if (value === testNativeCrashData.symbols) {
                        symbolSerializations++
                    }
                    return delegate.toJson(value, serializer)
                }
            },
        )
        nativeCrashDataSource = NativeCrashDataSourceImpl(crashProcessor, installer, args)

        listOf("first", "second").forEach { id ->
            crashProcessor.addNativeCrashData(testNativeCrashData.copy(nativeCrashId = id))
            nativeCrashDataSource.sendNativeCrash(
                nativeCrash = testNativeCrashData.copy(nativeCrashId = id, crash = null, symbols = null),
                userSessionProperties = emptyMap(),
                metadata = emptyMap(),
            )
        }

        assertEquals(1, symbolSerializations)
        val symbols = args.destination.logEvents.map { it.schemaType.attributes()[embNativeCrashSymbols] }
        assertEquals(listOf("{\"key\":\"value\"}", "{\"key\":\"value\"}"), symbols)
    }

    @Test
    fun `native crash is sent with the report and symbols loaded by the processor`() {
        crashProcessor.addNativeCrashData(testNativeCrashData)

        nativeCrashDataSource.sendNativeCrash(
            nativeCrash = testNativeCrashData.copy(crash = null, symbols = null),
            userSessionProperties = emptyMap(),
            metadata = emptyMap(),
        )

        val attributes = args.destination.logEvents.single().schemaType.attributes()
        assertEquals(testNativeCrashData.crash, attributes[embNativeCrashException])
        assertEquals("{\"key\":\"value\"}", attributes[embNativeCrashSymbols])
    }

    @Test
    fun `a deleted native crash is not sent again`() {
        crashProcessor.addNativeCrashData(testNativeCrashData)
        nativeCrashDataSource.deleteNativeCrash(testNativeCrashData)

        nativeCrashDataSource.sendNativeCrash(
            nativeCrash = testNativeCrashData.copy(crash = null, symbols = null),
            userSessionProperties = emptyMap(),
            metadata = emptyMap(),
        )

        assertTrue(args.destination.logEvents.isEmpty())
    }

    private val testNativeCrashData: NativeCrashData = NativeCrashData(
        nativeCrashId = "nativeCrashId",
        sessionPartId = "sessionId",
        userSessionId = "userSessionId",
        timestamp = 1700000000000,
        crash = "base64binarystring",
        symbols = mapOf("key" to "value"),
    )
}
