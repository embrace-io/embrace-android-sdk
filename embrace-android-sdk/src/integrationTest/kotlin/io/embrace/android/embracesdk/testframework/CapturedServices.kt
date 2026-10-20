package io.embrace.android.embracesdk.testframework

import android.content.Context
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentTrackingService
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.injection.WorkerThreadPool
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageService
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.sdk.OtelSdkWrapper
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.session.id.SessionIdsProvider
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.internal.utils.event.EventBus

/**
 * The SDK services that integration tests inspect. Services the test setup owns are passed in; the rest are
 * captured by observe-overrides as the SDK constructs them. Reading one before its created will fail.
 */
internal class CapturedServices(
    val workerThreadPool: WorkerThreadPool,
    val networkConnectivityService: NetworkConnectivityService,
    val errorHandler: ErrorHandler,
    val appContext: Context,
) {
    lateinit var configService: ConfigService
    lateinit var instrumentationRegistry: InstrumentationRegistry
    lateinit var spanRepository: SpanRepository
    lateinit var otelSdkConfig: OtelSdkConfig
    lateinit var otelSdkWrapper: OtelSdkWrapper
    lateinit var sessionIdsProvider: SessionIdsProvider
    lateinit var sessionOrchestrator: SessionOrchestrator
    lateinit var experimentTrackingService: ExperimentTrackingService
    lateinit var payloadStorageService: PayloadStorageService
    lateinit var intakeService: IntakeService
    lateinit var eventBus: EventBus
    var threadBlockageService: ThreadBlockageService? = null

    /**
     * The OTel SDK config, or null if the SDK has not constructed it.
     */
    val otelSdkConfigOrNull: OtelSdkConfig?
        get() = if (::otelSdkConfig.isInitialized) otelSdkConfig else null
}
