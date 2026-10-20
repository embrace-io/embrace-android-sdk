package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentTrackingService
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionService
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageService
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.sdk.OtelSdkWrapper
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.session.id.SessionIdsProvider
import io.embrace.android.embracesdk.internal.session.lifecycle.LifecycleTracker
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker

/**
 * Test seams for SDK construction, resolved with [resolve]. Every field is null in production.
 *
 * Add a field only when a test needs that seam.
 */
internal class SdkOverrides(
    val appContext: Override<Context>? = null,
    val lifecycleTracker: Override<LifecycleTracker>? = null,
    val networkConnectivityService: Override<NetworkConnectivityService>? = null,
    val deliveryTracer: Override<DeliveryTracer?>? = null,
    val payloadStorageService: Override<PayloadStorageService>? = null,
    val cacheStorageService: Override<PayloadStorageService>? = null,
    val requestExecutionService: Override<RequestExecutionService>? = null,
    val payloadStorageLimit: Override<Int>? = null,
    val threadBlockageService: Override<ThreadBlockageService?>? = null,
    val logOrchestratorWorker: Override<BackgroundWorker>? = null,
    val versionChecker: Override<VersionChecker>? = null,
    val instrumentationRegistry: Override<InstrumentationRegistry>? = null,
    val otelSdkConfig: Override<OtelSdkConfig>? = null,
    val otelSdkWrapper: Override<OtelSdkWrapper>? = null,
    val sessionOrchestrator: Override<SessionOrchestrator>? = null,
    val intakeService: Override<IntakeService>? = null,
    val experimentTrackingService: Override<ExperimentTrackingService>? = null,
    val sessionIdsProvider: Override<SessionIdsProvider>? = null,
    val spanRepository: Override<SpanRepository>? = null,
    val eventBus: Override<EventBus>? = null,
)
