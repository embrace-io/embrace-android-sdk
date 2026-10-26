package io.embrace.android.embracesdk.internal.instance

import android.content.Context
import android.content.ContextWrapper
import io.embrace.android.embracesdk.assertions.waitUntilConditionMet
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.fakes.FilesDirApplication
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.config.resolved.SdkModeConfig
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.injection.ConfigLoadServices
import io.embrace.android.embracesdk.internal.injection.ConfigServiceSupplier
import io.embrace.android.embracesdk.internal.injection.PreStartServices
import io.embrace.android.embracesdk.internal.injection.SdkOverrides
import io.embrace.android.embracesdk.internal.worker.Worker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class SdkStateHolderFactoryTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private lateinit var context: Context
    private val preStartServices = PreStartServices(errorHandler = FakeErrorHandler(throwOnSdkError = false))
    private val workerThreadPool = RecordingWorkerThreadPool()

    @Before
    fun setUp() {
        context = FilesDirApplication(tempDir.newFolder())
    }

    @Test
    fun `otel config set before start is applied to pre-start services`() {
        val holder = createSdkStateHolder(
            preStartServices = preStartServices,
            sdkInitResourceUsageTrackerSupplier = { error("failed to init") },
        )
        holder.api.addSpanExporter(FakeSpanExporter())
        assertFalse(preStartServices.externalOtelConfig.hasConfiguredOtlpExport())
        holder.start(context)
        assertTrue(preStartServices.externalOtelConfig.hasConfiguredOtlpExport())
        assertEquals(SdkState.FAILED, holder.state)
    }

    @Test
    fun `sdk services are not constructed when the sdk is remotely disabled`() {
        var servicesCreated = false
        val holder = createStateHolder(
            configServiceSupplier = { _, _, _, _, _, _ -> disabledConfigService() },
            overrides = SdkOverrides(
                appContext = {
                    servicesCreated = true
                    error("sdk services should not be created")
                },
            ),
        )
        holder.start(context)
        assertFalse(servicesCreated)
        assertFalse(holder.api.isStarted)
        assertEquals(SdkState.DISABLED, holder.state)
    }

    @Test
    fun `worker threads are released after the config request when the sdk is remotely disabled`() {
        val configRequestRan = CountDownLatch(1)
        val holder = createStateHolder(
            configServiceSupplier = { _, _, _, _, workerThreadPool, _ ->
                // schedule task on same worker as config HTTP requests
                workerThreadPool.backgroundWorker(Worker.Background.HttpRequestWorker).scheduleWithFixedDelay(
                    { configRequestRan.countDown() },
                    0,
                    1,
                    TimeUnit.HOURS,
                )
                disabledConfigService()
            },
        )
        holder.start(context)
        assertTrue(workerThreadPool.closed.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS))
        assertEquals(0, configRequestRan.count)
    }

    @Test
    fun `worker threads are released when the sdk services cannot be constructed`() {
        val holder = createStateHolder(
            configServiceSupplier = { _, _, _, _, _, _ -> FakeConfigService() },
            overrides = SdkOverrides(appContext = { error("failed to init") }),
        )
        holder.start(context)
        assertEquals(SdkState.FAILED, holder.state)
        assertTrue(workerThreadPool.closed.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `disabling after a start that fails deletes the sdk files`() {
        val sdkFile = createSdkFile()
        val holder = createStateHolder(
            configServiceSupplier = { _, _, _, _, _, _ -> FakeConfigService() },
            overrides = SdkOverrides(appContext = { error("failed to init") }),
        )
        holder.start(context)
        assertEquals(SdkState.FAILED, holder.state)
        assertTrue(sdkFile.exists())

        holder.close()
        awaitDeleted(sdkFile)
    }

    /**
     * A start whose context can't say where the SDK's files are leaves nothing to delete, which neither the start nor a
     * disable fails on.
     */
    @Test
    fun `disabling after a failed start whose storage can't be found still disables the sdk`() {
        val noStorage = object : ContextWrapper(context) {
            override fun getFilesDir(): File = error("no files dir")
        }
        val holder = createStateHolder(
            configServiceSupplier = { _, _, _, _, _, _ -> FakeConfigService() },
            overrides = SdkOverrides(appContext = { error("failed to init") }),
        )
        holder.start(noStorage)
        assertEquals(SdkState.FAILED, holder.state)
        holder.close()
        assertEquals(SdkState.DISABLED, holder.state)
    }

    private fun createSdkFile(): File = File(File(context.filesDir, StorageLocation.PAYLOAD.dir), "payload").apply {
        parentFile?.mkdirs()
        writeText("data")
    }

    private fun awaitDeleted(file: File) {
        waitUntilConditionMet(
            waitTimeMs = AWAIT_TIMEOUT_MS.toInt(),
            dataProvider = { file.exists() },
            condition = { exists -> !exists },
        )
    }

    private fun createStateHolder(
        configServiceSupplier: ConfigServiceSupplier,
        overrides: SdkOverrides? = null,
    ): SdkStateHolder = createSdkStateHolder(
        preStartServices = preStartServices,
        configLoadServicesSupplier = { ctx, preStartServices ->
            ConfigLoadServices(
                context = ctx,
                preStartServices = preStartServices,
                workerThreadPoolSupplier = { workerThreadPool },
                configServiceSupplier = configServiceSupplier,
            )
        },
        overrides = overrides,
    )

    private fun disabledConfigService(): ConfigService = FakeConfigService(
        config = EmbraceConfig(sdkMode = { SdkModeConfig(sdkDisabled = { true }) }),
    )

    private companion object {
        const val AWAIT_TIMEOUT_MS = 5000L
    }
}
