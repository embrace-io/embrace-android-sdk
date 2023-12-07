package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.config.LocalConfigParser
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class LocalConfigTest {

    private val serializer = EmbraceSerializer()

    @Test
    fun testEmptyConfig() {
        val localConfig = LocalConfigParser.buildConfig("GrCPU", false, null, serializer)
        assertNotNull(localConfig)
    }

    @Test
    fun testAppOnlyConfig() {
        val localConfig =
            LocalConfigParser.buildConfig(
                "GrCPU",
                false,
                "{\"app\": {\"report_disk_usage\": false}}",
                serializer
            )
        assertFalse(checkNotNull(localConfig.sdkConfig.app?.reportDiskUsage))
    }

    @Test
    fun testBetaFunctionalityOnlyConfig() {
        // disabled explicitly
        var localConfig =
            LocalConfigParser.buildConfig("GrCPU", false, "{\"beta_features_enabled\": false}", serializer)
        assertFalse(checkNotNull(localConfig.sdkConfig.betaFeaturesEnabled))

        // enabled explicitly
        localConfig = LocalConfigParser.buildConfig(
            "GrCPU",
            false,
            "{\"beta_features_enabled\": true}",
            serializer
        )
        assertTrue(checkNotNull(localConfig.sdkConfig.betaFeaturesEnabled))

        // enabled by default
        localConfig = LocalConfigParser.buildConfig("GrCPU", false, "{}", serializer)
        assertNull(localConfig.sdkConfig.betaFeaturesEnabled)
    }

    @Test
    fun testSigHandlerDetectionOnlyConfig() {
        // disabled explicitly
        var localConfig =
            LocalConfigParser.buildConfig("GrCPU", false, "{\"sig_handler_detection\": false}", serializer)
        assertFalse(checkNotNull(localConfig.sdkConfig.sigHandlerDetection))

        // enabled explicitly
        localConfig = LocalConfigParser.buildConfig(
            "GrCPU",
            false,
            "{\"sig_handler_detection\": true}",
            serializer
        )
        assertTrue(checkNotNull(localConfig.sdkConfig.sigHandlerDetection))

        // enabled by default
        localConfig = LocalConfigParser.buildConfig("GrCPU", false, "{}", serializer)
        assertNull(localConfig.sdkConfig.sigHandlerDetection)
    }

    @Test
    fun testBaseUrlOnlyConfig() {
        var localConfig = LocalConfigParser.buildConfig(
            "GrCPU",
            false,
            "{\"base_urls\": {\"config\": \"custom_config\"}}",
            serializer
        )
        assertEquals(localConfig.sdkConfig.baseUrls?.config, "custom_config")
        localConfig =
            LocalConfigParser.buildConfig(
                "GrCPU",
                false,
                "{\"base_urls\": {\"data\": \"custom_data\"}}",
                serializer
            )
        assertEquals(localConfig.sdkConfig.baseUrls?.data, "custom_data")
        localConfig = LocalConfigParser.buildConfig(
            "GrCPU",
            false,
            "{\"base_urls\": {\"data_dev\": \"custom_data_dev\"}}",
            serializer
        )
        assertEquals(
            localConfig.sdkConfig.baseUrls?.dataDev,
            "custom_data_dev"
        )
        localConfig = LocalConfigParser.buildConfig(
            "GrCPU",
            false,
            "{\"base_urls\": {\"images\": \"custom_images\"}}",
            serializer
        )
        assertEquals(localConfig.sdkConfig.baseUrls?.images, "custom_images")
    }

    @Test
    fun testViewConfigOnlyConfig() {
        var localConfig = LocalConfigParser.buildConfig("GrCPU", false, "{}", serializer)
        assertNull(
            localConfig.sdkConfig.viewConfig?.enableAutomaticActivityCapture,
        )
        localConfig = LocalConfigParser.buildConfig(
            "GrCPU",
            false,
            "{\"view_config\":{\"enable_automatic_activity_capture\":false}}",
            serializer
        )
        assertFalse(checkNotNull(localConfig.sdkConfig.viewConfig?.enableAutomaticActivityCapture))
    }

    @Test
    fun testCrashHandlerOnlyConfig() {
        var localConfig =
            LocalConfigParser.buildConfig(
                "GrCPU",
                false,
                "{\"crash_handler\": {\"enabled\": false}}",
                serializer
            )
        assertFalse(checkNotNull(localConfig.sdkConfig.crashHandler?.enabled))
        localConfig =
            LocalConfigParser.buildConfig(
                "GrCPU",
                false,
                "{\"crash_handler\": {\"ndk_enabled\": false}}",
                serializer
            )
        assertFalse(localConfig.ndkEnabled)
    }

    @Suppress("DEPRECATION")
    @Test
    fun testSessionOnlyConfig() {
        var localConfig =
            LocalConfigParser.buildConfig(
                "GrCPU",
                false,
                "{\"session\": {\"max_session_seconds\": 60}}",
                serializer
            )
        assertEquals(
            localConfig.sdkConfig.sessionConfig?.maxSessionSeconds,
            60
        )

        // ignore max_session_seconds when it is too small
        localConfig =
            LocalConfigParser.buildConfig(
                "GrCPU",
                false,
                "{\"session\": {\"max_session_seconds\": 59}}",
                serializer
            )
        assertEquals(
            59,
            localConfig.sdkConfig.sessionConfig?.maxSessionSeconds,
        )

        // max_session_seconds can be null
        localConfig = LocalConfigParser.buildConfig(
            "GrCPU",
            false,
            "{\"session\": {\"max_session_seconds\": null}}",
            serializer
        )
        assertNull(
            localConfig.sdkConfig.sessionConfig?.maxSessionSeconds,
        )

        // ignore max_session_seconds when it is too small
        localConfig =
            LocalConfigParser.buildConfig("GrCPU", false, "{\"session\": {\"async_end\": true}}", serializer)
        assertTrue(checkNotNull(localConfig.sdkConfig.sessionConfig?.asyncEnd))

        // error_log_strict_mode is true
        localConfig = LocalConfigParser.buildConfig(
            "GrCPU",
            false,
            "{\"session\": {\"error_log_strict_mode\": true}}",
            serializer
        )
        assertTrue(
            checkNotNull(localConfig.sdkConfig.sessionConfig?.sessionEnableErrorLogStrictMode)
        )

        // receive a session component to restrict session messages
        localConfig = LocalConfigParser.buildConfig(
            "GrCPU",
            false,
            "{\"session\": {\"components\": [\"breadcrumbs_taps\"]}}",
            serializer
        )
        assertTrue(
            checkNotNull(localConfig.sdkConfig.sessionConfig?.sessionComponents)
                .contains("breadcrumbs_taps")
        )

        // full session for component list is empty
        localConfig =
            LocalConfigParser.buildConfig(
                "GrCPU",
                false,
                "{\"session\": {\"send_full_for\": []}}",
                serializer
            )
        assertTrue(
            checkNotNull(localConfig.sdkConfig.sessionConfig?.fullSessionEvents).isEmpty()
        )

        // receive a full session for component to restrict session messages
        localConfig = LocalConfigParser.buildConfig(
            "GrCPU",
            false,
            "{\"session\": {\"send_full_for\": [\"crashes\"]}}",
            serializer
        )
        val sessionConfig = localConfig.sdkConfig.sessionConfig
        assertFalse(
            checkNotNull(sessionConfig?.fullSessionEvents?.isEmpty())
        )
        assertTrue(
            checkNotNull(sessionConfig?.fullSessionEvents?.contains("crashes"))
        )
    }

    @Test
    fun testStartupMomentOnlyConfig() {
        val localConfig = LocalConfigParser.buildConfig(
            "GrCPU",
            false,
            "{\"startup_moment\": {\"automatically_end\": false}}",
            serializer
        )
        assertFalse(checkNotNull(localConfig.sdkConfig.startupMoment?.automaticallyEnd))
    }

    @Test
    fun testTapsOnlyConfig() {
        val localConfig =
            LocalConfigParser.buildConfig(
                "GrCPU",
                false,
                "{\"taps\": {\"capture_coordinates\": false}}",
                serializer
            )
        assertFalse(checkNotNull(localConfig.sdkConfig.taps?.captureCoordinates))
    }

    @Test
    fun testNetworkingOnlyConfig() {
        var localConfig = LocalConfigParser.buildConfig(
            "GrCPU",
            false,
            "{\"networking\": {\"capture_request_content_length\": true}}",
            serializer
        )
        assertTrue(
            checkNotNull(localConfig.sdkConfig.networking?.captureRequestContentLength)
        )

        localConfig = LocalConfigParser.buildConfig(
            "GrCPU",
            false,
            "{\"networking\": {\"enable_native_monitoring\": true}}",
            serializer
        )
        assertTrue(checkNotNull(localConfig.sdkConfig.networking?.enableNativeMonitoring))
        localConfig = LocalConfigParser.buildConfig(
            "GrCPU",
            false,
            "{\"networking\": {\"trace_id_header\": \"custom-value\"}}",
            serializer
        )
        assertEquals(
            checkNotNull(localConfig.sdkConfig.networking?.traceIdHeader),
            "custom-value"
        )
        localConfig = LocalConfigParser.buildConfig(
            "GrCPU",
            false,
            "{\"networking\": {\"disabled_url_patterns\": [\"a.b.c\", \"https://example.com\", \"https://example2.com/foo/123/bar\"]}}",
            serializer
        )
        assertEquals(
            3,
            localConfig.sdkConfig.networking?.disabledUrlPatterns?.size
        )
    }

    @Test
    fun testWebviewCaptureOnlyConfig() {
        var localConfig = LocalConfigParser.buildConfig(
            "GrCPU",
            false,
            "{\"webview\": {\"enable\": false}}",
            serializer
        )
        assertFalse(checkNotNull(localConfig.sdkConfig.webViewConfig?.captureWebViews))
        localConfig = LocalConfigParser.buildConfig(
            "GrCPU",
            false,
            "{\"webview\": {\"capture_query_params\": false}}",
            serializer
        )
        assertFalse(checkNotNull(localConfig.sdkConfig.webViewConfig?.captureQueryParams))
    }

    @Test
    fun testBackgroundActivityConfig() {
        var localConfig = LocalConfigParser.buildConfig(
            "GrCPU",
            false,
            "{\"background_activity\": {}}",
            serializer
        )
        var backgroundActivityCfg = checkNotNull(localConfig.sdkConfig.backgroundActivityConfig)
        assertNull(
            backgroundActivityCfg.backgroundActivityCaptureEnabled
        )
        assertNull(
            backgroundActivityCfg.manualBackgroundActivityLimit
        )
        assertNull(
            backgroundActivityCfg.minBackgroundActivityDuration
        )
        assertNull(
            backgroundActivityCfg.maxCachedActivities
        )
        localConfig = LocalConfigParser.buildConfig(
            "GrCPU",
            false,
            "{\"background_activity\": {\"capture_enabled\": true}}",
            serializer
        )
        backgroundActivityCfg = checkNotNull(localConfig.sdkConfig.backgroundActivityConfig)

        assertTrue(
            checkNotNull(backgroundActivityCfg.backgroundActivityCaptureEnabled)
        )
        localConfig = LocalConfigParser.buildConfig(
            "GrCPU",
            false,
            "{\"background_activity\": {\"capture_enabled\": true, \"manual_background_activity_limit\": 50}}",
            serializer
        )
        backgroundActivityCfg = checkNotNull(localConfig.sdkConfig.backgroundActivityConfig)
        assertTrue(
            checkNotNull(backgroundActivityCfg.backgroundActivityCaptureEnabled)
        )
        assertEquals(
            50,
            backgroundActivityCfg.manualBackgroundActivityLimit
        )
        localConfig = LocalConfigParser.buildConfig(
            "GrCPU",
            false,
            "{\"background_activity\": {\"capture_enabled\": true, \"min_background_activity_duration\": 300}}",
            serializer
        )
        backgroundActivityCfg = checkNotNull(localConfig.sdkConfig.backgroundActivityConfig)
        assertTrue(
            checkNotNull(backgroundActivityCfg.backgroundActivityCaptureEnabled)
        )
        assertEquals(
            300L,
            backgroundActivityCfg.minBackgroundActivityDuration
        )
        localConfig = LocalConfigParser.buildConfig(
            "GrCPU",
            false,
            "{\"background_activity\": {\"capture_enabled\": true, \"max_cached_activities\": 50}}",
            serializer
        )
        backgroundActivityCfg = checkNotNull(localConfig.sdkConfig.backgroundActivityConfig)
        assertTrue(
            checkNotNull(backgroundActivityCfg.backgroundActivityCaptureEnabled)
        )
        assertEquals(
            50,
            backgroundActivityCfg.maxCachedActivities
        )
    }

    @Test
    fun testComposeConfig() {
        var localConfig = LocalConfigParser.buildConfig("GrCPU", false, "{}", serializer)
        assertNull(
            localConfig.sdkConfig.composeConfig?.captureComposeOnClick,
        )
        localConfig = LocalConfigParser.buildConfig(
            "GrCPU",
            false,
            "{\"compose\":{\"capture_compose_onclick\":false}}",
            serializer
        )
        assertFalse(checkNotNull(localConfig.sdkConfig.composeConfig?.captureComposeOnClick))
    }

    @Test
    fun testServiceEnablementMemoryServiceConfig() {
        var localConfig = LocalConfigParser.buildConfig(
            "GrCPU", false,
            "{\"automatic_data_capture\": { \"memory_info\": false}}",
            serializer
        )
        var cfg = checkNotNull(localConfig.sdkConfig.automaticDataCaptureConfig)
        assertFalse(
            checkNotNull(cfg.memoryServiceEnabled)
        )
        localConfig = LocalConfigParser.buildConfig(
            "GrCPU", false,
            "{\"automatic_data_capture\": { \"memory_info\": true}}",
            serializer
        )
        cfg = checkNotNull(localConfig.sdkConfig.automaticDataCaptureConfig)
        assertTrue(
            checkNotNull(cfg.memoryServiceEnabled)
        )
    }

    @Test
    fun testServiceEnablementPowerSaveModeServiceConfig() {
        var localConfig = LocalConfigParser.buildConfig(
            "GrCPU", false,
            "{\"automatic_data_capture\": { \"power_save_mode_info\": false}}",
            serializer
        )
        var cfg = checkNotNull(localConfig.sdkConfig.automaticDataCaptureConfig)
        assertFalse(
            checkNotNull(cfg.powerSaveModeServiceEnabled)
        )
        localConfig = LocalConfigParser.buildConfig(
            "GrCPU", false,
            "{\"automatic_data_capture\": { \"power_save_mode_info\": true}}",
            serializer
        )
        cfg = checkNotNull(localConfig.sdkConfig.automaticDataCaptureConfig)
        assertTrue(
            checkNotNull(cfg.powerSaveModeServiceEnabled)
        )
    }

    @Test
    fun testServiceEnablementNetworkConnectivityServiceConfig() {
        var localConfig = LocalConfigParser.buildConfig(
            "GrCPU", false,
            "{\"automatic_data_capture\": { \"network_connectivity_info\": false}}",
            serializer
        )
        var cfg = checkNotNull(localConfig.sdkConfig.automaticDataCaptureConfig)

        assertFalse(
            checkNotNull(cfg.networkConnectivityServiceEnabled)
        )
        localConfig = LocalConfigParser.buildConfig(
            "GrCPU", false,
            "{\"automatic_data_capture\": { \"network_connectivity_info\": true}}",
            serializer
        )
        cfg = checkNotNull(localConfig.sdkConfig.automaticDataCaptureConfig)

        assertTrue(
            checkNotNull(cfg.networkConnectivityServiceEnabled)
        )
    }

    @Test
    fun testServiceEnablementAnrServiceConfig() {
        var localConfig = LocalConfigParser.buildConfig(
            "GrCPU", false,
            "{\"automatic_data_capture\": { \"anr_info\": false}}",
            serializer
        )
        var cfg = checkNotNull(localConfig.sdkConfig.automaticDataCaptureConfig)
        assertFalse(
            checkNotNull(cfg.anrServiceEnabled)
        )
        localConfig = LocalConfigParser.buildConfig(
            "GrCPU", false,
            "{\"automatic_data_capture\": { \"anr_info\": true}}",
            serializer
        )
        cfg = checkNotNull(localConfig.sdkConfig.automaticDataCaptureConfig)
        assertTrue(
            checkNotNull(cfg.anrServiceEnabled)
        )
    }
}
