package io.embrace.android.embracesdk.internal.capture.webview

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.payload.WebViewInfo
import io.embrace.android.embracesdk.internal.payload.WebVitalType
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.session.MemoryCleanerListener
import io.embrace.android.embracesdk.internal.utils.Provider
import java.util.EnumMap

internal class EmbraceWebViewService(
    val configService: ConfigService,
    private val serializer: PlatformSerializer,
    private val logger: EmbLogger,
    private val webViewDataSourceProvider: Provider<WebViewDataSource?>,
) : WebViewService, MemoryCleanerListener {

    /**
     * The information collected for each WebView
     */
    private val webViewInfoMap = hashMapOf<String, WebViewInfo>()

    override fun collectWebData(tag: String, message: String) {
        if (message.contains(MESSAGE_KEY_FOR_METRICS)) {
            collectWebVital(message, tag)
        }
    }

    private fun collectWebVital(message: String, tag: String) {
        if (webViewInfoMap.size >= configService.webViewVitalsBehavior.getMaxWebViewVitals()) {
            return
        }
        val collectedWebVitals = parseWebVital(message)
        collectedWebVitals?.let {
            if (webViewInfoMap[it.url + it.startTime] == null) {
                webViewInfoMap[it.url + it.startTime] = it.copy(
                    tag = tag,
                    webVitalMap = EnumMap(
                        WebVitalType::class.java
                    )
                )
            }

            webViewInfoMap[it.url + it.startTime] =
                processVitalList(it, checkNotNull(webViewInfoMap[it.url + it.startTime]))
        }
        webViewDataSourceProvider()?.loadDataIntoSession(webViewInfoMap.values.toList())
    }

    /**
     * The WebView can emit multiple metrics of the same type. Depending on the type of metric, a different filter
     * should be applied:
     * - CLS: keep the metric with the longest duration (worst performance)
     * - LCP: keep the last generated metric (highest start time)
     * - FID and FCP: keep the first metric that arrives
     */
    private fun processVitalList(newMessage: WebViewInfo, storedMessage: WebViewInfo): WebViewInfo {
        newMessage.webVitals.forEach { newVital ->
            storedMessage.webVitalMap[newVital.type].let {
                if (it == null) {
                    storedMessage.webVitalMap[newVital.type] = newVital
                } else {
                    when (it.type) {
                        WebVitalType.CLS -> {
                            if ((newVital.duration ?: 0) > (it.duration ?: 0)) { // largest CLS metric
                                storedMessage.webVitalMap[it.type] = newVital
                            }
                        }

                        WebVitalType.LCP -> {
                            if (newVital.startTime > it.startTime) { // most recent capture st time
                                storedMessage.webVitalMap[it.type] = newVital
                            }
                        }

                        else -> {
                            // do nothing
                        }
                    }
                }
            }
        }

        return storedMessage.copy(webVitals = storedMessage.webVitalMap.values.toMutableList())
    }

    private fun parseWebVital(message: String): WebViewInfo? {
        try {
            if (message.length < SCRIPT_MESSAGE_MAXIMUM_ALLOWED_LENGTH) {
                return serializer.fromJson(message, WebViewInfo::class.java)
            }
        } catch (e: Exception) {
            logger.trackInternalError(InternalErrorType.WEB_VITAL_PARSE_FAIL, e)
        }
        return null
    }

    override fun cleanCollections() {
        webViewInfoMap.clear()
    }

    private companion object {
        private const val SCRIPT_MESSAGE_MAXIMUM_ALLOWED_LENGTH = 2000

        /**
         * Metrics have this attribute to recognize and parse them.
         */
        private const val MESSAGE_KEY_FOR_METRICS = "EMBRACE_METRIC"
    }
}
