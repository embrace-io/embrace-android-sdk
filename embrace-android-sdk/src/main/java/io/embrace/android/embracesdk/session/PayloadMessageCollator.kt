package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.anr.ndk.NativeThreadSamplerService
import io.embrace.android.embracesdk.capture.PerformanceInfoService
import io.embrace.android.embracesdk.capture.crumbs.BreadcrumbService
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.capture.thermalstate.ThermalStatusService
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.capture.webview.WebViewService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.event.EventService
import io.embrace.android.embracesdk.event.LogMessageService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.spans.EmbraceAttributes
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.SpansService
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.logging.InternalErrorService
import io.embrace.android.embracesdk.payload.BetaFeatures
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.prefs.PreferencesService
import io.embrace.android.embracesdk.session.properties.SessionPropertiesService

internal class PayloadMessageCollator(
    private val configService: ConfigService,
    private val metadataService: MetadataService,
    private val eventService: EventService,
    private val logMessageService: LogMessageService,
    private val internalErrorService: InternalErrorService,
    private val performanceInfoService: PerformanceInfoService,
    private val webViewService: WebViewService,
    private val thermalStatusService: ThermalStatusService,
    private val nativeThreadSamplerService: NativeThreadSamplerService?,
    private val breadcrumbService: BreadcrumbService,
    private val userService: UserService,
    private val preferencesService: PreferencesService,
    private val spansService: SpansService,
    private val clock: Clock,
    private val sessionPropertiesService: SessionPropertiesService
) {

    /**
     * Builds a new session object. This should not be sent to the backend but is used
     * to populate essential session information (such as ID), etc
     */
    internal fun buildInitialSession(params: InitialEnvelopeParams) = with(params) {
        Session(
            sessionId = Uuid.getEmbUuid(),
            startTime = startTime,
            isColdStart = coldStart,
            messageType = Session.MESSAGE_TYPE_END,
            appState = appState,
            startType = startType,
            user = captureDataSafely(userService::loadUserInfoFromDisk),
            number = getSessionNumber(preferencesService),
            properties = getProperties(sessionPropertiesService),
        )
    }

    /**
     * Builds a fully populated session message. This can be sent to the backend (or stored
     * on disk).
     */
    @Suppress("ComplexMethod")
    internal fun buildEndSessionMessage(
        originSession: Session,
        endedCleanly: Boolean,
        forceQuit: Boolean,
        crashId: String?,
        endType: Session.LifeEventType,
        sdkStartupDuration: Long,
        endTime: Long,
        spans: List<EmbraceSpanData>? = null
    ): SessionMessage {
        val startTime: Long = originSession.startTime

        // if it's a crash session, then add the stacktrace to the session payload
        val crashReportId = when {
            !crashId.isNullOrEmpty() -> crashId
            else -> null
        }
        val terminationTime = when {
            forceQuit -> endTime
            else -> null
        }
        val receivedTermination = when {
            forceQuit -> true
            else -> null
        }
        // We don't set end time for force-quit, as the API interprets this to be a clean
        // termination
        val endTimeVal = when {
            forceQuit -> null
            else -> endTime
        }

        val sdkStartDuration = when (originSession.isColdStart) {
            true -> sdkStartupDuration
            false -> null
        }

        val startupEventInfo = captureDataSafely(eventService::getStartupMomentInfo)

        val startupDuration = when (originSession.isColdStart && startupEventInfo != null) {
            true -> startupEventInfo.duration
            false -> null
        }
        val startupThreshold = when (originSession.isColdStart && startupEventInfo != null) {
            true -> startupEventInfo.threshold
            false -> null
        }

        val betaFeatures = when (configService.sdkModeBehavior.isBetaFeaturesEnabled()) {
            false -> null
            else -> BetaFeatures(
                thermalStates = captureDataSafely(thermalStatusService::getCapturedData),
            )
        }

        val endSession = originSession.copy(
            isEndedCleanly = endedCleanly,
            appState = Session.APPLICATION_STATE_FOREGROUND,
            messageType = Session.MESSAGE_TYPE_END,
            eventIds = captureDataSafely {
                eventService.findEventIdsForSession(
                    startTime,
                    endTime
                )
            },
            infoLogIds = captureDataSafely { logMessageService.findInfoLogIds(startTime, endTime) },
            warningLogIds = captureDataSafely {
                logMessageService.findWarningLogIds(
                    startTime,
                    endTime
                )
            },
            errorLogIds = captureDataSafely {
                logMessageService.findErrorLogIds(
                    startTime,
                    endTime
                )
            },
            networkLogIds = captureDataSafely {
                logMessageService.findNetworkLogIds(
                    startTime,
                    endTime
                )
            },
            infoLogsAttemptedToSend = captureDataSafely(logMessageService::getInfoLogsAttemptedToSend),
            warnLogsAttemptedToSend = captureDataSafely(logMessageService::getWarnLogsAttemptedToSend),
            errorLogsAttemptedToSend = captureDataSafely(logMessageService::getErrorLogsAttemptedToSend),
            lastHeartbeatTime = clock.now(),
            properties = captureDataSafely(sessionPropertiesService::getProperties),
            endType = endType,
            unhandledExceptions = captureDataSafely(logMessageService::getUnhandledExceptionsSent),
            webViewInfo = captureDataSafely(webViewService::getCapturedData),
            crashReportId = crashReportId,
            terminationTime = terminationTime,
            isReceivedTermination = receivedTermination,
            endTime = endTimeVal,
            sdkStartupDuration = sdkStartDuration,
            startupDuration = startupDuration,
            startupThreshold = startupThreshold,
            user = captureDataSafely(userService::getUserInfo),
            betaFeatures = betaFeatures,
            symbols = captureDataSafely { nativeThreadSamplerService?.getNativeSymbols() }
        )

        val performanceInfo = performanceInfoService.getSessionPerformanceInfo(
            startTime,
            endTime,
            originSession.isColdStart,
            originSession.isReceivedTermination
        )

        val appInfo = captureDataSafely(metadataService::getAppInfo)
        val deviceInfo = captureDataSafely(metadataService::getDeviceInfo)
        val breadcrumbs = captureDataSafely { breadcrumbService.getBreadcrumbs(startTime, endTime) }

        val endSessionWithAllErrors =
            endSession.copy(exceptionError = internalErrorService.currentExceptionError)

        return SessionMessage(
            session = endSessionWithAllErrors,
            userInfo = endSessionWithAllErrors.user,
            appInfo = appInfo,
            deviceInfo = deviceInfo,
            performanceInfo = performanceInfo.copy(),
            breadcrumbs = breadcrumbs,
            spans = spans
        )
    }

    /**
     * Creates a background activity stop message.
     */
    fun createBackgroundActivityEndMessage(
        activity: Session,
        endTime: Long,
        endType: Session.LifeEventType?,
        crashId: String?
    ): Session {
        val startTime = activity.startTime ?: 0
        return activity.copy(
            appState = Session.APPLICATION_STATE_BACKGROUND,
            messageType = Session.MESSAGE_TYPE_END,
            endTime = endTime,
            eventIds = captureDataSafely {
                eventService.findEventIdsForSession(
                    startTime,
                    endTime
                )
            },
            infoLogIds = captureDataSafely { logMessageService.findInfoLogIds(startTime, endTime) },
            warningLogIds = captureDataSafely {
                logMessageService.findWarningLogIds(
                    startTime,
                    endTime
                )
            },
            errorLogIds = captureDataSafely {
                logMessageService.findErrorLogIds(
                    startTime,
                    endTime
                )
            },
            infoLogsAttemptedToSend = captureDataSafely(logMessageService::getInfoLogsAttemptedToSend),
            warnLogsAttemptedToSend = captureDataSafely(logMessageService::getWarnLogsAttemptedToSend),
            errorLogsAttemptedToSend = captureDataSafely(logMessageService::getErrorLogsAttemptedToSend),
            exceptionError = captureDataSafely(internalErrorService::currentExceptionError),
            lastHeartbeatTime = endTime,
            endType = endType,
            unhandledExceptions = captureDataSafely(logMessageService::getUnhandledExceptionsSent),
            crashReportId = crashId
        )
    }

    /**
     * Create the background session message with the current state of the background activity.
     *
     * @param backgroundActivity      the current state of a background activity
     * @param isBackgroundActivityEnd true if the message is being built for the termination of the background activity
     * @return a background activity message for backend
     */
    fun buildBgActivityMessage(
        backgroundActivity: Session?,
        isBackgroundActivityEnd: Boolean
    ): SessionMessage? {
        if (backgroundActivity != null) {
            val startTime = backgroundActivity.startTime ?: 0L
            val endTime = backgroundActivity.endTime ?: clock.now()
            val isCrash = backgroundActivity.crashReportId != null
            val breadcrumbs = captureDataSafely {
                when {
                    isBackgroundActivityEnd -> breadcrumbService.flushBreadcrumbs()
                    else -> breadcrumbService.getBreadcrumbs(startTime, endTime)
                }
            }
            val spans: List<EmbraceSpanData>? = captureDataSafely {
                when {
                    isBackgroundActivityEnd -> {
                        val appTerminationCause = when {
                            isCrash -> EmbraceAttributes.AppTerminationCause.CRASH
                            else -> null
                        }
                        spansService.flushSpans(appTerminationCause)
                    }

                    else -> spansService.completedSpans()
                }
            }

            return SessionMessage(
                session = backgroundActivity,
                userInfo = backgroundActivity.user,
                appInfo = captureDataSafely(metadataService::getAppInfo),
                deviceInfo = captureDataSafely(metadataService::getDeviceInfo),
                performanceInfo = captureDataSafely {
                    performanceInfoService.getSessionPerformanceInfo(
                        startTime,
                        endTime,
                        java.lang.Boolean.TRUE == backgroundActivity.isColdStart,
                        null
                    )
                },
                breadcrumbs = breadcrumbs,
                spans = spans
            )
        }
        return null
    }
}
