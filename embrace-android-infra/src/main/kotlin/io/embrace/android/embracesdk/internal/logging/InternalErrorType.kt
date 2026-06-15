package io.embrace.android.embracesdk.internal.logging

import io.embrace.android.embracesdk.internal.logging.InternalLogger.Severity
import io.embrace.android.embracesdk.internal.logging.InternalLogger.Severity.ERROR

/**
 * Represents a type of internal error that can be recorded for the Embrace SDK's telemetry.
 */
sealed class InternalErrorType(private val severity: Severity) {

    fun shouldCapture(): Boolean = severity >= ERROR

    override fun toString(): String = javaClass.simpleName

    object UncaughtExceptionHandler : InternalErrorType(ERROR)
    object ProcessAeiRecords : InternalErrorType(ERROR)
    object NetworkStatusCaptureFail : InternalErrorType(ERROR)
    object ScreenResCaptureFail : InternalErrorType(ERROR)
    object PeriodicSessionCacheFail : InternalErrorType(ERROR)
    object AppStateCallbackFail : InternalErrorType(ERROR)
    object ThreadBlockageHeartbeatCheckFail : InternalErrorType(ERROR)
    object DataSourceDataCaptureFail : InternalErrorType(ERROR)
    object UserLoadFail : InternalErrorType(ERROR)
    object NativeCrashLoadFail : InternalErrorType(ERROR)
    object NativeCrashResurrectionError : InternalErrorType(ERROR)
    object InvalidNativeSymbols : InternalErrorType(ERROR)
    object NativeHandlerInstallFail : InternalErrorType(ERROR)
    object SafeDataCaptureFail : InternalErrorType(ERROR)
    object ProcessStateSummaryFail : InternalErrorType(ERROR)
    object DeliverySchedulingFail : InternalErrorType(ERROR)
    object PayloadDeliveryFail : InternalErrorType(ERROR)
    object PayloadResurrectionFail : InternalErrorType(ERROR)
    object PayloadResurrectionPayloadFail : InternalErrorType(ERROR)
    object IntakeFail : InternalErrorType(ERROR)
    object IntakeUnexpectedType : InternalErrorType(ERROR)
    object PayloadStorageFail : InternalErrorType(ERROR)
    object InternalInterfaceFail : InternalErrorType(ERROR)
    object NativeReadFail : InternalErrorType(Severity.WARNING)
    object AppLaunchTraceFail : InternalErrorType(Severity.WARNING)
    object UiCallbackFail : InternalErrorType(ERROR)
    object InstrumentationRegFail : InternalErrorType(ERROR)
    object UrlStreamHandlerFactoryInstallFail : InternalErrorType(ERROR)
    object SessionStateCreationFail : InternalErrorType(ERROR)
    object ConnectivityUpdateFailure : InternalErrorType(ERROR)
    object ClockBackwardsShift : InternalErrorType(ERROR)
    object NavControllerTrackingFail : InternalErrorType(ERROR)
    object UserSessionCallbackFail : InternalErrorType(ERROR)
}
