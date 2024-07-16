package io.embrace.android.embracesdk.capture.webview

import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.NoInputValidation
import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.config.behavior.WebViewVitalsBehavior
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.utils.toUTF8String
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.payload.WebViewInfo

/**
 * Captures WebViews information, like view properties, console logs, or core web vitals.
 */
internal class WebViewDataSource(
    private val webViewVitalsBehavior: WebViewVitalsBehavior,
    private val writer: SessionSpanWriter,
    private val logger: EmbLogger,
    private val serializer: EmbraceSerializer,
) : DataSourceImpl<SessionSpanWriter>(
    destination = writer,
    logger = logger,
    limitStrategy = UpToLimitStrategy(webViewVitalsBehavior::getMaxWebViewVitals),
) {

    fun loadDataIntoSession(webViewInfoList: List<WebViewInfo>) {
        try {
            writer.removeEvents(EmbType.System.WebViewInfo)
            webViewInfoList.forEach { webViewInfo ->
                captureData(
                    inputValidation = NoInputValidation,
                    captureAction = {
                        val webVitalsString = serializer
                            .toJson(webViewInfo.webVitals, List::class.java)
                            .toByteArray()
                            .toUTF8String()

                        addEvent(
                            SchemaType.WebViewInfo(
                                url = webViewInfo.url,
                                webVitals = webVitalsString,
                                tag = webViewInfo.tag
                            ),
                            webViewInfo.startTime
                        )
                    },
                )
            }
        } catch (ex: Exception) {
            logger.logError("Failed to capture WebViewInfo", ex)
        }
    }
}
