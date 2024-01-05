package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.capture.PerformanceInfoService
import io.embrace.android.embracesdk.capture.crumbs.BreadcrumbService
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.event.EventService
import io.embrace.android.embracesdk.event.LogMessageService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.spans.EmbraceAttributes
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.SpansService
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.logging.InternalErrorService
import io.embrace.android.embracesdk.payload.BackgroundActivity
import io.embrace.android.embracesdk.payload.BackgroundActivityMessage
import io.embrace.android.embracesdk.prefs.PreferencesService

internal class BackgroundActivityCollator(
    private val userService: UserService,
    private val preferencesService: PreferencesService,
    private val eventService: EventService,
    private val logMessageService: LogMessageService,
    private val internalErrorService: InternalErrorService,
    private val breadcrumbService: BreadcrumbService,
    private val metadataService: MetadataService,
    private val performanceInfoService: PerformanceInfoService,
    private val spansService: SpansService,
    private val clock: Clock
) {

    fun createStartMessage(
        startTime: Long,
        coldStart: Boolean,
        startType: BackgroundActivity.LifeEventType
    ): BackgroundActivity {
        return BackgroundActivity(
            sessionId = Uuid.getEmbUuid(),
            startTime = startTime,
            appState = EmbraceBackgroundActivityService.APPLICATION_STATE_BACKGROUND,
            isColdStart = coldStart,
            startType = startType,
            user = captureDataSafely(userService::loadUserInfoFromDisk),
            number = preferencesService
                .incrementAndGetBackgroundActivityNumber()
        )
    }

    fun createStopMessage(
        activity: BackgroundActivity,
        endTime: Long,
        endType: BackgroundActivity.LifeEventType?,
        crashId: String?
    ): BackgroundActivity {
        val startTime = activity.startTime ?: 0
        return activity.copy(
            appState = EmbraceBackgroundActivityService.APPLICATION_STATE_BACKGROUND,
            messageType = EmbraceBackgroundActivityService.MESSAGE_TYPE_END,
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
            errorLogIds = captureDataSafely { logMessageService.findErrorLogIds(startTime, endTime) },
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
        backgroundActivity: BackgroundActivity?,
        isBackgroundActivityEnd: Boolean
    ): BackgroundActivityMessage? {
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

            return BackgroundActivityMessage(
                backgroundActivity = backgroundActivity,
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
