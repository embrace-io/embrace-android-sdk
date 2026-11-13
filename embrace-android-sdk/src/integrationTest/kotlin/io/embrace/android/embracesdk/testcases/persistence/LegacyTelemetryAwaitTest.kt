package io.embrace.android.embracesdk.testcases.persistence

import io.embrace.android.embracesdk.LastRunEndState
import io.embrace.android.embracesdk.assertions.getSessionPartId
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.fakeIncompleteSessionEnvelope
import io.embrace.android.embracesdk.fakes.testSdkStorage
import io.embrace.android.embracesdk.fixtures.FAKE_SESSION_PART_ID
import io.embrace.android.embracesdk.fixtures.FAKE_USER_SESSION_ID
import io.embrace.android.embracesdk.fixtures.fakeCachedSessionStoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.instrumentation.crash.CrashFileMarkerImpl
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.storage.LegacyStorageMigrator
import io.embrace.android.embracesdk.internal.storage.SdkDir
import io.embrace.android.embracesdk.internal.storage.lazyFile
import io.embrace.android.embracesdk.internal.storage.migration.LegacyTelemetryMigration
import io.embrace.android.embracesdk.internal.storage.migration.MigrationBudget
import io.embrace.android.embracesdk.internal.storage.migration.MigrationWorker
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch

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

    private val releaseMove = CountDownLatch(1)

    @After
    fun tearDown() {
        releaseMove.countDown()
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

    @Test
    fun `a telemetry move that never completes doesn't stop resurrection`() {
        val stalledMigration = stalledTelemetryMigration()
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(pctMultiFilePersistenceEnabled = 100.0f),
            setupAction = {
                persistSessionPart(
                    userSessionId = FAKE_USER_SESSION_ID,
                    sessionPartId = FAKE_SESSION_PART_ID,
                )
                awaitLegacyTelemetryMigration = stalledMigration::await
            },
            testCaseAction = {},
            assertAction = {
                val envelope = getSessionEnvelopes(1).single()
                assertEquals(FAKE_SESSION_PART_ID, envelope.getSessionPartId())
                assertFalse(stalledMigration.isComplete)
            },
        )
    }

    /**
     * A migration whose move is stuck behind a job that only finishes once the test ends.
     */
    private fun stalledTelemetryMigration(): LegacyTelemetryMigration {
        val errorHandler = FakeErrorHandler(throwOnSdkError = false)
        val data = Files.createTempDirectory("legacy-telemetry").toFile()
        File(data, "files/embrace").mkdirs()
        val worker = MigrationWorker(errorHandler)
        worker.submit("stall", Unit, SdkErrorType.Ignored) { releaseMove.await() }
        val migrator = LegacyStorageMigrator(
            FileSystem.SYSTEM,
            { File(data, "files").toOkioPath() },
            { File(data, "cache").toOkioPath() },
            { File(data, "no_backup").toOkioPath() },
            errorHandler,
        )
        return LegacyTelemetryMigration(worker, migrator, errorHandler, awaitTimeoutMs = 100).apply {
            move(MigrationBudget(worker, errorHandler, 0))
        }
    }

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
