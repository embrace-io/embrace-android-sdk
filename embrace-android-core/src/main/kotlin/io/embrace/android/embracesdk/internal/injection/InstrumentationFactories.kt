package io.embrace.android.embracesdk.internal.injection

import android.app.Application
import android.content.Context
import io.embrace.android.embracesdk.internal.InstrumentationArgsImpl
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingService
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateTracker
import io.embrace.android.embracesdk.internal.capture.session.UserSessionPropertiesService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot
import io.embrace.android.embracesdk.internal.session.id.SessionPartTracker
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import java.io.File

@Suppress("LongParameterList")
fun createInstrumentationArgs(
    configService: ConfigService,
    destination: TelemetryDestination,
    errorHandler: ErrorHandler,
    clock: Clock,
    context: Context,
    application: Application,
    storeProvider: () -> KeyValueStore,
    serializer: PlatformSerializer,
    uuidSource: UuidSource,
    ordinalStore: OrdinalStore,
    processIdentifier: String,
    processStateTracker: ProcessStateTracker,
    navigationTrackingService: NavigationTrackingService,
    telemetryService: InternalTelemetryService,
    workerThreadPool: WorkerThreadPool,
    sessionPartTracker: SessionPartTracker,
    userSessionPropertiesService: UserSessionPropertiesService,
    userSessionIdsProvider: () -> String?,
    activeSessionIdsProvider: () -> SessionIdsSnapshot,
    crashMarkerFileProvider: () -> File,
    eventBus: EventBus,
): InstrumentationArgs = InstrumentationArgsImpl(
    configService = configService,
    destination = destination,
    errorHandler = errorHandler,
    clock = clock,
    context = context,
    application = application,
    storeProvider = storeProvider,
    serializer = serializer,
    uuidSource = uuidSource,
    ordinalStore = ordinalStore,
    processIdentifier = processIdentifier,
    processStateTracker = processStateTracker,
    navigationTrackingService = navigationTrackingService,
    telemetryService = telemetryService,
    workerThreadPool = workerThreadPool,
    sessionPartTracker = sessionPartTracker,
    userSessionPropertiesService = userSessionPropertiesService,
    userSessionIdsProvider = userSessionIdsProvider,
    activeSessionIdsProvider = activeSessionIdsProvider,
    crashMarkerFileProvider = crashMarkerFileProvider,
    eventBus = eventBus,
)
