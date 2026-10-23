package io.embrace.android.embracesdk.internal.logging

import io.embrace.android.embracesdk.internal.logging.ErrorHandler.Severity
import io.embrace.android.embracesdk.internal.logging.ErrorHandler.Severity.ERROR

/**
 * Represents a type of SDK error that can be recorded for the Embrace SDK's telemetry.
 */
sealed class SdkErrorType(private val severity: Severity) {

    fun shouldCapture(): Boolean = severity >= ERROR

    override fun toString(): String = javaClass.simpleName

    object UncaughtExceptionHandler : SdkErrorType(ERROR)
    object CrashTeardownFail : SdkErrorType(ERROR)
    object ProcessAeiRecords : SdkErrorType(ERROR)
    object NetworkStatusCaptureFail : SdkErrorType(ERROR)
    object ScreenResCaptureFail : SdkErrorType(ERROR)
    object PeriodicSessionCacheFail : SdkErrorType(ERROR)
    object SpanTimeoutSweepFail : SdkErrorType(ERROR)
    object SpanStopFail : SdkErrorType(ERROR)
    object AppStateCallbackFail : SdkErrorType(ERROR)
    object ThreadBlockageHeartbeatCheckFail : SdkErrorType(ERROR)
    object DataSourceDataCaptureFail : SdkErrorType(ERROR)
    object UserLoadFail : SdkErrorType(ERROR)
    object NativeCrashLoadFail : SdkErrorType(ERROR)
    object NativeCrashResurrectionError : SdkErrorType(ERROR)
    object InvalidNativeSymbols : SdkErrorType(ERROR)
    object NativeHandlerInstallFail : SdkErrorType(ERROR)
    object SafeDataCaptureFail : SdkErrorType(ERROR)
    object ProcessStateSummaryFail : SdkErrorType(ERROR)
    object DeliverySchedulingFail : SdkErrorType(ERROR)
    object PayloadDeliveryFail : SdkErrorType(ERROR)
    object PayloadResurrectionFail : SdkErrorType(ERROR)
    object PayloadResurrectionPayloadFail : SdkErrorType(ERROR)
    object IntakeFail : SdkErrorType(ERROR)
    object IntakeUnexpectedType : SdkErrorType(ERROR)
    object PayloadStorageFail : SdkErrorType(ERROR)
    object SessionMetadataWriteFail : SdkErrorType(ERROR)
    object SessionPartDirectoryStoreFail : SdkErrorType(ERROR)
    object SessionReconstructionFail : SdkErrorType(ERROR)
    object SessionPartReadFail : SdkErrorType(ERROR)
    object SessionPartWritesCompleteFail : SdkErrorType(ERROR)
    object SessionPartEndMissed : SdkErrorType(ERROR)
    object OrphanedSessionSpan : SdkErrorType(ERROR)
    object DuplicateSpanIds : SdkErrorType(ERROR)
    object CompletedSpansWriteFail : SdkErrorType(ERROR)
    object SpanSnapshotsWriteFail : SdkErrorType(ERROR)
    object InternalInterfaceFail : SdkErrorType(ERROR)
    object PublicApiFail : SdkErrorType(ERROR)
    object NativeReadFail : SdkErrorType(Severity.WARNING)
    object AppLaunchTraceFail : SdkErrorType(Severity.WARNING)
    object UiCallbackFail : SdkErrorType(ERROR)
    object InstrumentationRegFail : SdkErrorType(ERROR)
    object InstrumentationDisableFail : SdkErrorType(ERROR)
    object UrlStreamHandlerFactoryInstallFail : SdkErrorType(ERROR)
    object SessionStateCreationFail : SdkErrorType(ERROR)
    object ConnectivityUpdateFailure : SdkErrorType(ERROR)
    object ClockBackwardsShift : SdkErrorType(ERROR)
    object NavControllerTrackingFail : SdkErrorType(ERROR)
    object UserSessionCallbackFail : SdkErrorType(ERROR)
    object UserInfoCallbackFail : SdkErrorType(ERROR)
    object HttpRequestInfoModifierFail : SdkErrorType(Severity.WARNING)
    object SdkInitPhaseFail : SdkErrorType(ERROR)
    object SdkInitAttributeCaptureFail : SdkErrorType(ERROR)
    object OtelSdkCodeError : SdkErrorType(ERROR)
    object OtelExternalExportFail : SdkErrorType(ERROR)
    object OtelProcessorFail : SdkErrorType(ERROR)
    object SpanOperationFail : SdkErrorType(ERROR)
    object EventBusHandlerFail : SdkErrorType(ERROR)
    object EventBusProducerFail : SdkErrorType(ERROR)
    object SessionPartListenerFail : SdkErrorType(ERROR)
    object DeviceChangeListenerFail : SdkErrorType(ERROR)
    object UserSessionLoadFail : SdkErrorType(ERROR)
    object UserSessionTimerFail : SdkErrorType(ERROR)
    object ResourceCloseFail : SdkErrorType(ERROR)
    object WorkerTaskFail : SdkErrorType(ERROR)

    /**
     * A failure that can be ignored.
     */
    object Ignored : SdkErrorType(Severity.INFO)
}
