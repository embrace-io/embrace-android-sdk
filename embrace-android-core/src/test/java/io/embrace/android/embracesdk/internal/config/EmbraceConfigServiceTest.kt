package io.embrace.android.embracesdk.internal.config

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.fakeBackgroundWorker
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.comms.api.ApiService
import io.embrace.android.embracesdk.internal.comms.api.CachedConfig
import io.embrace.android.embracesdk.internal.comms.delivery.CacheService
import io.embrace.android.embracesdk.internal.config.remote.AnrRemoteConfig
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
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

internal class EmbraceConfigServiceTest {

    private lateinit var fakePreferenceService: PreferencesService
    private lateinit var service: EmbraceConfigService
    private lateinit var worker: BackgroundWorker

    companion object {
        private lateinit var remoteConfig: RemoteConfig
        private lateinit var mockApiService: ApiService
        private lateinit var processStateService: ProcessStateService
        private lateinit var mockCacheService: CacheService
        private lateinit var logger: EmbLogger
        private lateinit var fakeClock: FakeClock
        private lateinit var mockConfigListener: () -> Unit
        private lateinit var fakeCachedConfig: RemoteConfig
        private var configListenerTriggered = false

        /**
         * Setup before all tests get executed. Create mocks here.
         */
        @BeforeClass
        @JvmStatic
        fun setupBeforeAll() {
            mockkStatic(RemoteConfig::class)
            remoteConfig = RemoteConfig()
            mockApiService = mockk()
            processStateService = FakeProcessStateService()
            mockCacheService = mockk(relaxed = true)
            fakeClock = FakeClock()
            logger = EmbLoggerImpl()
            configListenerTriggered = false
            mockConfigListener = { configListenerTriggered = true }
            fakeCachedConfig = RemoteConfig( // alter config to trigger listener
                anrConfig = AnrRemoteConfig()
            )
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
        every { mockApiService.getConfig() } returns remoteConfig
        fakePreferenceService = FakePreferenceService(deviceIdentifier = "07D85B44E4E245F4A30E559BFC0D07FF")
        every {
            mockCacheService.loadObject<RemoteConfig>("config.json", RemoteConfig::class.java)
        } returns fakeCachedConfig
        every { mockApiService.getCachedConfig() } returns CachedConfig(fakeCachedConfig, null)
        worker = fakeBackgroundWorker()
        service = createService(worker = worker, action = {})
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
        assertEquals(0.0, service.thresholdCheck.getNormalizedDeviceId().toDouble(), 0.01)

        fakePreferenceService.deviceIdentifier = "07D85B44E4E245F4A30E559BFC0D07FF"
        assertEquals(100.0, service.thresholdCheck.getNormalizedDeviceId().toDouble(), 0.01)

        fakePreferenceService.deviceIdentifier = "07D85B44E4E245F4A30E559BFC0D0739"
        assertEquals(22.35, service.thresholdCheck.getNormalizedDeviceId().toDouble(), 0.01)

        fakePreferenceService.deviceIdentifier = "07D85B44E4E245F4A30E559BFC0D07D9"
        assertEquals(85.09, service.thresholdCheck.getNormalizedDeviceId().toDouble(), 0.01)
    }

    @Test
    fun `test new normalized DeviceId`() {
        fakePreferenceService.deviceIdentifier = "07D85B44E4E245F4A30E559BFC000000"
        assertEquals(0.0, service.thresholdCheck.getNormalizedLargeDeviceId().toDouble(), 0.01)

        fakePreferenceService.deviceIdentifier = "07D85B44E4E245F4A30E559BFCFFFFFF"
        assertEquals(100.0, service.thresholdCheck.getNormalizedLargeDeviceId().toDouble(), 0.01)

        fakePreferenceService.deviceIdentifier = "07D85B44E4E245F4A30E559BFC0D0739"
        assertEquals(5.08, service.thresholdCheck.getNormalizedLargeDeviceId().toDouble(), 0.01)

        fakePreferenceService.deviceIdentifier = "07D85B44E4E245F4A30E559BFCED0739"
        assertEquals(92.58, service.thresholdCheck.getNormalizedLargeDeviceId().toDouble(), 0.01)
    }

    @Test
    fun `test isBehaviourEnabled`() {
        fakePreferenceService.deviceIdentifier = "07D85B44E4E245F4A30E559BFC000000"
        assertFalse(service.thresholdCheck.isBehaviorEnabled(0.0f))
        assertTrue(service.thresholdCheck.isBehaviorEnabled(0.1f))
        assertTrue(service.thresholdCheck.isBehaviorEnabled(100.0f))
        assertTrue(service.thresholdCheck.isBehaviorEnabled(99.9f))
        assertTrue(service.thresholdCheck.isBehaviorEnabled(34.9f))

        fakePreferenceService.deviceIdentifier = "07D85B44E4E245F4A30E559BFCFFFFFF"
        assertFalse(service.thresholdCheck.isBehaviorEnabled(99.9f))
        assertTrue(service.thresholdCheck.isBehaviorEnabled(100.0f))

        fakePreferenceService.deviceIdentifier = "07D85B44E4E245F4A30E559BFC0D0739"
        assertFalse(service.thresholdCheck.isBehaviorEnabled(0.0f))
        assertFalse(service.thresholdCheck.isBehaviorEnabled(2.0f))
        assertFalse(service.thresholdCheck.isBehaviorEnabled(5.0f))
        assertFalse(service.thresholdCheck.isBehaviorEnabled(5.07f))
        assertTrue(service.thresholdCheck.isBehaviorEnabled(5.09f))
        assertTrue(service.thresholdCheck.isBehaviorEnabled(47.92f))
        assertTrue(service.thresholdCheck.isBehaviorEnabled(100.0f))
    }

    @Test
    fun `test isBehaviourEnabled with bad input`() {
        fakePreferenceService.deviceIdentifier = "07D85B44E4E245F4A30E559BFCFFFFFF"
        assertFalse(service.thresholdCheck.isBehaviorEnabled(1000f))
        assertFalse(service.thresholdCheck.isBehaviorEnabled(-1000f))
    }

    @Test
    fun `test config exists in cache and is loaded correctly`() {
        assertTrue(service.anrBehavior.isAnrCaptureEnabled())

        val obj = RemoteConfig(anrConfig = AnrRemoteConfig(pctEnabled = 0))
        every { mockApiService.getCachedConfig() } returns CachedConfig(obj, null)
        service.loadConfigFromCache()

        // config was updated
        assertFalse(service.anrBehavior.isAnrCaptureEnabled())
    }

    @Test
    fun `test config does not exist in cache, so it's not loaded`() {
        assertTrue(service.anrBehavior.isAnrCaptureEnabled())
        every { mockApiService.getCachedConfig() } returns CachedConfig(null, null)
        service.loadConfigFromCache()

        // config was not updated
        assertTrue(service.anrBehavior.isAnrCaptureEnabled())
    }

    @Test
    fun `test service constructor reads cached config`() {
        val obj = RemoteConfig(anrConfig = AnrRemoteConfig(pctEnabled = 0))
        every { mockApiService.getConfig() } returns null
        every { mockApiService.getCachedConfig() } returns CachedConfig(obj, null)
        service = createService(worker)

        // config was updated
        assertFalse(service.anrBehavior.isAnrCaptureEnabled())
    }

    /**
     * Test that calling getConfig() refreshes the config
     * As we are using a DirectExecutor this method will run synchronously and
     * return the updated config.
     * In a real situation, the async refresh would be triggered and the config returned would be the previous one.
     */
    @Test
    fun `test onForeground() refreshes the config`() {
        // advance the clock so it's safe to retry config refresh
        fakeClock.tick(1000000000000)
        val newConfig = RemoteConfig(anrConfig = AnrRemoteConfig())
        every { mockApiService.getConfig() } returns newConfig

        service.onForeground(true, 1100L)
        verify(exactly = 2) { mockApiService.getConfig() }
    }

    @Test
    fun `test app framework`() {
        assertEquals(AppFramework.NATIVE, service.appFramework)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testEmptyAppId() {
        createService(worker = worker, appId = null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNullAppId() {
        createService(worker = worker, appId = null)
    }

    @Test
    fun testNoAppIdRequiredWithExporters() {
        val cfg = OpenTelemetryConfiguration(
            SpanSinkImpl(),
            LogSinkImpl(),
            SystemInfo()
        )
        cfg.addLogExporter(FakeLogRecordExporter())
        val service = createService(worker = worker, config = cfg, appId = null)
        assertNotNull(service)
        assertTrue(service.isOnlyUsingOtelExporters())
    }

    /**
     * Create a new instance of the [EmbraceConfigService] using the passed in [worker] to run
     * tasks for its internal [BackgroundWorker]
     */
    private fun createService(
        worker: BackgroundWorker,
        action: ConfigService.() -> Unit = {},
        config: OpenTelemetryConfiguration = OpenTelemetryConfiguration(
            SpanSinkImpl(),
            LogSinkImpl(),
            SystemInfo()
        ),
        appId: String? = "AbCdE",
    ): EmbraceConfigService = EmbraceConfigService(
        openTelemetryCfg = config,
        preferencesService = fakePreferenceService,
        clock = fakeClock,
        backgroundWorker = worker,
        suppliedFramework = AppFramework.NATIVE,
        foregroundAction = action,
        appIdFromConfig = appId
    ).apply {
        remoteConfigSource = mockApiService
    }
}
