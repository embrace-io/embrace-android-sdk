package io.embrace.android.gradle.plugin.instrumentation.config.arch.sdk

import io.embrace.android.gradle.plugin.instrumentation.config.model.AnrLocalConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.AppExitInfoLocalConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.AppLocalConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.AutomaticDataCaptureLocalConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.BackgroundActivityLocalConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.ComposeLocalConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.CrashHandlerLocalConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.EmbraceVariantConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.NetworkLocalConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.SdkLocalConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.TapsLocalConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.ViewLocalConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.WebViewLocalConfig
import org.junit.Test

class EnabledFeatureConfigInstrumentationKtTest {

    private val cfg = VariantConfig(
        "",
        null,
        null,
        null,
        null
    )

    private val methods = listOf(
        ConfigMethod("isUnityAnrCaptureEnabled", "()Z", true),
        ConfigMethod("isActivityBreadcrumbCaptureEnabled", "()Z", true),
        ConfigMethod("isComposeClickCaptureEnabled", "()Z", true),
        ConfigMethod("isViewClickCoordinateCaptureEnabled", "()Z", true),
        ConfigMethod("isMemoryWarningCaptureEnabled", "()Z", true),
        ConfigMethod("isPowerSaveModeCaptureEnabled", "()Z", true),
        ConfigMethod("isNetworkConnectivityCaptureEnabled", "()Z", true),
        ConfigMethod("isAnrCaptureEnabled", "()Z", true),
        ConfigMethod("isDiskUsageCaptureEnabled", "()Z", true),
        ConfigMethod("isJvmCrashCaptureEnabled", "()Z", true),
        ConfigMethod("isNativeCrashCaptureEnabled", "()Z", true),
        ConfigMethod("isAeiCaptureEnabled", "()Z", true),
        ConfigMethod("is3rdPartySigHandlerDetectionEnabled", "()Z", true),
        ConfigMethod("isBackgroundActivityCaptureEnabled", "()Z", true),
        ConfigMethod("isWebViewBreadcrumbCaptureEnabled", "()Z", true),
        ConfigMethod("isWebViewBreadcrumbQueryParamCaptureEnabled", "()Z", true),
        ConfigMethod("isFcmPiiDataCaptureEnabled", "()Z", true),
        ConfigMethod("isRequestContentLengthCaptureEnabled", "()Z", true),
        ConfigMethod("isHttpUrlConnectionCaptureEnabled", "()Z", true),
        ConfigMethod("isNetworkSpanForwardingEnabled", "()Z", true),
        ConfigMethod("isUiLoadTracingEnabled", "()Z", true),
        ConfigMethod("isUiLoadTracingTraceAll", "()Z", true),
    )

    @Test
    fun `test empty cfg`() {
        val instrumentation = createEnabledFeatureConfigInstrumentation(cfg)

        methods.map { it.copy(result = null) }.forEach { method ->
            verifyConfigMethodVisitor(instrumentation, method)
        }
    }

    @Test
    fun `test instrumentation`() {
        val instrumentation = createEnabledFeatureConfigInstrumentation(
            cfg.copy(
                embraceConfig = EmbraceVariantConfig(
                    null,
                    null,
                    true,
                    SdkLocalConfig(
                        anr = AnrLocalConfig(
                            captureUnityThread = true
                        ),
                        app = AppLocalConfig(
                            reportDiskUsage = true
                        ),
                        appExitInfoConfig = AppExitInfoLocalConfig(
                            aeiCaptureEnabled = true
                        ),
                        automaticDataCaptureConfig = AutomaticDataCaptureLocalConfig(
                            memoryServiceEnabled = true,
                            powerSaveModeServiceEnabled = true,
                            networkConnectivityServiceEnabled = true,
                            anrServiceEnabled = true,
                            uiLoadPerfTracingDisabled = false,
                            uiLoadPerfTracingSelectedOnly = false,
                        ),
                        backgroundActivityConfig = BackgroundActivityLocalConfig(
                            backgroundActivityCaptureEnabled = true
                        ),
                        captureFcmPiiData = true,
                        composeConfig = ComposeLocalConfig(
                            captureComposeOnClick = true
                        ),
                        crashHandler = CrashHandlerLocalConfig(
                            enabled = true
                        ),
                        networking = NetworkLocalConfig(
                            captureRequestContentLength = true,
                            enableNativeMonitoring = true,
                            enableNetworkSpanForwarding = true
                        ),
                        sigHandlerDetection = true,
                        taps = TapsLocalConfig(
                            captureCoordinates = true
                        ),
                        webViewConfig = WebViewLocalConfig(
                            captureWebViews = true,
                            captureQueryParams = true
                        ),
                        viewConfig = ViewLocalConfig(
                            enableAutomaticActivityCapture = true
                        )
                    ),
                    null
                )
            )
        )
        methods.forEach { method ->
            verifyConfigMethodVisitor(instrumentation, method)
        }
    }

    @Test
    fun `verify disabling ui load traces`() {
        val instrumentation = createEnabledFeatureConfigInstrumentation(
            cfg.copy(
                embraceConfig = EmbraceVariantConfig(
                    null,
                    null,
                    true,
                    SdkLocalConfig(
                        automaticDataCaptureConfig = AutomaticDataCaptureLocalConfig(
                            uiLoadPerfTracingDisabled = true,
                        )
                    ),
                    null
                )
            )
        )

        listOf(
            ConfigMethod("isUiLoadTracingEnabled", "()Z", false),
            ConfigMethod("isUiLoadTracingTraceAll", "()Z", false),
        ).forEach { method ->
            verifyConfigMethodVisitor(instrumentation, method)
        }
    }

    @Test
    fun `verify enabling only selected activities for ui load traces`() {
        val instrumentation = createEnabledFeatureConfigInstrumentation(
            cfg.copy(
                embraceConfig = EmbraceVariantConfig(
                    null,
                    null,
                    true,
                    SdkLocalConfig(
                        automaticDataCaptureConfig = AutomaticDataCaptureLocalConfig(
                            uiLoadPerfTracingSelectedOnly = true,
                        )
                    ),
                    null
                )
            )
        )

        listOf(
            ConfigMethod("isUiLoadTracingEnabled", "()Z", true),
            ConfigMethod("isUiLoadTracingTraceAll", "()Z", false),
        ).forEach { method ->
            verifyConfigMethodVisitor(instrumentation, method)
        }
    }

    @Test
    fun `verify disabling and enabling selected ui load traces end up disabling both`() {
        val instrumentation = createEnabledFeatureConfigInstrumentation(
            cfg.copy(
                embraceConfig = EmbraceVariantConfig(
                    null,
                    null,
                    true,
                    SdkLocalConfig(
                        automaticDataCaptureConfig = AutomaticDataCaptureLocalConfig(
                            uiLoadPerfTracingDisabled = true,
                            uiLoadPerfTracingSelectedOnly = true,
                        )
                    ),
                    null
                )
            )
        )

        listOf(
            ConfigMethod("isUiLoadTracingEnabled", "()Z", false),
            ConfigMethod("isUiLoadTracingTraceAll", "()Z", false),
        ).forEach { method ->
            verifyConfigMethodVisitor(instrumentation, method)
        }
    }

    @Test
    fun `verify not specifying any configuration for ui load traces`() {
        val instrumentation = createEnabledFeatureConfigInstrumentation(
            cfg.copy(
                embraceConfig = EmbraceVariantConfig(
                    null,
                    null,
                    true,
                    SdkLocalConfig(
                        automaticDataCaptureConfig = AutomaticDataCaptureLocalConfig()
                    ),
                    null
                )
            )
        )

        listOf(
            ConfigMethod("isUiLoadTracingEnabled", "()Z", true),
            ConfigMethod("isUiLoadTracingTraceAll", "()Z", true),
        ).forEach { method ->
            verifyConfigMethodVisitor(instrumentation, method)
        }
    }
}
