package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.anr.AnrOtelMapper
import io.embrace.android.embracesdk.anr.ndk.NativeAnrOtelMapper
import io.embrace.android.embracesdk.anr.ndk.NativeThreadSamplerService
import io.embrace.android.embracesdk.arch.schema.AppTerminationCause
import io.embrace.android.embracesdk.capture.PerformanceInfoService
import io.embrace.android.embracesdk.capture.envelope.session.SessionEnvelopeSource
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.capture.startup.StartupService
import io.embrace.android.embracesdk.capture.webview.WebViewService
import io.embrace.android.embracesdk.event.EventService
import io.embrace.android.embracesdk.event.LogMessageService
import io.embrace.android.embracesdk.gating.GatingService
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.toOldPayload
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.SpanRepository
import io.embrace.android.embracesdk.internal.spans.SpanSink
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.prefs.PreferencesService
import io.embrace.android.embracesdk.session.captureDataSafely
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.session.properties.SessionPropertiesService

/**
 * Generates a V2 payload. This currently calls through to the V1 collator for
 * backwards compatibility.
 */
internal class V2PayloadMessageCollator(
    private val gatingService: GatingService,
    private val sessionEnvelopeSource: SessionEnvelopeSource,
    private val metadataService: MetadataService,
    private val eventService: EventService,
    private val logMessageService: LogMessageService,
    private val performanceInfoService: PerformanceInfoService,
    private val webViewService: WebViewService,
    private val nativeThreadSamplerService: NativeThreadSamplerService?,
    private val preferencesService: PreferencesService,
    private val spanRepository: SpanRepository,
    private val spanSink: SpanSink,
    private val currentSessionSpan: CurrentSessionSpan,
    private val sessionPropertiesService: SessionPropertiesService,
    private val startupService: StartupService,
    private val anrOtelMapper: AnrOtelMapper,
    private val nativeAnrOtelMapper: NativeAnrOtelMapper,
    private val logger: EmbLogger,
) : PayloadMessageCollator {

    override fun buildInitialSession(params: InitialEnvelopeParams): Session {
        return with(params) {
            Session(
                sessionId = currentSessionSpan.getSessionId(),
                startTime = startTime,
                isColdStart = coldStart,
                messageType = Session.MESSAGE_TYPE_END,
                appState = appState,
                startType = startType,
                number = getSessionNumber(preferencesService),
                properties = getProperties(sessionPropertiesService),
            )
        }
    }

    override fun buildFinalSessionMessage(params: FinalEnvelopeParams.SessionParams): SessionMessage {
        val newParams = FinalEnvelopeParams.SessionParams(
            initial = params.initial,
            endTime = params.endTime,
            lifeEventType = params.lifeEventType,
            crashId = params.crashId,
            endType = params.endType,
            captureSpans = false,
            logger = logger
        )
        val obj = with(newParams) {
            val base = buildFinalBackgroundActivity(newParams)
            val startupInfo = getStartupEventInfo(eventService)

            val endSession = base.copy(
                isEndedCleanly = endType.endedCleanly,
                networkLogIds = captureDataSafely(logger) {
                    logMessageService.findNetworkLogIds(
                        initial.startTime,
                        endTime
                    )
                },
                properties = captureDataSafely(logger, sessionPropertiesService::getProperties),
                webViewInfo = captureDataSafely(logger, webViewService::getCapturedData),
                terminationTime = terminationTime,
                isReceivedTermination = receivedTermination,
                endTime = endTimeVal,
                sdkStartupDuration = startupService.getSdkStartupDuration(initial.isColdStart),
                startupDuration = startupInfo?.duration,
                startupThreshold = startupInfo?.threshold,
                symbols = captureDataSafely(logger) { nativeThreadSamplerService?.getNativeSymbols() }
            )
            val envelope = buildWrapperEnvelope(newParams, endSession, initial.startTime, endTime)
            gatingService.gateSessionMessage(envelope)
        }
        return obj.convertToV2Payload(newParams.endType)
    }

    override fun buildFinalBackgroundActivityMessage(params: FinalEnvelopeParams.BackgroundActivityParams): SessionMessage {
        val newParams = FinalEnvelopeParams.BackgroundActivityParams(
            initial = params.initial,
            endTime = params.endTime,
            lifeEventType = params.lifeEventType,
            crashId = params.crashId,
            endType = params.endType,
            captureSpans = false,
            logger = logger
        )
        val msg = buildFinalBackgroundActivity(newParams)
        val startTime = msg.startTime
        val endTime = newParams.endTime
        val envelope = buildWrapperEnvelope(newParams, msg, startTime, endTime)
        val obj = gatingService.gateSessionMessage(envelope)
        return obj.convertToV2Payload(newParams.endType)
    }

    private fun SessionMessage.convertToV2Payload(endType: SessionSnapshotType): SessionMessage {
        val envelope = gatingService.gateSessionEnvelope(this, sessionEnvelopeSource.getEnvelope(endType))
        return copy(
            // future work: make legacy fields null here.
            resource = envelope.resource,
            metadata = envelope.metadata,
            data = envelope.data,
            newVersion = envelope.version,
            type = envelope.type,

            // make legacy fields null
            version = null,
            spans = null,
        )
    }

    /**
     * Creates a background activity stop message.
     */
    private fun buildFinalBackgroundActivity(
        params: FinalEnvelopeParams
    ): Session = with(params) {
        return initial.copy(
            endTime = endTime,
            eventIds = captureDataSafely(logger) {
                eventService.findEventIdsForSession()
            },
            lastHeartbeatTime = endTime,
            endType = lifeEventType,
            crashReportId = crashId
        )
    }

    private fun buildWrapperEnvelope(
        params: FinalEnvelopeParams,
        finalPayload: Session,
        startTime: Long,
        endTime: Long,
    ): SessionMessage {
        val spans: List<EmbraceSpanData>? = captureDataSafely(logger) {
            val result = when {
                !params.captureSpans -> null
                !params.isCacheAttempt -> {
                    val appTerminationCause = when {
                        finalPayload.crashReportId != null -> AppTerminationCause.Crash
                        else -> null
                    }
                    val spans = currentSessionSpan.endSession(appTerminationCause)
                    if (appTerminationCause == null) {
                        sessionPropertiesService.populateCurrentSession()
                    }
                    spans
                }

                else -> spanSink.completedSpans()
            }
            // add ANR spans if the payload is capturing spans.
            result?.plus(anrOtelMapper.snapshot(!params.isCacheAttempt).map(Span::toOldPayload))
                ?.plus(nativeAnrOtelMapper.snapshot(!params.isCacheAttempt).map(Span::toOldPayload))
                ?: result
        }
        val spanSnapshots = captureDataSafely(logger) {
            spanRepository.getActiveSpans().mapNotNull { it.snapshot()?.toOldPayload() }
        }

        return SessionMessage(
            session = finalPayload,
            appInfo = captureDataSafely(logger, metadataService::getAppInfo),
            deviceInfo = captureDataSafely(logger, metadataService::getDeviceInfo),
            performanceInfo = captureDataSafely(logger) {
                performanceInfoService.getSessionPerformanceInfo(
                    startTime,
                    endTime,
                    finalPayload.isColdStart,
                    null
                )
            },
            spans = spans,
            spanSnapshots = spanSnapshots,
        )
    }
}
