package io.embrace.android.embracesdk.testcases.features

import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.getLastLog
import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.fakeEnvelopeMetadata
import io.embrace.android.embracesdk.fakes.fakeEnvelopeResource
import io.embrace.android.embracesdk.fixtures.fakeCachedSessionStoredTelemetryMetadata
import io.embrace.android.embracesdk.fixtures.fakeNativeCrashStoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.actions.createStoredNativeCrashData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [TIRAMISU])
@RunWith(AndroidJUnit4::class)
internal class ResurrectionFeatureTest {

    private val serializer = TestPlatformSerializer()
    private val fakeSymbols = mapOf("libfoo.so" to "symbol_content")
    private val symbols = createNativeSymbolsForCurrentArch(fakeSymbols)
    private lateinit var cacheStorageService: FakePayloadStorageService

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule {
        EmbraceSetupInterface(
            fakeStorageLayer = true,
        ).apply {
            getEmbLogger().throwOnInternalError = false
        }.also {
            cacheStorageService = checkNotNull(it.fakeCacheStorageService)
        }
    }

    @Test
    fun `crashed session and native crash resurrected and sent properly`() {
        val crashData = createStoredNativeCrashData(
            serializer = serializer,
            resourceFixtureName = "native_crash_1.txt",
            crashMetadata = fakeNativeCrashStoredTelemetryMetadata,
            sessionMetadata = fakeCachedSessionStoredTelemetryMetadata,
        )
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(nativeCrashCapture = true),
                symbols = symbols
            ),
            setupAction = {
                setupCachedDataFromNativeCrash(crashData = crashData)
                setupFakeNativeCrash(serializer, crashData)
            },
            testCaseAction = {},
            assertAction = {
                with(getSingleSessionEnvelope()) {
                    assertDeadSessionResurrected(crashData)
                }
                val envelope = getSingleLogEnvelope()
                with(envelope) {
                    assertEquals(fakeEnvelopeResource, resource)
                    assertEquals(fakeEnvelopeMetadata, metadata)
                }

                val log = envelope.getLastLog()
                assertNativeCrashSent(log, crashData, fakeSymbols)
            }
        )
    }

    @Test
    fun `native crash without session resurrected and sent properly`() {
        val crashData = createStoredNativeCrashData(
            serializer = serializer,
            resourceFixtureName = "native_crash_1.txt",
            crashMetadata = fakeNativeCrashStoredTelemetryMetadata,
        )
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(nativeCrashCapture = true),
                symbols = symbols
            ),
            setupAction = {
                setupCachedDataFromNativeCrash(crashData = crashData)
                setupFakeNativeCrash(serializer, crashData)
            },
            testCaseAction = {},
            assertAction = {
                val envelope = getSingleLogEnvelope()
                with(envelope) {
                    assertEquals(fakeEnvelopeResource, resource)
                    assertEquals(fakeEnvelopeMetadata, metadata)
                }

                val log = envelope.getLastLog()
                assertNativeCrashSent(log, crashData, fakeSymbols)
            }
        )
    }


    @Ignore("Flakey because the internal errors sometimes comes before the crash")
    @Test
    fun `native crash without session or crash envelope is sent with current SDK envelope`() {
        val crashData = createStoredNativeCrashData(
            serializer = serializer,
            resourceFixtureName = "native_crash_1.txt",
            crashMetadata = fakeNativeCrashStoredTelemetryMetadata,
            createCrashEnvelope = false,
        )
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    bgActivityCapture = false,
                    nativeCrashCapture = true
                ),
                symbols = symbols
            ),
            setupAction = {
                setupCachedDataFromNativeCrash(crashData = crashData)
                setupFakeNativeCrash(serializer, crashData)
            },
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val session = getSingleSessionEnvelope()
                val crashEnvelope = getSingleLogEnvelope()
                with(crashEnvelope) {
                    assertEquals(session.resource, resource)
                    assertEquals(session.metadata, metadata)
                    val crash = getLastLog()
                    assertNativeCrashSent(crash, crashData, fakeSymbols)
                }
            }
        )
    }

    @Test
    fun `session with native crash ID but no matching crash sent properly`() {
        val crashData = createStoredNativeCrashData(
            serializer = serializer,
            resourceFixtureName = "native_crash_1.txt",
            crashMetadata = fakeNativeCrashStoredTelemetryMetadata,
            sessionMetadata = fakeCachedSessionStoredTelemetryMetadata,
        )
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(nativeCrashCapture = true),
                symbols = symbols
            ),
            setupAction = {
                setupCachedDataFromNativeCrash(crashData = crashData)
            },
            testCaseAction = {},
            assertAction = {
                with(getSingleSessionEnvelope()) {
                    assertDeadSessionResurrected(null)
                }
                assertEquals(0, getLogEnvelopes(0).size)
            }
        )
    }

    @Test
    fun `empty crash envelope not available for native crash resurrection if background activity is enabled`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                backgroundActivityConfig = BackgroundActivityRemoteConfig(100f)
            ),
            testCaseAction = {
                recordSession()
                recordSession()
            },
            assertAction = {
                getSessionEnvelopes(2)
                assertTrue(cacheStorageService.getCachedCrashEnvelope().isEmpty())
            }
        )
    }

    @Test
    fun `one empty crash envelope available for native crash resurrection if background activity is not enabled`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                backgroundActivityConfig = BackgroundActivityRemoteConfig(0f)
            ),
            testCaseAction = {
                recordSession()
                recordSession()
                recordSession()
            },
            assertAction = {
                getSessionEnvelopes(3)
                with(cacheStorageService.getCachedCrashEnvelope().single()) {
                    assertEquals(SupportedEnvelopeType.CRASH, envelopeType)
                    assertEquals(PayloadType.UNKNOWN, payloadType)
                    assertFalse(complete)
                }

            }
        )
    }

    private fun FakePayloadStorageService.getCachedCrashEnvelope(): List<StoredTelemetryMetadata> {
        return storedPayloadMetadata().filter { !it.complete && it.envelopeType == SupportedEnvelopeType.CRASH }
    }
}
