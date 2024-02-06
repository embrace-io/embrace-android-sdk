package io.embrace.android.embracesdk.capture.webview

import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.payload.WebViewInfo
import io.embrace.android.embracesdk.payload.WebVitalType
import java.util.EnumMap

internal class EmbraceWebViewService(
    val configService: ConfigService,
    private val serializer: EmbraceSerializer
) : WebViewService {

    /**
     * The information collected for each WebView
     */
    private val webViewInfoMap = hashMapOf<String, WebViewInfo>()

    override fun collectWebData(tag: String, message: String) {
        if (message.contains(MESSAGE_KEY_FOR_METRICS)) {
            collectWebVital(message, tag)
        } else {
            InternalStaticEmbraceLogger.logger.logDebug("WebView console message ignored.")
        }
    }

    override fun getCapturedData(): List<WebViewInfo> {
        return webViewInfoMap.values.toList().take(configService.webViewVitalsBehavior.getMaxWebViewVitals())
    }

    private fun collectWebVital(message: String, tag: String) {
        InternalStaticEmbraceLogger.logger.logDeveloper("EmbraceWebViewService", "Collecting web metric")

        if (webViewInfoMap.size >= configService.webViewVitalsBehavior.getMaxWebViewVitals()) {
            InternalStaticEmbraceLogger.logger.logDebug("Max webview vitals per session exceeded")
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

            webViewInfoMap[it.url + it.startTime] = processVitalList(it, checkNotNull(webViewInfoMap[it.url + it.startTime]))
        }
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
            } else {
                InternalStaticEmbraceLogger.logger.logError("Web Vital info is too large to parse")
            }
        } catch (e: Exception) {
            InternalStaticEmbraceLogger.logger.logError("Cannot parse Web Vital", e)
        }
        return null
    }

    override fun cleanCollections() {
        webViewInfoMap.clear()
    }

    companion object {
        private const val SCRIPT_MESSAGE_MAXIMUM_ALLOWED_LENGTH = 2000

        /**
         * Metrics have this attribute to recognize and parse them.
         */
        private const val MESSAGE_KEY_FOR_METRICS = "EMBRACE_METRIC"
    }
}
