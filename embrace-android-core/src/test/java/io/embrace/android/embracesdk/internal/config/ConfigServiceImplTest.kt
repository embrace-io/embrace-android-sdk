package io.embrace.android.embracesdk.internal.config

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeProjectConfig
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.config.behavior.BehaviorThresholdCheck
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.opentelemetry.OpenTelemetryConfiguration
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.internal.spans.SpanSinkImpl
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.mockk.clearAllMocks
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

internal class ConfigServiceImplTest {

    private lateinit var fakePreferenceService: PreferencesService
    private lateinit var service: ConfigServiceImpl
    private lateinit var worker: BackgroundWorker
    private lateinit var executor: BlockingScheduledExecutorService
    private lateinit var thresholdCheck: BehaviorThresholdCheck

    companion object {
        private lateinit var remoteConfig: RemoteConfig
        private lateinit var processStateService: ProcessStateService
        private lateinit var logger: EmbLogger
        private lateinit var fakeClock: FakeClock

        /**
         * Setup before all tests get executed. Create mocks here.
         */
        @BeforeClass
        @JvmStatic
        fun setupBeforeAll() {
            mockkStatic(RemoteConfig::class)
            remoteConfig = RemoteConfig()
            processStateService = FakeProcessStateService()
            fakeClock = FakeClock()
            logger = EmbLoggerImpl()
        }

        /**
         * Setup after all tests get executed. Un-mock all here.
         */
        @AfterClass
        @JvmStatic
        fun tearDownAfterAll() {
            unmockkAll()
        }
    }

    /**
     * Setup before each test.
     */
    @Before
    fun setup() {
        fakeClock.setCurrentTime(1000000000000)
        fakePreferenceService = FakePreferenceService(deviceIdentifier = "07D85B44E4E245F4A30E559BFC0D07FF")
        executor = BlockingScheduledExecutorService(blockingMode = false)
        worker = BackgroundWorker(executor)
        service = createService()
        assertFalse(service.isOnlyUsingOtelExporters())
    }

    /**
     * Setup after each test. Clean mocks content.
     */
    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Suppress("DEPRECATION")
    @Test
    fun `test legacy normalized DeviceId`() {
        fakePreferenceService.deviceIdentifier = "07D85B44E4E245F4A30E559BFC0D0700"
        assertEquals(0.0, thresholdCheck.getNormalizedDeviceId().toDouble(), 0.01)

        fakePreferenceService.deviceIdentifier = "07D85B44E4E245F4A30E559BFC0D07FF"
        assertEquals(100.0, thresholdCheck.getNormalizedDeviceId().toDouble(), 0.01)

        fakePreferenceService.deviceIdentifier = "07D85B44E4E245F4A30E559BFC0D0739"
        assertEquals(22.35, thresholdCheck.getNormalizedDeviceId().toDouble(), 0.01)

        fakePreferenceService.deviceIdentifier = "07D85B44E4E245F4A30E559BFC0D07D9"
        assertEquals(85.09, thresholdCheck.getNormalizedDeviceId().toDouble(), 0.01)
    }

    @Test
    fun `test new normalized DeviceId`() {
        fakePreferenceService.deviceIdentifier = "07D85B44E4E245F4A30E559BFC000000"
        assertEquals(0.0, thresholdCheck.getNormalizedLargeDeviceId().toDouble(), 0.01)

        fakePreferenceService.deviceIdentifier = "07D85B44E4E245F4A30E559BFCFFFFFF"
        assertEquals(100.0, thresholdCheck.getNormalizedLargeDeviceId().toDouble(), 0.01)

        fakePreferenceService.deviceIdentifier = "07D85B44E4E245F4A30E559BFC0D0739"
        assertEquals(5.08, thresholdCheck.getNormalizedLargeDeviceId().toDouble(), 0.01)

        fakePreferenceService.deviceIdentifier = "07D85B44E4E245F4A30E559BFCED0739"
        assertEquals(92.58, thresholdCheck.getNormalizedLargeDeviceId().toDouble(), 0.01)
    }

    @Test
    fun `test isBehaviourEnabled`() {
        fakePreferenceService.deviceIdentifier = "07D85B44E4E245F4A30E559BFC000000"
        assertFalse(thresholdCheck.isBehaviorEnabled(0.0f))
        assertTrue(thresholdCheck.isBehaviorEnabled(0.1f))
        assertTrue(thresholdCheck.isBehaviorEnabled(100.0f))
        assertTrue(thresholdCheck.isBehaviorEnabled(99.9f))
        assertTrue(thresholdCheck.isBehaviorEnabled(34.9f))

        fakePreferenceService.deviceIdentifier = "07D85B44E4E245F4A30E559BFCFFFFFF"
        assertFalse(thresholdCheck.isBehaviorEnabled(99.9f))
        assertTrue(thresholdCheck.isBehaviorEnabled(100.0f))

        fakePreferenceService.deviceIdentifier = "07D85B44E4E245F4A30E559BFC0D0739"
        assertFalse(thresholdCheck.isBehaviorEnabled(0.0f))
        assertFalse(thresholdCheck.isBehaviorEnabled(2.0f))
        assertFalse(thresholdCheck.isBehaviorEnabled(5.0f))
        assertFalse(thresholdCheck.isBehaviorEnabled(5.07f))
        assertTrue(thresholdCheck.isBehaviorEnabled(5.09f))
        assertTrue(thresholdCheck.isBehaviorEnabled(47.92f))
        assertTrue(thresholdCheck.isBehaviorEnabled(100.0f))
    }

    @Test
    fun `test isBehaviourEnabled with bad input`() {
        fakePreferenceService.deviceIdentifier = "07D85B44E4E245F4A30E559BFCFFFFFF"
        assertFalse(thresholdCheck.isBehaviorEnabled(1000f))
        assertFalse(thresholdCheck.isBehaviorEnabled(-1000f))
    }

    @Test
    fun `test app framework`() {
        assertEquals(AppFramework.NATIVE, service.appFramework)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testEmptyAppId() {
        createService(appId = null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNullAppId() {
        createService(appId = null)
    }

    @Test
    fun testNoAppIdRequiredWithExporters() {
        val cfg = OpenTelemetryConfiguration(
            SpanSinkImpl(),
            LogSinkImpl(),
            SystemInfo()
        )
        cfg.addLogExporter(FakeLogRecordExporter())
        val service = createService(config = cfg, appId = null)
        assertNotNull(service)
        assertTrue(service.isOnlyUsingOtelExporters())
    }

    /**
     * Create a new instance of the [ConfigServiceImpl] using the passed in [worker] to run
     * tasks for its internal [BackgroundWorker]
     */
    private fun createService(
        config: OpenTelemetryConfiguration = OpenTelemetryConfiguration(
            SpanSinkImpl(),
            LogSinkImpl(),
            SystemInfo()
        ),
        appId: String? = "AbCdE",
    ): ConfigServiceImpl {
        thresholdCheck = BehaviorThresholdCheck { fakePreferenceService.deviceIdentifier }
        return ConfigServiceImpl(
            openTelemetryCfg = config,
            preferencesService = fakePreferenceService,
            suppliedFramework = AppFramework.NATIVE,
            instrumentedConfig = FakeInstrumentedConfig(project = FakeProjectConfig(appId = appId)),
            remoteConfig = remoteConfig,
            thresholdCheck = thresholdCheck
        )
    }
}
