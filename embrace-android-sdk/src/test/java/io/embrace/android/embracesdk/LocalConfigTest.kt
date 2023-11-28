package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class LocalConfigTest {

    @Test
    fun testEmptyConfig() {
        val localConfig = LocalConfig.buildConfig("GrCPU", false, null, EmbraceSerializer())
        assertNotNull(localConfig)
    }

    @Test
    fun testAppOnlyConfig() {
        val localConfig =
            LocalConfig.buildConfig(
                "GrCPU",
                false,
                "{\"app\": {\"report_disk_usage\": false}}",
                EmbraceSerializer()
            )
        assertFalse(checkNotNull(localConfig.sdkConfig.app?.reportDiskUsage))
    }

    @Test
    fun testBetaFunctionalityOnlyConfig() {
        // disabled explicitly
        var localConfig =
            LocalConfig.buildConfig("GrCPU", false, "{\"beta_features_enabled\": false}", EmbraceSerializer())
        assertFalse(checkNotNull(localConfig.sdkConfig.betaFeaturesEnabled))

        // enabled explicitly
        localConfig = LocalConfig.buildConfig(
            "GrCPU",
            false,
            "{\"beta_features_enabled\": true}",
            EmbraceSerializer()
        )
        assertTrue(checkNotNull(localConfig.sdkConfig.betaFeaturesEnabled))

        // enabled by default
        localConfig = LocalConfig.buildConfig("GrCPU", false, "{}", EmbraceSerializer())
        assertNull(localConfig.sdkConfig.betaFeaturesEnabled)
    }

    @Test
    fun testSigHandlerDetectionOnlyConfig() {
        // disabled explicitly
        var localConfig =
            LocalConfig.buildConfig("GrCPU", false, "{\"sig_handler_detection\": false}", EmbraceSerializer())
        assertFalse(checkNotNull(localConfig.sdkConfig.sigHandlerDetection))

        // enabled explicitly
        localConfig = LocalConfig.buildConfig(
            "GrCPU",
            false,
            "{\"sig_handler_detection\": true}",
            EmbraceSerializer()
        )
        assertTrue(checkNotNull(localConfig.sdkConfig.sigHandlerDetection))

        // enabled by default
        localConfig = LocalConfig.buildConfig("GrCPU", false, "{}", EmbraceSerializer())
        assertNull(localConfig.sdkConfig.sigHandlerDetection)
    }

    @Test
    fun testBaseUrlOnlyConfig() {
        var localConfig = LocalConfig.buildConfig(
            "GrCPU",
            false,
            "{\"base_urls\": {\"config\": \"custom_config\"}}",
            EmbraceSerializer()
        )
        assertEquals(localConfig.sdkConfig.baseUrls?.config, "custom_config")
        localConfig =
            LocalConfig.buildConfig(
                "GrCPU",
                false,
                "{\"base_urls\": {\"data\": \"custom_data\"}}",
                EmbraceSerializer()
            )
        assertEquals(localConfig.sdkConfig.baseUrls?.data, "custom_data")
        localConfig = LocalConfig.buildConfig(
            "GrCPU",
            false,
            "{\"base_urls\": {\"data_dev\": \"custom_data_dev\"}}",
            EmbraceSerializer()
        )
        assertEquals(
            localConfig.sdkConfig.baseUrls?.dataDev,
            "custom_data_dev"
        )
        localConfig = LocalConfig.buildConfig(
            "GrCPU",
            false,
            "{\"base_urls\": {\"images\": \"custom_images\"}}",
            EmbraceSerializer()
        )
        assertEquals(localConfig.sdkConfig.baseUrls?.images, "custom_images")
    }

    @Test
    fun testViewConfigOnlyConfig() {
        var localConfig = LocalConfig.buildConfig("GrCPU", false, "{}", EmbraceSerializer())
        assertNull(
            localConfig.sdkConfig.viewConfig?.enableAutomaticActivityCapture,
        )
        localConfig = LocalConfig.buildConfig(
            "GrCPU",
            false,
            "{\"view_config\":{\"enable_automatic_activity_capture\":false}}",
            EmbraceSerializer()
        )
        assertFalse(checkNotNull(localConfig.sdkConfig.viewConfig?.enableAutomaticActivityCapture))
    }

    @Test
    fun testCrashHandlerOnlyConfig() {
        var localConfig =
            LocalConfig.buildConfig(
                "GrCPU",
                false,
                "{\"crash_handler\": {\"enabled\": false}}",
                EmbraceSerializer()
            )
        assertFalse(checkNotNull(localConfig.sdkConfig.crashHandler?.enabled))
        localConfig =
            LocalConfig.buildConfig(
                "GrCPU",
                false,
                "{\"crash_handler\": {\"ndk_enabled\": false}}",
                EmbraceSerializer()
            )
        assertFalse(localConfig.ndkEnabled)
    }

    @Suppress("DEPRECATION")
    @Test
    fun testSessionOnlyConfig() {
        var localConfig =
            LocalConfig.buildConfig(
                "GrCPU",
                false,
                "{\"session\": {\"max_session_seconds\": 60}}",
                EmbraceSerializer()
            )
        assertEquals(
            localConfig.sdkConfig.sessionConfig?.maxSessionSeconds,
            60
        )

        // ignore max_session_seconds when it is too small
        localConfig =
            LocalConfig.buildConfig(
                "GrCPU",
                false,
                "{\"session\": {\"max_session_seconds\": 59}}",
                EmbraceSerializer()
            )
        assertEquals(
            59,
            localConfig.sdkConfig.sessionConfig?.maxSessionSeconds,
        )

        // max_session_seconds can be null
        localConfig = LocalConfig.buildConfig(
            "GrCPU",
            false,
            "{\"session\": {\"max_session_seconds\": null}}",
            EmbraceSerializer()
        )
        assertNull(
            localConfig.sdkConfig.sessionConfig?.maxSessionSeconds,
        )

        // ignore max_session_seconds when it is too small
        localConfig =
            LocalConfig.buildConfig("GrCPU", false, "{\"session\": {\"async_end\": true}}", EmbraceSerializer())
        assertTrue(checkNotNull(localConfig.sdkConfig.sessionConfig?.asyncEnd))

        // error_log_strict_mode is true
        localConfig = LocalConfig.buildConfig(
            "GrCPU",
            false,
            "{\"session\": {\"error_log_strict_mode\": true}}",
            EmbraceSerializer()
        )
        assertTrue(
            checkNotNull(localConfig.sdkConfig.sessionConfig?.sessionEnableErrorLogStrictMode)
        )

        // receive a session component to restrict session messages
        localConfig = LocalConfig.buildConfig(
            "GrCPU",
            false,
            "{\"session\": {\"components\": [\"breadcrumbs_taps\"]}}",
            EmbraceSerializer()
        )
        assertTrue(
            checkNotNull(localConfig.sdkConfig.sessionConfig?.sessionComponents)
                .contains("breadcrumbs_taps")
        )

        // full session for component list is empty
        localConfig =
            LocalConfig.buildConfig(
                "GrCPU",
                false,
                "{\"session\": {\"send_full_for\": []}}",
                EmbraceSerializer()
            )
        assertTrue(
            checkNotNull(localConfig.sdkConfig.sessionConfig?.fullSessionEvents).isEmpty()
        )

        // receive a full session for component to restrict session messages
        localConfig = LocalConfig.buildConfig(
            "GrCPU",
            false,
            "{\"session\": {\"send_full_for\": [\"crashes\"]}}",
            EmbraceSerializer()
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
        val localConfig = LocalConfig.buildConfig(
            "GrCPU",
            false,
            "{\"startup_moment\": {\"automatically_end\": false}}",
            EmbraceSerializer()
        )
        assertFalse(checkNotNull(localConfig.sdkConfig.startupMoment?.automaticallyEnd))
    }

    @Test
    fun testTapsOnlyConfig() {
        val localConfig =
            LocalConfig.buildConfig(
                "GrCPU",
                false,
                "{\"taps\": {\"capture_coordinates\": false}}",
                EmbraceSerializer()
            )
        assertFalse(checkNotNull(localConfig.sdkConfig.taps?.captureCoordinates))
    }

    @Test
    fun testNetworkingOnlyConfig() {
        var localConfig = LocalConfig.buildConfig(
            "GrCPU",
            false,
            "{\"networking\": {\"capture_request_content_length\": true}}",
            EmbraceSerializer()
        )
        assertTrue(
            checkNotNull(localConfig.sdkConfig.networking?.captureRequestContentLength)
        )

        localConfig = LocalConfig.buildConfig(
            "GrCPU",
            false,
            "{\"networking\": {\"enable_native_monitoring\": true}}",
            EmbraceSerializer()
        )
        assertTrue(checkNotNull(localConfig.sdkConfig.networking?.enableNativeMonitoring))
        localConfig = LocalConfig.buildConfig(
            "GrCPU",
            false,
            "{\"networking\": {\"trace_id_header\": \"custom-value\"}}",
            EmbraceSerializer()
        )
        assertEquals(
            checkNotNull(localConfig.sdkConfig.networking?.traceIdHeader),
            "custom-value"
        )
        localConfig = LocalConfig.buildConfig(
            "GrCPU",
            false,
            "{\"networking\": {\"disabled_url_patterns\": [\"a.b.c\", \"https://example.com\", \"https://example2.com/foo/123/bar\"]}}",
            EmbraceSerializer()
        )
        assertEquals(
            3,
            localConfig.sdkConfig.networking?.disabledUrlPatterns?.size
        )
    }

    @Test
    fun testWebviewCaptureOnlyConfig() {
        var localConfig = LocalConfig.buildConfig(
            "GrCPU",
            false,
            "{\"webview\": {\"enable\": false}}",
            EmbraceSerializer()
        )
        assertFalse(checkNotNull(localConfig.sdkConfig.webViewConfig?.captureWebViews))
        localConfig = LocalConfig.buildConfig(
            "GrCPU",
            false,
            "{\"webview\": {\"capture_query_params\": false}}",
            EmbraceSerializer()
        )
        assertFalse(checkNotNull(localConfig.sdkConfig.webViewConfig?.captureQueryParams))
    }

    @Test
    fun testBackgroundActivityConfig() {
        var localConfig = LocalConfig.buildConfig(
            "GrCPU",
            false,
            "{\"background_activity\": {}}",
            EmbraceSerializer()
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
        localConfig = LocalConfig.buildConfig(
            "GrCPU",
            false,
            "{\"background_activity\": {\"capture_enabled\": true}}",
            EmbraceSerializer()
        )
        backgroundActivityCfg = checkNotNull(localConfig.sdkConfig.backgroundActivityConfig)

        assertTrue(
            checkNotNull(backgroundActivityCfg.backgroundActivityCaptureEnabled)
        )
        localConfig = LocalConfig.buildConfig(
            "GrCPU",
            false,
            "{\"background_activity\": {\"capture_enabled\": true, \"manual_background_activity_limit\": 50}}",
            EmbraceSerializer()
        )
        backgroundActivityCfg = checkNotNull(localConfig.sdkConfig.backgroundActivityConfig)
        assertTrue(
            checkNotNull(backgroundActivityCfg.backgroundActivityCaptureEnabled)
        )
        assertEquals(
            50,
            backgroundActivityCfg.manualBackgroundActivityLimit
        )
        localConfig = LocalConfig.buildConfig(
            "GrCPU",
            false,
            "{\"background_activity\": {\"capture_enabled\": true, \"min_background_activity_duration\": 300}}",
            EmbraceSerializer()
        )
        backgroundActivityCfg = checkNotNull(localConfig.sdkConfig.backgroundActivityConfig)
        assertTrue(
            checkNotNull(backgroundActivityCfg.backgroundActivityCaptureEnabled)
        )
        assertEquals(
            300L,
            backgroundActivityCfg.minBackgroundActivityDuration
        )
        localConfig = LocalConfig.buildConfig(
            "GrCPU",
            false,
            "{\"background_activity\": {\"capture_enabled\": true, \"max_cached_activities\": 50}}",
            EmbraceSerializer()
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
        var localConfig = LocalConfig.buildConfig("GrCPU", false, "{}", EmbraceSerializer())
        assertNull(
            localConfig.sdkConfig.composeConfig?.captureComposeOnClick,
        )
        localConfig = LocalConfig.buildConfig(
            "GrCPU",
            false,
            "{\"compose\":{\"capture_compose_onclick\":false}}",
            EmbraceSerializer()
        )
        assertFalse(checkNotNull(localConfig.sdkConfig.composeConfig?.captureComposeOnClick))
    }

    @Test
    fun testServiceEnablementMemoryServiceConfig() {
        var localConfig = LocalConfig.buildConfig(
            "GrCPU", false,
            "{\"automatic_data_capture\": { \"memory_info\": false}}",
            EmbraceSerializer()
        )
        var cfg = checkNotNull(localConfig.sdkConfig.automaticDataCaptureConfig)
        assertFalse(
            checkNotNull(cfg.memoryServiceEnabled)
        )
        localConfig = LocalConfig.buildConfig(
            "GrCPU", false,
            "{\"automatic_data_capture\": { \"memory_info\": true}}",
            EmbraceSerializer()
        )
        cfg = checkNotNull(localConfig.sdkConfig.automaticDataCaptureConfig)
        assertTrue(
            checkNotNull(cfg.memoryServiceEnabled)
        )
    }

    @Test
    fun testServiceEnablementPowerSaveModeServiceConfig() {
        var localConfig = LocalConfig.buildConfig(
            "GrCPU", false,
            "{\"automatic_data_capture\": { \"power_save_mode_info\": false}}",
            EmbraceSerializer()
        )
        var cfg = checkNotNull(localConfig.sdkConfig.automaticDataCaptureConfig)
        assertFalse(
            checkNotNull(cfg.powerSaveModeServiceEnabled)
        )
        localConfig = LocalConfig.buildConfig(
            "GrCPU", false,
            "{\"automatic_data_capture\": { \"power_save_mode_info\": true}}",
            EmbraceSerializer()
        )
        cfg = checkNotNull(localConfig.sdkConfig.automaticDataCaptureConfig)
        assertTrue(
            checkNotNull(cfg.powerSaveModeServiceEnabled)
        )
    }

    @Test
    fun testServiceEnablementNetworkConnectivityServiceConfig() {
        var localConfig = LocalConfig.buildConfig(
            "GrCPU", false,
            "{\"automatic_data_capture\": { \"network_connectivity_info\": false}}",
            EmbraceSerializer()
        )
        var cfg = checkNotNull(localConfig.sdkConfig.automaticDataCaptureConfig)

        assertFalse(
            checkNotNull(cfg.networkConnectivityServiceEnabled)
        )
        localConfig = LocalConfig.buildConfig(
            "GrCPU", false,
            "{\"automatic_data_capture\": { \"network_connectivity_info\": true}}",
            EmbraceSerializer()
        )
        cfg = checkNotNull(localConfig.sdkConfig.automaticDataCaptureConfig)

        assertTrue(
            checkNotNull(cfg.networkConnectivityServiceEnabled)
        )
    }

    @Test
    fun testServiceEnablementAnrServiceConfig() {
        var localConfig = LocalConfig.buildConfig(
            "GrCPU", false,
            "{\"automatic_data_capture\": { \"anr_info\": false}}",
            EmbraceSerializer()
        )
        var cfg = checkNotNull(localConfig.sdkConfig.automaticDataCaptureConfig)
        assertFalse(
            checkNotNull(cfg.anrServiceEnabled)
        )
        localConfig = LocalConfig.buildConfig(
            "GrCPU", false,
            "{\"automatic_data_capture\": { \"anr_info\": true}}",
            EmbraceSerializer()
        )
        cfg = checkNotNull(localConfig.sdkConfig.automaticDataCaptureConfig)
        assertTrue(
            checkNotNull(cfg.anrServiceEnabled)
        )
    }
}
