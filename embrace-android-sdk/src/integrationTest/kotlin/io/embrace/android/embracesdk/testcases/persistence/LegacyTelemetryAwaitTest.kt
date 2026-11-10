package io.embrace.android.embracesdk.testcases.persistence

import io.embrace.android.embracesdk.LastRunEndState
import io.embrace.android.embracesdk.assertions.getSessionPartId
import io.embrace.android.embracesdk.fakes.fakeIncompleteSessionEnvelope
import io.embrace.android.embracesdk.fakes.testSdkStorage
import io.embrace.android.embracesdk.fixtures.FAKE_SESSION_PART_ID
import io.embrace.android.embracesdk.fixtures.FAKE_USER_SESSION_ID
import io.embrace.android.embracesdk.fixtures.fakeCachedSessionStoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.instrumentation.crash.CrashFileMarkerImpl
import io.embrace.android.embracesdk.internal.storage.SdkDir
import io.embrace.android.embracesdk.internal.storage.lazyFile
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import java.io.File

/**
 * The crash marker read, the session part reader and resurrection all wait for the legacy telemetry hook, so they
 * see files that the hook puts on disk.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
internal class LegacyTelemetryAwaitTest(
    private val otelSdkMode: OtelSdkMode,
) {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode) {
        EmbraceSetupInterface(
            workersToFake = listOf(Worker.Background.PersistenceWorker),
            fakeStorageLayer = true,
        ).apply {
            getFakedWorkerExecutor(Worker.Background.PersistenceWorker).blockingMode = false
        }
    }

    @Test
    fun `crash marker and cached session created by the hook are read`() {
        testRule.runTest(
            setupAction = {
                val cacheStorageService = checkNotNull(fakeCacheStorageService)
                val seed = lazy {
                    val installState = testSdkStorage(services.appContext).lazyFile(SdkDir.INSTALL_STATE).value
                    installState.mkdirs()
                    File(installState, CrashFileMarkerImpl.CRASH_MARKER_FILE_NAME).createNewFile()
                    cacheStorageService.addPayload(
                        fakeCachedSessionStoredTelemetryMetadata,
                        fakeIncompleteSessionEnvelope(
                            userSessionId = FAKE_USER_SESSION_ID,
                            sessionPartId = FAKE_SESSION_PART_ID,
                        ),
                    )
                }
                awaitLegacyTelemetryMigration = { seed.value }
            },
            testCaseAction = {
                assertEquals(LastRunEndState.CRASH, embrace.lastRunEndState)
            },
            assertAction = {
                val envelope = getSessionEnvelopes(1).single()
                assertEquals(FAKE_SESSION_PART_ID, envelope.getSessionPartId())
            },
        )
    }

    @Test
    fun `session part created by the hook is resurrected`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(pctMultiFilePersistenceEnabled = 100.0f),
            setupAction = {
                val seed = lazy {
                    persistSessionPart(
                        userSessionId = FAKE_USER_SESSION_ID,
                        sessionPartId = FAKE_SESSION_PART_ID,
                    )
                }
                awaitLegacyTelemetryMigration = { seed.value }
            },
            testCaseAction = {},
            assertAction = {
                val envelope = getSessionEnvelopes(1).single()
                assertEquals(FAKE_SESSION_PART_ID, envelope.getSessionPartId())
            },
        )
    }

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
