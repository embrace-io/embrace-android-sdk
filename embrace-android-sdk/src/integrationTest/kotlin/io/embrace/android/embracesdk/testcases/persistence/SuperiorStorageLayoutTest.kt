package io.embrace.android.embracesdk.testcases.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.embrace.android.embracesdk.LastRunEndState
import io.embrace.android.embracesdk.assertions.getSessionPartId
import io.embrace.android.embracesdk.fakes.testSdkStorage
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.storage.SdkDir
import io.embrace.android.embracesdk.internal.storage.SdkStorage
import io.embrace.android.embracesdk.internal.storage.SdkStorageLayout
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import java.io.File

@RunWith(ParameterizedRobolectricTestRunner::class)
internal class SuperiorStorageLayoutTest(
    private val otelSdkMode: OtelSdkMode,
) {

    private val ctx: Context by lazy { ApplicationProvider.getApplicationContext() }
    private val storage: SdkStorage by lazy { testSdkStorage(ctx) }
    private val remoteConfig = RemoteConfig(pctMultiFilePersistenceEnabled = 100f)

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode) {
        EmbraceSetupInterface(
            workersToFake = listOf(Worker.Background.PersistenceWorker),
        ).apply {
            getFakedWorkerExecutor(Worker.Background.PersistenceWorker).blockingMode = false
        }
    }

    @Test
    fun `config, telemetry and the crash marker are kept under xcor`() {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig,
            testCaseAction = {
                recordSession(endInBackground = false)
                shutdownSdkWithIncompleteSession()
            },
        )
        val deadPart = deadSessionPart()
        val crashMarker = File(storage.dir(SdkDir.INSTALL_STATE).toFile(), SdkStorageLayout.CRASH_MARKER_FILE_NAME)

        testRule.runTest(
            persistedRemoteConfig = remoteConfig,
            setupAction = {
                crashMarker.writeText("1")
            },
            testCaseAction = {
                assertEquals(LastRunEndState.CRASH, embrace.lastRunEndState)
            },
            assertAction = {
                val resurrected = getSessionEnvelopes(1, ProcessState.FOREGROUND).single()
                assertEquals(deadPart.sessionPartId, resurrected.getSessionPartId())
                assertFalse(crashMarker.exists())
            },
        )
        assertNoFilesIn(ctx.filesDir)
        assertNoFilesIn(ctx.cacheDir)
    }

    @Test
    fun `persisted config is read from xcor`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(threshold = 0),
            serverResponseConfig = RemoteConfig(threshold = 100),
            expectSdkToStart = false,
            testCaseAction = {
                assertFalse(embrace.isStarted)
            },
        )
    }

    private fun shutdownSdkWithIncompleteSession() {
        testRule.setup.getFakedWorkerExecutor(Worker.Background.PersistenceWorker).shutdown()
        testRule.services.networkConnectivityService.close()
        testRule.services.otelSdkConfig.close()
        testRule.services.workerThreadPool.close()
    }

    private fun deadSessionPart(): SessionPartDirectory {
        val parts = checkNotNull(storage.dir(SdkDir.TELEMETRY_PENDING_SESSION_PARTS).toFile().listFiles())
        return checkNotNull(SessionPartDirectory.fromDirName(parts.single().name))
    }

    private fun assertNoFilesIn(dir: File) {
        assertEquals(emptyList<File>(), dir.walk().filter(File::isFile).toList())
    }

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
