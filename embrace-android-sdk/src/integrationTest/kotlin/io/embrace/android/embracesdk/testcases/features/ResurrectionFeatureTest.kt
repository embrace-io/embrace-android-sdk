package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.getLastLog
import io.embrace.android.embracesdk.assertions.getSessionPartId
import io.embrace.android.embracesdk.assertions.getUserSessionId
import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.fakeEnvelopeMetadata
import io.embrace.android.embracesdk.fakes.fakeEnvelopeResource
import io.embrace.android.embracesdk.fixtures.fakeCachedSessionStoredTelemetryMetadata
import io.embrace.android.embracesdk.fixtures.fakeNativeCrashStoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.getSessionPartSpan
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.semconv.EmbSpanAttributes
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.actions.StoredNativeCrashData
import io.embrace.android.embracesdk.testframework.actions.createStoredNativeCrashData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

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
                symbols = symbols,
            ),
            setupAction = {
                setupCachedDataFromNativeCrash(crashData = crashData)
                setupFakeNativeCrash(serializer, crashData)
            },
            testCaseAction = {},
            assertAction = {
                with(getSingleSessionEnvelope()) {
                    assertDeadPartResurrected(crashData)
                }
                val envelope = getSingleLogEnvelope()
                with(envelope) {
                    assertEquals(fakeEnvelopeResource, resource)
                    assertEquals(fakeEnvelopeMetadata, metadata)
                }
                val log = envelope.getLastLog()
                assertNativeCrashSent(log, crashData, fakeSymbols)
            },
        )
    }

    @Test
    fun `crashed multi-file session part with no native crash resurrected and sent properly`() {
        var filesDir: File? = null
        var sessionPartDir: SessionPartDirectory? = null

        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(pctMultiFilePersistenceEnabled = 100f),
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(nativeCrashCapture = true),
                symbols = symbols,
            ),
            testCaseAction = {
                filesDir = testRule.bootstrapper.coreModule.context.filesDir
                recordSession(isBackgroundActivityEnabled = true, endInBackground = false)
                shutdownSdkWithIncompleteSession()
            },
            assertAction = {
                sessionPartDir = obtainSessionPartDirectory(filesDir)
            },
        )
        val dir = checkNotNull(sessionPartDir)

        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(pctMultiFilePersistenceEnabled = 100f),
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(nativeCrashCapture = true),
                symbols = symbols,
            ),
            testCaseAction = {},
            assertAction = {
                // session envelope was delivered
                val envelopes = getSessionEnvelopes(1, ProcessState.FOREGROUND)
                val sessionEnvelope = envelopes.first()
                val sessionPartSpan = checkNotNull(sessionEnvelope.getSessionPartSpan())
                assertEquals(dir.userSessionId, sessionEnvelope.getUserSessionId())
                assertEquals(dir.sessionPartId, sessionEnvelope.getSessionPartId())

                // span snapshots were converted to failed spans
                assertSpanSnapshotConversion(sessionEnvelope, sessionPartSpan)

                // assert crash wasn't delivered
                getLogEnvelopes(0)
            },
        )
    }

    @Test
    fun `crashed multi-file session part and native crash resurrected and sent properly`() {
        var filesDir: File? = null
        var sessionPartDir: SessionPartDirectory? = null

        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(pctMultiFilePersistenceEnabled = 100f),
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(nativeCrashCapture = true),
                symbols = symbols,
            ),
            testCaseAction = {
                filesDir = testRule.bootstrapper.coreModule.context.filesDir
                recordSession(isBackgroundActivityEnabled = true, endInBackground = false)
                shutdownSdkWithIncompleteSession()
            },
            assertAction = {
                sessionPartDir = obtainSessionPartDirectory(filesDir)
            },
        )

        val dir = checkNotNull(sessionPartDir)
        val updatedCrashData = createFakeCrashMatchingSessionPart(dir)

        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(pctMultiFilePersistenceEnabled = 100f),
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(nativeCrashCapture = true),
                symbols = symbols,
            ),
            setupAction = {
                setupFakeNativeCrash(serializer, updatedCrashData)
            },
            testCaseAction = {},
            assertAction = {
                // session envelope and crash envelope were delivered
                val envelopes = getSessionEnvelopes(1, ProcessState.FOREGROUND)
                val sessionEnvelope = envelopes.first()
                val logEnvelope = getSingleLogEnvelope()
                val crash = logEnvelope.getLastLog()
                val sessionPartSpan = checkNotNull(sessionEnvelope.getSessionPartSpan())

                // session envelope and crash envelope associate via session/process identifiers
                assertEquals(dir.userSessionId, sessionEnvelope.getUserSessionId())
                assertEquals(dir.sessionPartId, sessionEnvelope.getSessionPartId())

                val crashAttrs = checkNotNull(crash.attributes)
                assertEquals(dir.userSessionId, crashAttrs.findAttributeValue(EmbSessionAttributes.EMB_USER_SESSION_ID))
                assertEquals(dir.sessionPartId, crashAttrs.findAttributeValue(EmbSessionAttributes.EMB_SESSION_PART_ID))

                // span snapshots were converted to failed spans
                assertSpanSnapshotConversion(sessionEnvelope, sessionPartSpan)
            },
        )
    }

    private fun shutdownSdkWithIncompleteSession() {
        val workerThreadModule = testRule.bootstrapper.workerThreadModule
        val worker = workerThreadModule.backgroundWorker(Worker.Background.SessionPersistenceWorker)
        worker.shutdownAndWait(3000)
        testRule.bootstrapper.stop()
    }

    private fun obtainSessionPartDirectory(
        filesDir: File?,
    ): SessionPartDirectory? {
        val sessionDir = File(filesDir, StorageLocation.SESSION_SPLIT.dir)
        val sessionParts = checkNotNull(sessionDir.listFiles())
        assertEquals(1, sessionParts.size)
        return SessionPartDirectory.fromDirName(sessionParts.single().name)
    }

    private fun createFakeCrashMatchingSessionPart(dir: SessionPartDirectory): StoredNativeCrashData {
        val crashData = createStoredNativeCrashData(
            serializer = serializer,
            resourceFixtureName = "native_crash_1.txt",
            crashMetadata = fakeNativeCrashStoredTelemetryMetadata.copy(
                userSessionId = dir.userSessionId,
                sessionPartId = dir.sessionPartId,
                processIdentifier = "",
            ),
            sessionMetadata = fakeCachedSessionStoredTelemetryMetadata.copy(
                userSessionId = dir.userSessionId,
                sessionPartId = dir.sessionPartId,
                processIdentifier = "",
            ),
        )
        val updatedCrashData = crashData.copy(
            nativeCrash = crashData.nativeCrash.copy(
                userSessionId = dir.userSessionId,
                sessionPartId = dir.sessionPartId,
            ),
        )
        return updatedCrashData
    }

    private fun assertSpanSnapshotConversion(
        sessionEnvelope: Envelope<SessionPartPayload>,
        sessionPartSpan: Span,
    ) {
        assertEquals(emptyList<Span>(), sessionEnvelope.data.spanSnapshots)
        assertNotEquals(emptyList<Span>(), sessionEnvelope.data.spans)
        assertEquals("failure", sessionPartSpan.attributes?.findAttributeValue(EmbSpanAttributes.EMB_ERROR_CODE))
        assertEquals("crash", sessionPartSpan.attributes?.findAttributeValue(EmbSpanAttributes.EMB_TERMINATION_CAUSE))
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
                symbols = symbols,
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
            },
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
                    nativeCrashCapture = true,
                ),
                symbols = symbols,
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
            },
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
                symbols = symbols,
            ),
            setupAction = {
                setupCachedDataFromNativeCrash(crashData = crashData)
            },
            testCaseAction = {},
            assertAction = {
                with(getSingleSessionEnvelope()) {
                    assertDeadPartResurrected(null)
                }
                assertEquals(0, getLogEnvelopes(0).size)
            },
        )
    }

    @Test
    fun `empty crash envelope not available for native crash resurrection if background activity is enabled`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                backgroundActivityConfig = BackgroundActivityRemoteConfig(100f),
            ),
            testCaseAction = {
                recordSession()
                recordSession()
            },
            assertAction = {
                getSessionEnvelopes(2)
                assertTrue(cacheStorageService.getCachedCrashEnvelope().isEmpty())
            },
        )
    }

    @Test
    fun `one empty crash envelope available for native crash resurrection if background activity is not enabled`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                backgroundActivityConfig = BackgroundActivityRemoteConfig(0f),
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

            },
        )
    }

    private fun FakePayloadStorageService.getCachedCrashEnvelope(): List<StoredTelemetryMetadata> {
        return storedPayloadMetadata().filter { !it.complete && it.envelopeType == SupportedEnvelopeType.CRASH }
    }
}
