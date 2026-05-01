@file:OptIn(io.embrace.android.embracesdk.semconv.ExperimentalSemconv::class)
@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.testcases.session

import android.app.ApplicationExitInfo
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.getLastLog
import io.embrace.android.embracesdk.assertions.getLogOfType
import io.embrace.android.embracesdk.assertions.getSessionId
import io.embrace.android.embracesdk.assertions.getUserSessionId
import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.TestAeiData
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.fakeIncompleteSessionEnvelope
import io.embrace.android.embracesdk.fakes.setupFakeAeiData
import io.embrace.android.embracesdk.fixtures.fakeCachedSessionStoredTelemetryMetadata
import io.embrace.android.embracesdk.fixtures.fakeNativeCrashStoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.capture.connectivity.ConnectionType
import io.embrace.android.embracesdk.internal.capture.connectivity.ConnectivityStatus
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.semconv.EmbAeiAttributes
import io.embrace.android.embracesdk.testcases.features.createNativeSymbolsForCurrentArch
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule.Companion.DEFAULT_SDK_START_TIME_MS
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.actions.StoredNativeCrashData
import io.embrace.android.embracesdk.testframework.actions.createStoredNativeCrashData
import io.opentelemetry.kotlin.semconv.SessionAttributes.SESSION_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivityManager

@Config(sdk = [UPSIDE_DOWN_CAKE], shadows = [ShadowActivityManager::class])
@RunWith(AndroidJUnit4::class)
internal class UserSessionResurrectionTransitionTest {

    private val serializer = TestPlatformSerializer()
    private val fakeSymbols = mapOf("libfoo.so" to "symbol_content")
    private val symbols = createNativeSymbolsForCurrentArch(fakeSymbols)
    private lateinit var cacheStorageService: FakePayloadStorageService

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule {
        EmbraceSetupInterface(fakeStorageLayer = true).apply {
            getEmbLogger().throwOnInternalError = false
        }.also {
            cacheStorageService = checkNotNull(it.fakeCacheStorageService)
        }
    }

    @Test
    fun `resurrected session part from JVM crash keeps old user session id when new user session is created offline`() {
        val oldUserSessionId = USER_SESSION_ID_A
        val sessionStartMs = DEFAULT_SDK_START_TIME_MS - DEFAULT_INACTIVITY_TIMEOUT_MS - 60_000L
        val sessionMetadata = fakeCachedSessionStoredTelemetryMetadata.copy(timestamp = sessionStartMs)

        testRule.runTest(
            setupAction = {
                fakeNetworkConnectivityService.connectivityStatus = ConnectivityStatus.None
                persistUserSession(
                    sessionId = oldUserSessionId,
                    startMs = sessionStartMs,
                    lastActivityMs = DEFAULT_SDK_START_TIME_MS - DEFAULT_INACTIVITY_TIMEOUT_MS - 1L,
                )
                cacheDeadSessionPart(
                    sessionId = oldUserSessionId,
                    sessionMetadata = sessionMetadata,
                )
            },
            testCaseAction = {
                recordSession()
                simulateConnectionTypeChange(ConnectionType.WIFI)
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                val resurrected = sessions.first()
                val live = sessions.last()

                assertEquals(oldUserSessionId, resurrected.getSessionId())
                assertNotEquals(oldUserSessionId, live.getSessionId())
                assertNotEquals(oldUserSessionId, live.getUserSessionId())
                assertNotNull(live.getUserSessionId())
            },
        )
    }

    @Test
    fun `resurrected payloads from native crash keeps old user session id when new user session is created offline`() {
        val oldUserSessionId = USER_SESSION_ID_A
        val sessionPartId = SESSION_PART_ID_A
        val sessionStartMs = DEFAULT_SDK_START_TIME_MS - DEFAULT_INACTIVITY_TIMEOUT_MS - 60_000L
        val sessionMetadata = fakeCachedSessionStoredTelemetryMetadata.copy(timestamp = sessionStartMs)
        val baseFixture = createStoredNativeCrashData(
            serializer = serializer,
            resourceFixtureName = "native_crash_1.txt",
            crashMetadata = fakeNativeCrashStoredTelemetryMetadata,
            sessionMetadata = sessionMetadata,
        )
        val customNativeCrash = baseFixture.nativeCrash.copy(
            sessionPartId = sessionPartId,
            userSessionId = oldUserSessionId,
        )
        val customPartEnvelope = fakeIncompleteSessionEnvelope(
            sessionId = oldUserSessionId,
            startMs = sessionMetadata.timestamp,
            lastHeartbeatTimeMs = sessionMetadata.timestamp + 1_000L,
            processIdentifier = sessionMetadata.processIdentifier,
        )
        val crashData = baseFixture.copy(
            nativeCrash = customNativeCrash,
            partEnvelope = customPartEnvelope,
        )

        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(nativeCrashCapture = true),
                symbols = symbols,
            ),
            setupAction = {
                fakeNetworkConnectivityService.connectivityStatus = ConnectivityStatus.None
                persistUserSession(
                    sessionId = oldUserSessionId,
                    startMs = sessionStartMs,
                    lastActivityMs = DEFAULT_SDK_START_TIME_MS - DEFAULT_INACTIVITY_TIMEOUT_MS - 1L,
                )
                setupCachedDataFromNativeCrash(crashData = crashData)
                setupFakeNativeCrash(serializer, crashData)
            },
            testCaseAction = {
                recordSession()
                simulateConnectionTypeChange(ConnectionType.WIFI)
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                val resurrected = sessions.first()
                val live = sessions.last()

                assertEquals(oldUserSessionId, resurrected.getSessionId())
                assertNotEquals(oldUserSessionId, live.getSessionId())
                assertNotEquals(oldUserSessionId, live.getUserSessionId())

                val crashLog = getSingleLogEnvelope().getLastLog()
                val crashAttrs = checkNotNull(crashLog.attributes)
                assertEquals(oldUserSessionId, crashAttrs.findAttributeValue(SESSION_ID))
                assertEquals(sessionPartId, crashAttrs.findAttributeValue("emb.session_part_id"))
                assertNotEquals(live.getUserSessionId(), crashAttrs.findAttributeValue(SESSION_ID))
            },
        )
    }

    @Test
    fun `session times out while device is offline works as expected`() {
        val oldUserSessionId = USER_SESSION_ID_A
        val sessionStartMs = DEFAULT_SDK_START_TIME_MS - DEFAULT_INACTIVITY_TIMEOUT_MS - 60_000L
        val sessionMetadata = fakeCachedSessionStoredTelemetryMetadata.copy(timestamp = sessionStartMs)

        testRule.runTest(
            setupAction = {
                fakeNetworkConnectivityService.connectivityStatus = ConnectivityStatus.None
                persistUserSession(
                    sessionId = oldUserSessionId,
                    startMs = sessionStartMs,
                    lastActivityMs = DEFAULT_SDK_START_TIME_MS - DEFAULT_INACTIVITY_TIMEOUT_MS - 1L,
                )
                cacheDeadSessionPart(
                    sessionId = oldUserSessionId,
                    sessionMetadata = sessionMetadata,
                )
            },
            testCaseAction = {
                recordSession()
                simulateConnectionTypeChange(ConnectionType.WIFI)
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                val cached = sessions.first()
                val live = sessions.last()

                assertEquals(oldUserSessionId, cached.getSessionId())
                assertNotEquals(oldUserSessionId, live.getUserSessionId())
                assertNotEquals(cached.getSessionId(), live.getUserSessionId())
            },
        )
    }

    @Test
    fun `native crash has previous session ID when no session metadata is on disk even when resurrected in a new session`() {
        val justEndedUserSessionId = USER_SESSION_ID_A
        val sessionPartId = SESSION_PART_ID_A
        val crashData = makeOrphanedNativeCrashWithIds(
            userSessionId = justEndedUserSessionId,
            sessionPartId = sessionPartId,
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
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val crashLog = getSingleLogEnvelope().getLogOfType(EmbType.System.NativeCrash)
                val attrs = checkNotNull(crashLog.attributes)
                assertEquals(justEndedUserSessionId, attrs.findAttributeValue(SESSION_ID))
                assertEquals(sessionPartId, attrs.findAttributeValue("emb.session_part_id"))

                val live = getSingleSessionEnvelope()
                assertNotEquals(justEndedUserSessionId, live.getUserSessionId())
            },
        )
    }

    @Test
    fun `native crash with empty session ids is sent`() {
        val crashData = makeOrphanedNativeCrashWithIds(
            userSessionId = "",
            sessionPartId = "",
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
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val crashLog = getSingleLogEnvelope().getLogOfType(EmbType.System.NativeCrash)
                val attrs = checkNotNull(crashLog.attributes)
                assertEquals("", attrs.findAttributeValue(SESSION_ID))
                assertEquals("", attrs.findAttributeValue("emb.session_part_id"))
            },
        )
    }

    @Test
    fun `AEI payload carries old user session id when persisted session expired between app instances`() {
        val oldUserSessionId = USER_SESSION_ID_A
        val oldSessionPartId = SESSION_PART_ID_A
        val processStateSummary = "${oldSessionPartId}_${oldUserSessionId}"
        val sessionStartMs = DEFAULT_SDK_START_TIME_MS - DEFAULT_INACTIVITY_TIMEOUT_MS - 60_000L

        val nativeAei = TestAeiData(
            reason = ApplicationExitInfo.REASON_CRASH_NATIVE,
            status = 6,
            description = "ndkCrash",
            trace = "someNdkCrashDetails",
            processStateSummary = processStateSummary,
        )

        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(nativeCrashCapture = true),
            ),
            setupAction = {
                persistUserSession(
                    sessionId = oldUserSessionId,
                    startMs = sessionStartMs,
                    lastActivityMs = DEFAULT_SDK_START_TIME_MS - DEFAULT_INACTIVITY_TIMEOUT_MS - 1L,
                )
                setupFakeAeiData(listOf(nativeAei.toAeiObject()))
            },
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val aeiLog = getSingleLogEnvelope().getLastLog()
                val attrs = checkNotNull(aeiLog.attributes)
                assertEquals(oldSessionPartId, attrs.findAttributeValue(EmbAeiAttributes.AEI_SESSION_PART_ID))
                assertEquals(oldUserSessionId, attrs.findAttributeValue(EmbAeiAttributes.AEI_USER_SESSION_ID))

                val live = getSingleSessionEnvelope()
                assertNotEquals(oldUserSessionId, live.getUserSessionId())
            },
        )
    }

    private fun EmbraceSetupInterface.cacheDeadSessionPart(
        sessionId: String,
        sessionMetadata: StoredTelemetryMetadata,
    ) {
        val deadPart = fakeIncompleteSessionEnvelope(
            sessionId = sessionId,
            startMs = sessionMetadata.timestamp,
            lastHeartbeatTimeMs = sessionMetadata.timestamp + 1_000L,
            processIdentifier = sessionMetadata.processIdentifier,
        )
        checkNotNull(fakeCacheStorageService).addPayload(sessionMetadata, deadPart)
    }

    /** Builds a [StoredNativeCrashData] with explicit ids and **no** associated session metadata,
     *  so process 2 sees a session-less cached crash and only produces the live process-2 session
     *  envelope. */
    private fun makeOrphanedNativeCrashWithIds(
        userSessionId: String,
        sessionPartId: String,
    ): StoredNativeCrashData = createStoredNativeCrashData(
        serializer = serializer,
        resourceFixtureName = "native_crash_1.txt",
        crashMetadata = fakeNativeCrashStoredTelemetryMetadata,
        sessionMetadata = null,
    ).let { base ->
        val customCrash = base.nativeCrash.copy(
            sessionPartId = sessionPartId,
            userSessionId = userSessionId,
        )
        base.copy(nativeCrash = customCrash)
    }

    private companion object {
        const val USER_SESSION_ID_A = "aabbccdd11223344aabbccdd11223344"
        const val SESSION_PART_ID_A = "11112222333344445555666677778888"
        const val DEFAULT_INACTIVITY_TIMEOUT_MS = 1_800L * 1_000L
    }
}
