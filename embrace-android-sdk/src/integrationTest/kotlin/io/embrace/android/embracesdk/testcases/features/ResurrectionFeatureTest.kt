package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.fakeEnvelopeMetadata
import io.embrace.android.embracesdk.fakes.fakeEnvelopeResource
import io.embrace.android.embracesdk.fixtures.fakeCachedSessionStoredTelemetryMetadata
import io.embrace.android.embracesdk.fixtures.fakeNativeCrashStoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.KillSwitchRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.actions.createStoredNativeCrashData
import io.embrace.android.embracesdk.testframework.assertions.getLastLog
import io.opentelemetry.semconv.ExceptionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ResurrectionFeatureTest {

    private val serializer = TestPlatformSerializer()
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
        cacheStorageService =
            FakePayloadStorageService(
                processIdProvider = {
                    testRule.setup.overriddenOpenTelemetryModule.openTelemetryConfiguration.processIdentifier
                }
            )
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
            instrumentedConfig = FakeInstrumentedConfig(enabledFeatures = FakeEnabledFeatureConfig(nativeCrashCapture = true)),
            setupAction = {
                setupCachedDataFromNativeCrash(cacheStorageService, crashData = crashData)
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
                assertNativeCrashSent(log, crashData, testRule.setup.symbols)
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
            instrumentedConfig = FakeInstrumentedConfig(enabledFeatures = FakeEnabledFeatureConfig(nativeCrashCapture = true)),
            setupAction = {
                setupCachedDataFromNativeCrash(cacheStorageService, crashData = crashData)
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
                assertNativeCrashSent(log, crashData, testRule.setup.symbols)
            }
        )
    }


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
                )
            ),
            setupAction = {
                setupCachedDataFromNativeCrash(cacheStorageService, crashData = crashData)
                setupFakeNativeCrash(serializer, crashData)
            },
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val session = getSingleSessionEnvelope()
                val envelopes = getLogEnvelopes(2).sortedBy { it.data.logs?.size }
                with(envelopes.first()) {
                    assertEquals(session.resource, resource)
                    assertEquals(session.metadata, metadata)
                    val crash = getLastLog()
                    assertNativeCrashSent(crash, crashData, testRule.setup.symbols)
                }

                with(envelopes.last()) {
                    val errors = checkNotNull(data.logs)
                    assertEquals(2, errors.size)
                    with(errors.first()) {
                        assertEquals(
                            "Cached native crash envelope data not found",
                            attributes?.findAttributeValue(ExceptionAttributes.EXCEPTION_MESSAGE.key)
                        )
                    }

                    with(errors.last()) {
                        assertEquals(
                            "java.io.FileNotFoundException",
                            attributes?.findAttributeValue(ExceptionAttributes.EXCEPTION_TYPE.key)
                        )
                    }
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
            instrumentedConfig = FakeInstrumentedConfig(enabledFeatures = FakeEnabledFeatureConfig(nativeCrashCapture = true)),
            setupAction = {
                setupCachedDataFromNativeCrash(cacheStorageService, crashData = crashData)
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
            setupAction = {
                cacheStorageServiceProvider = { cacheStorageService }
            },
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
            setupAction = {
                cacheStorageServiceProvider = { cacheStorageService }
            },
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

    @Test
    fun `resurrection attempt with v2 delivery layer off does not crash the SDK`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(killSwitchConfig = KillSwitchRemoteConfig(v2StoragePct = 0f)),
            setupAction = {
                useMockWebServer = false
            },
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                assertNotNull(getSessionEnvelopesV1(1).single())
            }
        )
    }

    private fun FakePayloadStorageService.getCachedCrashEnvelope(): List<StoredTelemetryMetadata> {
        return storedPayloadMetadata().filter { !it.complete && it.envelopeType == SupportedEnvelopeType.CRASH }
    }
}
