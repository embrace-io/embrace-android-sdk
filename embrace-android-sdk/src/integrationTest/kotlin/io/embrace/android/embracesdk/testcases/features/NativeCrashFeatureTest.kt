package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.getSessionId
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbracePayloadAssertionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.actions.StoredNativeCrashData
import io.embrace.android.embracesdk.testframework.actions.createStoredNativeCrashData
import io.embrace.android.embracesdk.testframework.assertions.getLastLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test cases that confirm the JVM layer of native crash reporting behaves as expected. The test cases work
 * by writing empty files to the directory that contains native crash reports. A JniDelegate fake is supplied that
 * returns valid NativeCrashData objects.
 *
 * The C/C++ layer is covered by an instrumentation test that checks a struct can be written to disk then deserialized into JSON.
 * embrace-android-sdk/src/androidTest/java/io/embrace/android/embracesdk/ndk/serializer/FileWriterTestSuite.kt
 */
@RunWith(AndroidJUnit4::class)
internal class NativeCrashFeatureTest {

    private companion object {
        private const val BASE_TIME_MS = 1691000299000L
    }

    private val config = FakeInstrumentedConfig(enabledFeatures = FakeEnabledFeatureConfig(nativeCrashCapture = true))
    private val serializer = TestPlatformSerializer()
    private val sessionMetadata = StoredTelemetryMetadata(
        timestamp = BASE_TIME_MS,
        uuid = "30690ad1-6b87-4e08-b72c-7deca14451d8",
        processId = "8115ec91-3e5e-4d8a-816d-cc40306f9822",
        envelopeType = SupportedEnvelopeType.SESSION,
        complete = false,
        payloadType = PayloadType.SESSION,
    )
    private val sessionMetadata2 = StoredTelemetryMetadata(
        timestamp = BASE_TIME_MS + 10_000L,
        uuid = "aa690ad1-6b87-4e08-b72c-7deca14451d8",
        processId = "aa15ec91-3e5e-4d8a-816d-cc40306f9822",
        envelopeType = SupportedEnvelopeType.SESSION,
        complete = false,
        payloadType = PayloadType.SESSION,
    )
    private val crashMetadata = StoredTelemetryMetadata(
        timestamp = sessionMetadata.timestamp + 1_000L,
        uuid = "94c0e427-9faf-4dac-b1d5-2fd74039ef2d",
        processId = "f0652f96-8e76-4f68-8545-14f6229f01f8",
        envelopeType = SupportedEnvelopeType.CRASH,
        payloadType = PayloadType.NATIVE_CRASH,
    )
    private val crashMetadata2 = StoredTelemetryMetadata(
        timestamp = sessionMetadata2.timestamp + 1_000L,
        uuid = "bb690ad1-6b87-4e08-b72c-7deca14451d8",
        processId = "bb15ec91-3e5e-4d8a-816d-cc40306f9822",
        envelopeType = SupportedEnvelopeType.CRASH,
        payloadType = PayloadType.NATIVE_CRASH,
    )
    private val crashData = createStoredNativeCrashData(
        serializer,
        sessionMetadata,
        crashMetadata,
        "native_crash_1.txt",
    )
    private val crashData2 = createStoredNativeCrashData(
        serializer,
        sessionMetadata2,
        crashMetadata2,
        "native_crash_2.txt",
    )

    private lateinit var cacheStorageService: FakePayloadStorageService

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule {
        EmbraceSetupInterface().apply {
            (overriddenInitModule.logger as FakeEmbLogger).throwOnInternalError = false
        }
    }

    @Before
    fun setUp() {
        cacheStorageService = FakePayloadStorageService()
    }

    @Test
    fun `native crash with foreground session`() {
        testRule.runTest(
            instrumentedConfig = config,
            setupAction = {
                setupFakeDeadSession(cacheStorageService, crashData)
                setupFakeNativeCrash(serializer, crashData)
            },
            testCaseAction = {},
            assertAction = {
                with(getSingleSessionEnvelope()) {
                    assertDeadSessionResurrected(crashData)
                }
                val envelope = getSingleLogEnvelope()
                val log = envelope.getLastLog()
                assertNativeCrashSent(log, crashData, testRule.setup.symbols)
            }
        )
    }

    @Test
    fun `native crash with session ID but no matching session`() {
        testRule.runTest(
            instrumentedConfig = config,
            setupAction = {
                setupFakeNativeCrash(serializer, crashData)
            },
            testCaseAction = {},
            assertAction = {
                assertEquals(0, getSessionEnvelopes(0).size)
                val envelope = getSingleLogEnvelope()
                val log = envelope.getLastLog()
                assertNativeCrashSent(log, crashData, testRule.setup.symbols)
            }
        )
    }

    @Test
    fun `session with native crash ID but no matching crash`() {
        testRule.runTest(
            instrumentedConfig = config,
            setupAction = {
                setupFakeDeadSession(cacheStorageService, crashData)
            },
            testCaseAction = {},
            assertAction = {
                with(getSingleSessionEnvelope()) {
                    assertDeadSessionResurrected(null)
                }
                assertNoNativeCrashSent(crashData)
            }
        )
    }

    @Test
    fun `multiple native crashes can be associated with multiple sessions`() {
        testRule.runTest(
            instrumentedConfig = config,
            setupAction = {
                setupFakeDeadSession(cacheStorageService, crashData)
                setupFakeDeadSession(cacheStorageService, crashData2)
                setupFakeNativeCrash(serializer, crashData)
                setupFakeNativeCrash(serializer, crashData2)
            },
            testCaseAction = {},
            assertAction = {
                val sessionEnvelopes = getSessionEnvelopes(2)
                val logEnvelopes = getLogEnvelopes(2)

                // crashes sent
                val log1 = logEnvelopes.single { findMatchingSessionId(it, crashData) }.getLastLog()
                val log2 = logEnvelopes.single { findMatchingSessionId(it, crashData2) }.getLastLog()
                assertNativeCrashSent(log1, crashData, testRule.setup.symbols)
                assertNativeCrashSent(log2, crashData2, testRule.setup.symbols)

                // sessions updated to include crash IDs
                val session1 = sessionEnvelopes.single { it.getSessionId() == crashData.nativeCrash.sessionId }
                session1.assertDeadSessionResurrected(crashData)

                // sessions updated to include crash IDs
                val session2 = sessionEnvelopes.single { it.getSessionId() == crashData2.nativeCrash.sessionId }
                session2.assertDeadSessionResurrected(crashData2)
            }
        )
    }

    @Test
    fun `stored native crash not sent if ndk disabled`() {
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(),
            setupAction = {
                setupFakeDeadSession(cacheStorageService, crashData)
                setupFakeNativeCrash(serializer, crashData)
            },
            testCaseAction = {},
            assertAction = {
                with(getSingleSessionEnvelope()) {
                    assertDeadSessionResurrected(null)
                }
                assertEquals(0, getLogEnvelopes(0).size)
                assertTrue(crashData.getCrashFile().exists())
            }
        )
    }

    @Test
    fun `native crash that fails to load at JNI layer`() {
        testRule.runTest(
            instrumentedConfig = config,
            setupAction = {
                setupFakeDeadSession(cacheStorageService, crashData)
                setupFakeNativeCrash(serializer, crashData)

                // simulate JNI call failing to load struct
                jniDelegate.addCrashRaw(crashData.getCrashFile().absolutePath, null)
            },
            testCaseAction = {},
            assertAction = {
                with(getSingleSessionEnvelope()) {
                    assertDeadSessionResurrected(null)
                }
                assertNoNativeCrashSent(crashData)
            }
        )
    }

    @Test
    fun `native crash that fails to deserialize at JVM layer`() {
        testRule.runTest(
            instrumentedConfig = config,
            setupAction = {
                setupFakeDeadSession(cacheStorageService, crashData)
                setupFakeNativeCrash(serializer, crashData)

                // simulate bad JSON
                jniDelegate.addCrashRaw(crashData.getCrashFile().absolutePath, "{")
            },
            testCaseAction = {},
            assertAction = {
                with(getSingleSessionEnvelope()) {
                    assertDeadSessionResurrected(null)
                }
                assertNoNativeCrashSent(crashData)
            }
        )
    }

    private fun findMatchingSessionId(it: Envelope<LogPayload>, data: StoredNativeCrashData): Boolean {
        return it.getLastLog().attributes?.findAttributeValue("session.id") == data.nativeCrash.sessionId
    }

    private fun EmbracePayloadAssertionInterface.assertNoNativeCrashSent(
        crashData: StoredNativeCrashData,
    ) {
        assertEquals(0, getLogEnvelopes(0).size)
        assertFalse(crashData.getCrashFile().exists())
    }
}
