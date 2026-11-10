package io.embrace.android.embracesdk.testcases.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.embrace.android.embracesdk.LastRunEndState
import io.embrace.android.embracesdk.assertions.getSessionPartId
import io.embrace.android.embracesdk.assertions.returnIfConditionMet
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.testSdkStorage
import io.embrace.android.embracesdk.fixtures.fakeLogStoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.storage.LegacyStorageMigrator
import io.embrace.android.embracesdk.internal.storage.SdkDir
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * An install upgrading from 9.x keeps its config and telemetry, which the first launch moves under `xcor/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
internal class LegacyStorageUpgradeTest(
    private val otelSdkMode: OtelSdkMode,
) {

    private val ctx: Context by lazy { ApplicationProvider.getApplicationContext() }
    private val legacyConfig = RemoteConfig(pctMultiFilePersistenceEnabled = 100f, maxUserSessionProperties = 7)

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode, superiorStorageSystemEnabled = true) {
        EmbraceSetupInterface(
            workersToFake = listOf(Worker.Background.PersistenceWorker),
        ).apply {
            getFakedWorkerExecutor(Worker.Background.PersistenceWorker).blockingMode = false
        }
    }

    @Test
    fun `a 9x install keeps its config and telemetry`() {
        testRule.runTest(
            persistedRemoteConfig = legacyConfig,
            setupAction = {
                superiorStorageSystemEnabled = false
            },
            testCaseAction = {
                recordSession(endInBackground = false)
                shutdownSdkWithIncompleteSession()
            },
        )
        val deadPart = deadSessionPart()

        testRule.runTest(
            persistRemoteConfig = false,
            serverResponseConfig = legacyConfig,
            setupAction = {
                writeLegacyFile("embrace/embrace_crash_marker", "1".toByteArray())
                writeLegacyFile("embrace_payloads/${fakeLogStoredTelemetryMetadata.filename}", legacyLogPayload())
            },
            testCaseAction = {
                assertEquals(LastRunEndState.CRASH, embrace.lastRunEndState)
                assertEquals(7, testRule.services.configService.sessionBehavior.getMaxUserSessionProperties())
            },
            assertAction = {
                val resurrected = getSessionEnvelopes(1, ProcessState.FOREGROUND).single()
                assertEquals(deadPart.sessionPartId, resurrected.getSessionPartId())
                val log = getLogEnvelopes(1).single().data.logs?.single()
                assertEquals(LEGACY_LOG_BODY, log?.body)
            },
        )
        LegacyStorageMigrator.LEGACY_DIR_NAMES.values.forEach {
            assertFalse("$it was left behind", File(ctx.filesDir, it).exists())
        }
    }

    @Test
    fun `disable deletes 9x telemetry the migration hasn't moved`() {
        val legacyPayload = File(ctx.filesDir, "embrace_payloads/unmigrated")
        val legacyCachePayload = File(ctx.cacheDir, "embrace_payloads/unmigrated")
        testRule.runTest(
            setupAction = {
                writeLegacyFile("embrace/leftover", "x".toByteArray())
            },
            testCaseAction = {
                // the 9.x install state is deleted last, so the migration has finished once it's gone
                awaitDeleted(File(ctx.filesDir, "embrace"))
                listOf(legacyPayload, legacyCachePayload).forEach {
                    checkNotNull(it.parentFile).mkdirs()
                    it.writeText("x")
                }
                embrace.disable()
            },
            assertAction = {
                awaitDeleted(legacyPayload)
                awaitDeleted(legacyCachePayload)
            },
        )
    }

    private fun awaitDeleted(file: File) {
        returnIfConditionMet(
            desiredValueSupplier = { true },
            dataProvider = { !file.exists() },
            condition = { it },
        )
    }

    private fun shutdownSdkWithIncompleteSession() {
        testRule.setup.getFakedWorkerExecutor(Worker.Background.PersistenceWorker).shutdown()
        testRule.services.networkConnectivityService.close()
        testRule.services.otelSdkConfig.close()
        testRule.services.workerThreadPool.close()
    }

    private fun deadSessionPart(): SessionPartDirectory {
        val legacyParts = testSdkStorage(ctx, superiorStorageSystemEnabled = false)
            .dir(SdkDir.TELEMETRY_PENDING_SESSION_PARTS).toFile()
        val parts = checkNotNull(legacyParts.listFiles())
        return checkNotNull(SessionPartDirectory.fromDirName(parts.single().name))
    }

    private fun writeLegacyFile(path: String, bytes: ByteArray) {
        File(ctx.filesDir, path).apply {
            checkNotNull(parentFile).mkdirs()
            writeBytes(bytes)
        }
    }

    private fun legacyLogPayload(): ByteArray {
        val envelope = Envelope(data = LogPayload(logs = listOf(Log(body = LEGACY_LOG_BODY))))
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use {
            TestPlatformSerializer().toJson(envelope, Envelope.logEnvelopeSerializer, it)
        }
        return bytes.toByteArray()
    }

    internal companion object {
        const val LEGACY_LOG_BODY = "logged by 9.x"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
