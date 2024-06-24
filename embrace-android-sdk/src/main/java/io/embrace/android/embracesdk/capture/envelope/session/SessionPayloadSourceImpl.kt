package io.embrace.android.embracesdk.capture.envelope.session

import io.embrace.android.embracesdk.anr.AnrOtelMapper
import io.embrace.android.embracesdk.anr.ndk.NativeAnrOtelMapper
import io.embrace.android.embracesdk.anr.ndk.NativeThreadSamplerService
import io.embrace.android.embracesdk.arch.schema.AppTerminationCause
import io.embrace.android.embracesdk.capture.webview.WebViewService
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.SpanRepository
import io.embrace.android.embracesdk.internal.spans.SpanSink
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.session.captureDataSafely
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.session.properties.SessionPropertiesService

internal class SessionPayloadSourceImpl(
    private val nativeThreadSamplerService: NativeThreadSamplerService?,
    private val spanSink: SpanSink,
    private val currentSessionSpan: CurrentSessionSpan,
    private val spanRepository: SpanRepository,
    private val anrOtelMapper: AnrOtelMapper,
    private val nativeAnrOtelMapper: NativeAnrOtelMapper,
    private val logger: EmbLogger,
    private val webViewServiceProvider: Provider<WebViewService>,
    private val sessionPropertiesServiceProvider: Provider<SessionPropertiesService>
) : SessionPayloadSource {

    override fun getSessionPayload(endType: SessionSnapshotType, crashId: String?): SessionPayload {
        val sharedLibSymbolMapping = captureDataSafely(logger) { nativeThreadSamplerService?.getNativeSymbols() }

        // future: convert webview service to use OtelMapper pattern or data source directly.
        webViewServiceProvider().loadDataIntoSession()

        // Ensure the span retrieving is last as that potentially ends the session span, which effectively ends the session
        val snapshots: List<Span>? = retrieveSpanSnapshots()
        val spans: List<Span>? = retrieveSpanData(endType, crashId)

        return SessionPayload(
            spans = spans,
            spanSnapshots = snapshots,
            sharedLibSymbolMapping = sharedLibSymbolMapping
        )
    }

    private fun retrieveSpanData(endType: SessionSnapshotType, crashId: String?): List<Span>? {
        val cacheAttempt = endType == SessionSnapshotType.PERIODIC_CACHE

        val spans: List<Span>? = captureDataSafely(logger) {
            val result = when {
                !cacheAttempt -> {
                    val appTerminationCause = when {
                        crashId != null -> AppTerminationCause.Crash
                        else -> null
                    }
                    val spans = currentSessionSpan.endSession(appTerminationCause)
                    if (appTerminationCause == null) {
                        sessionPropertiesServiceProvider().populateCurrentSession()
                    }
                    spans.map(EmbraceSpanData::toNewPayload)
                }

                else -> spanSink.completedSpans().map(EmbraceSpanData::toNewPayload)
            }
            // add ANR spans if the payload is capturing spans.
            result.plus(anrOtelMapper.snapshot(!cacheAttempt))
                .plus(nativeAnrOtelMapper.snapshot(!cacheAttempt))
        }
        return spans
    }

    private fun retrieveSpanSnapshots() = captureDataSafely(logger) {
        spanRepository.getActiveSpans().mapNotNull { it.snapshot() }
    }
}
