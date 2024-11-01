package io.embrace.android.embracesdk.internal.capture.webview

import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.NoInputValidation
import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.config.behavior.WebViewVitalsBehavior
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.payload.WebViewInfo
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.utils.toUTF8String

/**
 * Captures WebViews information, like view properties, console logs, or core web vitals.
 */
class WebViewDataSource(
    private val webViewVitalsBehavior: WebViewVitalsBehavior,
    private val writer: SessionSpanWriter,
    logger: EmbLogger,
    private val serializer: PlatformSerializer,
) : DataSourceImpl<SessionSpanWriter>(
    destination = writer,
    logger = logger,
    limitStrategy = UpToLimitStrategy(webViewVitalsBehavior::getMaxWebViewVitals),
) {

    fun loadDataIntoSession(webViewInfoList: List<WebViewInfo>) {
        runCatching {
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
        }
    }
}
