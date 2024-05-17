package io.embrace.android.embracesdk.capture.webview

import io.embrace.android.embracesdk.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.arch.destination.SpanEventData
import io.embrace.android.embracesdk.arch.destination.SpanEventMapper
import io.embrace.android.embracesdk.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.config.behavior.WebViewVitalsBehavior
import io.embrace.android.embracesdk.internal.clock.millisToNanos
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
    limitStrategy = UpToLimitStrategy(logger, webViewVitalsBehavior::getMaxWebViewVitals),
),
    SpanEventMapper<WebViewInfo> {

    fun loadDataIntoSession(webViewInfoList: List<WebViewInfo>) {
        try {
            writer.removeEvents(EmbType.System.WebViewInfo)
            webViewInfoList.forEach { webViewInfo ->
                alterSessionSpan(
                    inputValidation = { true },
                    captureAction = {
                        addEvent(webViewInfo, ::toSpanEventData)
                    },
                )
            }
        } catch (ex: Exception) {
            logger.logError("Failed to capture WebViewInfo", ex)
        }
    }

    override fun toSpanEventData(obj: WebViewInfo): SpanEventData {
        val webVitalsString = serializer
            .toJson(obj.webVitals, List::class.java)
            .toByteArray()
            .toUTF8String()
        return SpanEventData(
            SchemaType.WebViewInfo(
                url = obj.url,
                webVitals = webVitalsString,
                tag = obj.tag
            ),
            obj.startTime.millisToNanos()
        )
    }
}
