package io.embrace.android.embracesdk.internal.config.resolved

import io.embrace.android.embracesdk.fakes.config.FakeBase64SharedObjectFilesMap
import io.embrace.android.embracesdk.fakes.config.FakeBaseUrlConfig
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeNetworkCaptureConfig
import io.embrace.android.embracesdk.fakes.config.FakeProjectConfig
import io.embrace.android.embracesdk.fakes.config.FakeRedactionConfig
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.WebViewFragmentCapture
import io.embrace.android.embracesdk.internal.config.remote.AppExitInfoConfig
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.DataRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.KillSwitchRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.LogRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.NetworkRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.NetworkSpanForwardingRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.OtelKotlinSdkConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.ThreadBlockageRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.UiRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.UserSessionRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.VitalsRemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Asserts how every option in config-schema/embrace-config.yaml resolves. The expected values are written out here
 * rather than read from the schema, so that a change to a default, a key or a precedence rule fails this test.
 */
internal class EmbraceConfigTest {

    private val rule = NetworkCaptureRuleRemoteConfig(id = "rule", duration = null, method = "GET", urlRegex = "x")

    @Test
    fun `defaults are used without any config`() {
        assertDefaults(EmbraceConfig())
        assertDefaults(resolveConfig(InstrumentedConfigImpl, null, unreadBucket))
        assertDefaults(resolveConfig(InstrumentedConfigImpl, RemoteConfig(), unreadBucket))
    }

    @Test
    fun `remote values take precedence`() {
        with(resolveConfig(InstrumentedConfigImpl, remote(), unreadBucket)) {
            with(breadcrumb) {
                assertEquals(1, customLimit)
                assertEquals(2, tapLimit)
                assertEquals(3, webViewLimit)
                assertEquals(4, fragmentLimit)
            }
            assertTrue(persistence.multiFileEnabled)
            with(aei) {
                assertEquals(99, traceMaxLimit)
                assertFalse(captureEnabled)
                assertEquals(3, maxNum)
            }
            with(autoDataCapture) {
                assertFalse(thermalStatusCaptureEnabled)
                assertTrue(composeClickCaptureEnabled)
                assertTrue(thirdPartySigHandlerDetectionEnabled)
                assertFalse(uiLoadTracingEnabled)
                assertFalse(uiLoadTracingTraceAll)
                assertFalse(stateCaptureEnabled)
                assertTrue(networkCallbackConnectivityServiceEnabled)
                assertFalse(navigationStateCaptureEnabled)
                assertTrue(smoothnessCaptureEnabled)
                assertTrue(screenLoadCaptureEnabled)
                assertTrue(activityProcessLifecycleTrackerEnabled)
                assertTrue(activityLeakDetectionEnabled)
                assertTrue(fragmentLeakDetectionEnabled)
                assertTrue(webViewLeakDetectionEnabled)
            }
            assertTrue(backgroundActivity.captureEnabled)
            with(dataCaptureEvent) {
                assertFalse(internalExceptionCaptureEnabled)
                assertEquals(setOf("event"), disabledEventAndLogPatterns)
            }
            with(experiment) {
                assertEquals(600, experimentCountLimit)
                assertEquals(200, idLengthLimit)
                assertEquals(300, variantLengthLimit)
            }
            with(logMessage) {
                assertEquals(10, maxLength)
                assertEquals(11, infoLimit)
                assertEquals(12, warnLimit)
                assertEquals(13, errorLimit)
            }
            with(network) {
                assertEquals(20, requestLimitPerDomain)
                assertEquals(mapOf("small.com" to 5, "large.com" to 20), limitsByDomain)
                assertEquals(1234L, requestSpanTimeoutMs)
                assertEquals(setOf("url"), disabledUrlPatterns)
                assertEquals(setOf(rule), networkCaptureRules)
            }
            assertTrue(networkSpanForwarding.enabled)
            with(otel) {
                assertTrue(kotlinSdkEnabled)
                assertEquals(1, maxCustomSpansPerSessionPart)
                assertEquals(2, maxInternalSpansPerSessionPart)
                assertEquals(3, maxNetworkSpansPerSessionPart)
                assertEquals(4, maxSpanEventsPerSessionPart)
                assertEquals(5000L, periodicCacheIntervalMs)
            }
            assertFalse(sdkMode.enabled)
            with(threadBlockage) {
                assertFalse(captureEnabled)
                assertEquals(5L, sampleIntervalMs)
                assertEquals(6, maxStacktracesPerInterval)
                assertEquals(7, stacktraceFrameLimit)
                assertEquals(8, maxIntervalsPerSession)
                assertEquals(9, minDurationMs)
            }
            with(traceparentInjection) {
                assertTrue(enabled)
                assertFalse(legacyFallbackEnabled)
            }
            with(userSession) {
                assertTrue(sessionControlEnabled)
                assertEquals(150, sessionPropertyLimit)
                assertEquals(7200, maxDurationSeconds)
                assertEquals(600, inactivityTimeoutSeconds)
            }
            with(vitals) {
                assertEquals(1L, smoothnessIdleThresholdMs)
                assertEquals(2L, smoothnessHeldIdleThresholdMs)
                assertEquals(3.0, jankHeuristicMultiplier, 0.0)
                assertEquals(4L, screenLoadIdleThresholdMs)
                assertEquals(5L, screenLoadTimeoutMs)
                assertEquals(6L, screenLoadNavTimeoutMs)
                assertTrue(smoothnessFrameTraceEnabled)
                assertEquals(7, spanLimit)
            }
        }
    }

    @Test
    fun `local values are used without remote config`() {
        with(resolveConfig(local(), null, unreadBucket)) {
            with(breadcrumb) {
                assertTrue(captureViewClickCoordinates)
                assertFalse(captureActivities)
                assertFalse(captureWebViews)
                assertFalse(captureWebViewQueryParams)
                assertEquals(WebViewFragmentCapture.REMOVE, webViewFragmentCapture)
                assertTrue(captureFcmPiiData)
            }
            assertTrue(persistence.multiFileEnabled)
            assertFalse(aei.captureEnabled)
            with(autoDataCapture) {
                assertFalse(powerSaveModeCaptureEnabled)
                assertFalse(networkConnectivityCaptureEnabled)
                assertFalse(threadBlockageCaptureEnabled)
                assertFalse(jvmCrashCaptureEnabled)
                assertTrue(composeClickCaptureEnabled)
                assertTrue(thirdPartySigHandlerDetectionEnabled)
                assertFalse(nativeCrashCaptureEnabled)
                assertFalse(diskUsageCaptureEnabled)
                assertFalse(uiLoadTracingEnabled)
                assertFalse(uiLoadTracingTraceAll)
                assertTrue(endStartupWithAppReadyEnabled)
                assertTrue(activityProcessLifecycleTrackerEnabled)
            }
            assertTrue(backgroundActivity.captureEnabled)
            with(network) {
                assertTrue(requestContentLengthCaptureEnabled)
                assertTrue(okHttpResponseBodySizeCaptureEnabled)
                assertTrue(httpUrlConnectionCaptureEnabled)
                assertFalse(hucLiteInstrumentationEnabled)
                assertEquals(10, requestLimitPerDomain)
                assertEquals(mapOf("local.com" to 10), limitsByDomain)
                assertEquals(setOf("pattern"), disabledUrlPatterns)
                assertEquals("key", networkBodyCapturePublicKey)
            }
            assertTrue(networkSpanForwarding.enabled)
            assertTrue(otel.kotlinSdkEnabled)
            assertEquals(listOf("secret"), sensitiveKeys.denylist)
            with(traceparentInjection) {
                assertTrue(enabled)
                assertEquals(listOf(".allowed.com"), onlyAllowDomains)
            }
            with(app) {
                assertEquals("12345", appId)
                assertEquals("unity", appFramework)
                assertEquals("build", buildId)
                assertEquals("type", buildType)
                assertEquals("flavor", buildFlavor)
                assertEquals("com.example", packageName)
            }
            with(baseUrls) {
                assertEquals("https://config", config)
                assertEquals("https://data", data)
            }
            assertEquals("symbols", symbols.base64SharedObjectFilesMap)
        }
    }

    @Test
    fun `remote percentages are compared against the bucket`() {
        val remote = RemoteConfig(
            pctMultiFilePersistenceEnabled = 50f,
            threshold = 50,
            threadBlockageRemoteConfig = ThreadBlockageRemoteConfig(pctEnabled = 50),
            appExitInfoConfig = AppExitInfoConfig(pctAeiCaptureEnabled = 50f),
        )
        with(resolveConfig(InstrumentedConfigImpl, remote, lazy { 49f })) {
            assertTrue(persistence.multiFileEnabled)
            assertTrue(sdkMode.enabled)
            assertTrue(threadBlockage.captureEnabled)
            assertTrue(aei.captureEnabled)
        }
        with(resolveConfig(InstrumentedConfigImpl, remote, lazy { 51f })) {
            assertFalse(persistence.multiFileEnabled)
            assertFalse(sdkMode.enabled)
            assertFalse(threadBlockage.captureEnabled)
            assertFalse(aei.captureEnabled)
        }
    }

    @Test
    fun `remote rollouts override local values`() {
        val enabledRemotely = RemoteConfig(
            pctMultiFilePersistenceEnabled = 100f,
            appExitInfoConfig = AppExitInfoConfig(pctAeiCaptureEnabled = 100f),
        )
        val disabledLocally = FakeInstrumentedConfig(
            enabledFeatures = FakeEnabledFeatureConfig(multiFilePersistence = false, aeiCapture = false),
        )
        with(resolveConfig(disabledLocally, enabledRemotely, unreadBucket)) {
            assertTrue(persistence.multiFileEnabled)
            assertTrue(aei.captureEnabled)
        }
        val disabledRemotely = RemoteConfig(
            pctMultiFilePersistenceEnabled = 0f,
            appExitInfoConfig = AppExitInfoConfig(pctAeiCaptureEnabled = 0f),
        )
        val enabledLocally = FakeInstrumentedConfig(
            enabledFeatures = FakeEnabledFeatureConfig(multiFilePersistence = true, aeiCapture = true),
        )
        with(resolveConfig(enabledLocally, disabledRemotely, unreadBucket)) {
            assertFalse(persistence.multiFileEnabled)
            assertFalse(aei.captureEnabled)
        }
    }

    @Test
    fun `remote values are clamped`() {
        val high = RemoteConfig(
            experimentMaxCount = 6000,
            experimentIdMaxLength = 2000,
            experimentVariantMaxLength = 2000,
            maxUserSessionProperties = 300,
            dataConfig = DataRemoteConfig(periodicCacheIntervalMs = 999_999),
        )
        with(resolveConfig(InstrumentedConfigImpl, high, unreadBucket)) {
            assertEquals(5000, experiment.experimentCountLimit)
            assertEquals(1024, experiment.idLengthLimit)
            assertEquals(1024, experiment.variantLengthLimit)
            assertEquals(200, userSession.sessionPropertyLimit)
            assertEquals(120_000L, otel.periodicCacheIntervalMs)
        }
        val low = RemoteConfig(
            dataConfig = DataRemoteConfig(
                maxCustomSpansPerSession = -1,
                maxInternalSpansPerSession = -1,
                maxNetworkSpansPerSession = -1,
                periodicCacheIntervalMs = 1,
            ),
        )
        with(resolveConfig(InstrumentedConfigImpl, low, unreadBucket).otel) {
            assertEquals(0, maxCustomSpansPerSessionPart)
            assertEquals(0, maxInternalSpansPerSessionPart)
            assertEquals(0, maxNetworkSpansPerSessionPart)
            assertEquals(2000L, periodicCacheIntervalMs)
        }
    }

    @Test
    fun `user session values outside their range are ignored`() {
        listOf(3599, 86_401).forEach {
            val config = resolveSession(UserSessionRemoteConfig(maxDurationSeconds = it))
            assertEquals(43_200, config.maxDurationSeconds)
        }
        listOf(29, 86_401).forEach {
            val config = resolveSession(UserSessionRemoteConfig(inactivityTimeoutSeconds = it))
            assertEquals(1800, config.inactivityTimeoutSeconds)
        }
        with(resolveSession(UserSessionRemoteConfig(maxDurationSeconds = 3600, inactivityTimeoutSeconds = 3601))) {
            assertEquals(3600, maxDurationSeconds)
            assertEquals(1800, inactivityTimeoutSeconds)
        }
        with(resolveSession(UserSessionRemoteConfig(maxDurationSeconds = 3600, inactivityTimeoutSeconds = 3600))) {
            assertEquals(3600, inactivityTimeoutSeconds)
        }
    }

    @Test
    fun `local network limits cap the remote limits`() {
        val local = FakeInstrumentedConfig(
            networkCapture = FakeNetworkCaptureConfig(requestLimit = 10, limits = mapOf("local.com" to "5")),
        )
        val remote = RemoteConfig(
            networkConfig = NetworkRemoteConfig(defaultCaptureLimit = 20, domainLimits = mapOf("remote.com" to 50)),
        )
        with(resolveConfig(local, remote, unreadBucket).network) {
            assertEquals(10, requestLimitPerDomain)
            assertEquals(mapOf("remote.com" to 10), limitsByDomain)
        }
        with(resolveConfig(local, RemoteConfig(), unreadBucket).network) {
            assertEquals(mapOf("local.com" to 5), limitsByDomain)
        }
    }

    @Test
    fun `remote can only turn off locally enabled ui load tracing`() {
        val disabledLocally = FakeInstrumentedConfig(
            enabledFeatures = FakeEnabledFeatureConfig(uiLoadTracingEnabled = false, uiLoadTracingTraceAll = false),
        )
        with(resolveConfig(disabledLocally, RemoteConfig(uiLoadInstrumentationEnabled = true), unreadBucket).autoDataCapture) {
            assertFalse(uiLoadTracingEnabled)
            assertFalse(uiLoadTracingTraceAll)
        }
    }

    @Test
    fun `huc lite is off when full huc capture is on`() {
        val local = FakeInstrumentedConfig(
            enabledFeatures = FakeEnabledFeatureConfig(hucLiteInstrumentation = true, httpUrlConnectionCapture = true),
        )
        assertFalse(resolveConfig(local, null, unreadBucket).network.hucLiteInstrumentationEnabled)
        val liteOnly = FakeInstrumentedConfig(enabledFeatures = FakeEnabledFeatureConfig(hucLiteInstrumentation = true))
        assertTrue(resolveConfig(liteOnly, null, unreadBucket).network.hucLiteInstrumentationEnabled)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `legacy network span forwarding pct enables forwarding and injection for every host`() {
        val legacy =
            RemoteConfig(networkSpanForwardingRemoteConfig = NetworkSpanForwardingRemoteConfig(pctEnabled = 100f))
        with(resolveConfig(InstrumentedConfigImpl, legacy, unreadBucket)) {
            assertTrue(networkSpanForwarding.enabled)
            assertTrue(traceparentInjection.enabled)
            assertTrue(traceparentInjection.legacyFallbackEnabled)
        }
        val superseded = legacy.copy(nsfPctEnabled = 0f)
        with(resolveConfig(InstrumentedConfigImpl, superseded, unreadBucket)) {
            assertFalse(networkSpanForwarding.enabled)
            assertFalse(traceparentInjection.enabled)
            assertFalse(traceparentInjection.legacyFallbackEnabled)
        }
    }

    @Test
    fun `slices can be overridden`() {
        val config = EmbraceConfig(breadcrumb = { BreadcrumbConfig(customLimit = { 5 }) })
        assertEquals(5, config.breadcrumb.customLimit)
        assertEquals(100, config.breadcrumb.tapLimit)
    }

    private fun resolveSession(session: UserSessionRemoteConfig) =
        resolveConfig(InstrumentedConfigImpl, RemoteConfig(userSession = session), unreadBucket).userSession

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun assertDefaults(config: EmbraceConfig) = with(config) {
        with(breadcrumb) {
            assertEquals(100, customLimit)
            assertEquals(100, fragmentLimit)
            assertEquals(100, tapLimit)
            assertEquals(100, webViewLimit)
            assertFalse(captureViewClickCoordinates)
            assertTrue(captureActivities)
            assertTrue(captureWebViews)
            assertTrue(captureWebViewQueryParams)
            assertEquals(WebViewFragmentCapture.KEEP, webViewFragmentCapture)
            assertFalse(captureFcmPiiData)
        }
        assertFalse(persistence.multiFileEnabled)
        with(aei) {
            assertEquals(10_485_760, traceMaxLimit)
            assertTrue(captureEnabled)
            assertEquals(0, maxNum)
        }
        with(autoDataCapture) {
            assertTrue(thermalStatusCaptureEnabled)
            assertTrue(powerSaveModeCaptureEnabled)
            assertTrue(networkConnectivityCaptureEnabled)
            assertTrue(threadBlockageCaptureEnabled)
            assertTrue(jvmCrashCaptureEnabled)
            assertFalse(composeClickCaptureEnabled)
            assertFalse(thirdPartySigHandlerDetectionEnabled)
            assertTrue(nativeCrashCaptureEnabled)
            assertTrue(diskUsageCaptureEnabled)
            assertTrue(uiLoadTracingEnabled)
            assertTrue(uiLoadTracingTraceAll)
            assertFalse(endStartupWithAppReadyEnabled)
            assertTrue(stateCaptureEnabled)
            assertFalse(networkCallbackConnectivityServiceEnabled)
            assertTrue(navigationStateCaptureEnabled)
            assertFalse(smoothnessCaptureEnabled)
            assertFalse(screenLoadCaptureEnabled)
            assertFalse(activityProcessLifecycleTrackerEnabled)
            assertFalse(activityLeakDetectionEnabled)
            assertFalse(fragmentLeakDetectionEnabled)
            assertFalse(webViewLeakDetectionEnabled)
        }
        with(backgroundActivity) {
            assertFalse(captureEnabled)
            assertEquals(100, manualBackgroundActivityLimit)
            assertEquals(5000L, minBackgroundActivityDuration)
            assertEquals(30, maxCachedActivities)
        }
        with(dataCaptureEvent) {
            assertTrue(internalExceptionCaptureEnabled)
            assertNull(disabledEventAndLogPatterns)
        }
        with(experiment) {
            assertEquals(500, experimentCountLimit)
            assertEquals(128, idLengthLimit)
            assertEquals(128, variantLengthLimit)
        }
        with(logMessage) {
            assertEquals(128, maxLength)
            assertEquals(100, infoLimit)
            assertEquals(200, warnLimit)
            assertEquals(500, errorLimit)
        }
        with(network) {
            assertFalse(requestContentLengthCaptureEnabled)
            assertFalse(okHttpResponseBodySizeCaptureEnabled)
            assertFalse(httpUrlConnectionCaptureEnabled)
            assertTrue(hucLiteInstrumentationEnabled)
            assertEquals(1000, requestLimitPerDomain)
            assertEquals(emptyMap<String, Int>(), limitsByDomain)
            assertEquals(600_000L, requestSpanTimeoutMs)
            assertEquals(emptySet<String>(), disabledUrlPatterns)
            assertNull(networkBodyCapturePublicKey)
            assertEquals(emptySet<NetworkCaptureRuleRemoteConfig>(), networkCaptureRules)
        }
        assertFalse(networkSpanForwarding.enabled)
        with(otel) {
            assertFalse(kotlinSdkEnabled)
            assertEquals(500, maxCustomSpansPerSessionPart)
            assertEquals(1500, maxInternalSpansPerSessionPart)
            assertEquals(2000, maxNetworkSpansPerSessionPart)
            assertEquals(1000, maxSpanEventsPerSessionPart)
            assertEquals(2000L, periodicCacheIntervalMs)
        }
        assertTrue(sdkMode.enabled)
        assertNull(sensitiveKeys.denylist)
        with(threadBlockage) {
            assertTrue(captureEnabled)
            assertEquals(100L, sampleIntervalMs)
            assertEquals(80, maxStacktracesPerInterval)
            assertEquals(200, stacktraceFrameLimit)
            assertEquals(5, maxIntervalsPerSession)
            assertEquals(1000, minDurationMs)
        }
        with(traceparentInjection) {
            assertFalse(enabled)
            assertFalse(legacyFallbackEnabled)
            assertNull(onlyAllowDomains)
        }
        with(userSession) {
            assertFalse(sessionControlEnabled)
            assertEquals(100, sessionPropertyLimit)
            assertEquals(43_200, maxDurationSeconds)
            assertEquals(1800, inactivityTimeoutSeconds)
            assertEquals(5000L, minSessionDurationMs)
        }
        with(vitals) {
            assertEquals(100L, smoothnessIdleThresholdMs)
            assertEquals(500L, smoothnessHeldIdleThresholdMs)
            assertEquals(2.0, jankHeuristicMultiplier, 0.0)
            assertEquals(1000L, screenLoadIdleThresholdMs)
            assertEquals(30_000L, screenLoadTimeoutMs)
            assertEquals(500L, screenLoadNavTimeoutMs)
            assertFalse(smoothnessFrameTraceEnabled)
            assertEquals(250, spanLimit)
        }
        with(app) {
            listOf(appId, appFramework, buildId, buildType, buildFlavor, reactNativeBundleId, versionName, versionCode, packageName)
                .forEach(::assertNull)
        }
        assertNull(baseUrls.config)
        assertNull(baseUrls.data)
        assertNull(symbols.base64SharedObjectFilesMap)
        with(telemetryLimits) {
            assertEquals(2000, maxInternalNameLength)
            assertEquals(128, maxNameLength)
            assertEquals(10, maxCustomEventCount)
            assertEquals(2000, maxSystemEventCount)
            assertEquals(100, maxCustomAttributeCount)
            assertEquals(300, maxSystemAttributeCount)
            assertEquals(10, maxEventAttributeCount)
            assertEquals(10, maxCustomLinkCount)
            assertEquals(100, maxSystemLinkCount)
            assertEquals(1000, maxInternalAttributeKeyLength)
            assertEquals(2000, maxInternalAttributeValueLength)
            assertEquals(128, maxCustomAttributeKeyLength)
            assertEquals(1024, maxCustomAttributeValueLength)
            assertEquals("exception", exceptionEventName)
        }
    }

    private fun remote() = RemoteConfig(
        threshold = 0,
        disabledEventAndLogPatterns = setOf("event"),
        disabledUrlPatterns = setOf("url"),
        networkCaptureRules = setOf(rule),
        uiConfig = UiRemoteConfig(breadcrumbs = 1, taps = 2, webViews = 3, fragments = 4),
        networkConfig = NetworkRemoteConfig(defaultCaptureLimit = 20, domainLimits = mapOf("small.com" to 5, "large.com" to 50)),
        sessionConfig = SessionRemoteConfig(isEnabled = true),
        logConfig = LogRemoteConfig(
            logMessageMaximumAllowedLength = 10,
            logInfoLimit = 11,
            logWarnLimit = 12,
            logErrorLimit = 13,
        ),
        threadBlockageRemoteConfig = ThreadBlockageRemoteConfig(
            pctEnabled = 0,
            sampleIntervalMs = 5,
            maxStacktracesPerInterval = 6,
            stacktraceFrameLimit = 7,
            intervalsPerSession = 8,
            minDuration = 9,
        ),
        dataConfig = DataRemoteConfig(
            pctThermalStatusEnabled = 0f,
            networkRequestSpanTimeoutMs = 1234,
            maxCustomSpansPerSession = 1,
            maxInternalSpansPerSession = 2,
            maxNetworkSpansPerSession = 3,
            maxSpanEventsPerSessionPart = 4,
            periodicCacheIntervalMs = 5000,
        ),
        killSwitchConfig = KillSwitchRemoteConfig(sigHandlerDetection = true, jetpackCompose = true),
        internalExceptionCaptureEnabled = false,
        appExitInfoConfig = AppExitInfoConfig(appExitInfoTracesLimit = 99, pctAeiCaptureEnabled = 0f, aeiMaxNum = 3),
        backgroundActivityConfig = BackgroundActivityRemoteConfig(threshold = 100f),
        maxUserSessionProperties = 150,
        experimentMaxCount = 600,
        experimentIdMaxLength = 200,
        experimentVariantMaxLength = 300,
        nsfPctEnabled = 100f,
        traceparentInjectionPctEnabled = 100f,
        uiLoadInstrumentationEnabled = false,
        otelKotlinSdkConfig = OtelKotlinSdkConfig(pctEnabled = 100f),
        pctStateCaptureEnabledV2 = 0f,
        pctNetworkCallbackConnectivityServiceEnabled = 100f,
        pctNavigationStateCaptureEnabled = 0f,
        pctActivityProcessLifecycleTrackerEnabled = 100f,
        userSession = UserSessionRemoteConfig(maxDurationSeconds = 7200, inactivityTimeoutSeconds = 600),
        pctSmoothnessEnabled = 100f,
        pctScreenLoadEnabled = 100f,
        vitalsRemoteConfig = VitalsRemoteConfig(
            smoothnessIdleThresholdMs = 1,
            smoothnessHeldIdleThresholdMs = 2,
            jankHeuristicMultiplier = 3.0,
            screenLoadIdleThresholdMs = 4,
            screenLoadTimeoutMs = 5,
            screenLoadNavTimeoutMs = 6,
            smoothnessFrameTracePctEnabled = 100f,
            spanLimit = 7,
        ),
        pctMultiFilePersistenceEnabled = 100f,
        pctActivityLeakDetectionEnabled = 100f,
        pctFragmentLeakDetectionEnabled = 100f,
        pctWebViewLeakDetectionEnabled = 100f,
    )

    private fun local(): InstrumentedConfig = FakeInstrumentedConfig(
        enabledFeatures = FakeEnabledFeatureConfig(
            activityBreadcrumbCapture = false,
            composeClickCapture = true,
            viewClickCoordCapture = true,
            powerSaveCapture = false,
            networkConnectivityCapture = false,
            threadBlockageCapture = false,
            diskUsageCapture = false,
            jvmCrashCapture = false,
            nativeCrashCapture = false,
            aeiCapture = false,
            sigHandlerDetection = true,
            bgActivityCapture = true,
            webviewBreadcrumbCapture = false,
            webviewQueryCapture = false,
            webviewFragmentCapture = WebViewFragmentCapture.REMOVE,
            fcmPiiCapture = true,
            requestContentLengthCapture = true,
            okHttpResponseBodySizeCapture = true,
            httpUrlConnectionCapture = true,
            hucLiteInstrumentation = true,
            networkSpanForwarding = true,
            traceparentInjection = true,
            uiLoadTracingEnabled = false,
            uiLoadTracingTraceAll = false,
            endStartupWithAppReady = true,
            otelKotlinSdkEnabled = true,
            activityProcessLifecycleTracker = true,
            multiFilePersistence = true,
        ),
        networkCapture = FakeNetworkCaptureConfig(
            requestLimit = 10,
            limits = mapOf("local.com" to "50"),
            ignoredRequestPatterns = listOf("pattern"),
            publicKey = "key",
            traceparentOnlyAllowDomains = listOf(".allowed.com"),
        ),
        project = FakeProjectConfig(
            appId = "12345",
            appFramework = "unity",
            buildId = "build",
            buildType = "type",
            buildFlavor = "flavor",
            packageName = "com.example",
        ),
        baseUrls = FakeBaseUrlConfig(configImpl = "https://config", dataImpl = "https://data"),
        redaction = FakeRedactionConfig(sensitiveKeys = listOf("secret")),
        symbols = FakeBase64SharedObjectFilesMap("symbols"),
    )
}
