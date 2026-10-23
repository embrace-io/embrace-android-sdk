package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import android.content.pm.ApplicationInfo
import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateTracker
import io.embrace.android.embracesdk.internal.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.internal.capture.metadata.EmbraceMetadataService
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.capture.metadata.RnBundleIdTracker
import io.embrace.android.embracesdk.internal.capture.metadata.RnBundleIdTrackerImpl
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.delivery.storage.CachedLogEnvelopeStore
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.envelope.log.LogEnvelopeSource
import io.embrace.android.embracesdk.internal.envelope.log.LogEnvelopeSourceImpl
import io.embrace.android.embracesdk.internal.envelope.log.LogPayloadSourceImpl
import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.internal.envelope.metadata.FlutterSdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.metadata.NativeSdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.metadata.ObservableHostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.metadata.ReactNativeSdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.metadata.UnitySdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.resource.DeviceImpl
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSourceImpl
import io.embrace.android.embracesdk.internal.envelope.session.OtelPayloadMapper
import io.embrace.android.embracesdk.internal.envelope.session.SessionPartEnvelopeSource
import io.embrace.android.embracesdk.internal.envelope.session.SessionPartEnvelopeSourceImpl
import io.embrace.android.embracesdk.internal.envelope.session.SessionPartPayloadSourceImpl
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.otel.logs.LogSink
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.resurrection.PayloadResurrectionService
import io.embrace.android.embracesdk.internal.resurrection.PayloadResurrectionServiceImpl
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.spans.CurrentSessionPartSpan
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker

fun createRnBundleIdTracker(
    context: Context,
    configService: ConfigService,
    store: KeyValueStore,
    worker: BackgroundWorker,
): RnBundleIdTracker = RnBundleIdTrackerImpl(
    { context.assets.open(it) },
    configService,
    store,
    worker,
).apply { startAsyncRetrieval() }

fun createHostedSdkVersionInfo(
    appFramework: AppFramework,
    store: KeyValueStore,
): ObservableHostedSdkVersionInfo = ObservableHostedSdkVersionInfo(
    when (appFramework) {
        AppFramework.REACT_NATIVE -> ReactNativeSdkVersionInfo(store)
        AppFramework.UNITY -> UnitySdkVersionInfo(store)
        AppFramework.FLUTTER -> FlutterSdkVersionInfo(store)
        else -> NativeSdkVersionInfo()
    },
)

fun createEnvelopeResourceSource(
    context: Context,
    configService: ConfigService,
    worker: BackgroundWorker,
    systemInfo: SystemInfo,
    errorHandler: ErrorHandler,
    hostedSdkVersionInfo: ObservableHostedSdkVersionInfo,
    rnBundleIdTracker: Lazy<RnBundleIdTracker>,
    useKotlinSdkProvider: () -> Boolean,
): EnvelopeResourceSource {
    val appEnvironment = EmbTrace.trace("app-environment-init") {
        AppEnvironment(
            isDebug = with(context.applicationInfo) {
                flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            },
        )
    }
    val device = EmbTrace.trace("device-init") {
        DeviceImpl(
            windowManagerProvider = { context.getSystemServiceSafe(Context.WINDOW_SERVICE) },
            backgroundWorker = worker,
            systemInfo = systemInfo,
            errorHandler = errorHandler,
        ).apply { startAsyncRetrieval() }
    }
    return EmbTrace.trace("resource-source-impl-init") {
        EnvelopeResourceSourceImpl(
            hosted = hostedSdkVersionInfo,
            environment = appEnvironment.environment,
            configService = configService,
            device = device,
            rnBundleIdProvider = { rnBundleIdTracker.value.getReactNativeBundleId() },
            useKotlinSdkProvider = useKotlinSdkProvider,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE.toIntOrNull(),
        ).apply {
            device.addChangeListener(::notifyIfChanged)
            hostedSdkVersionInfo.addChangeListener(::notifyIfChanged)

            if (configService.appFramework == AppFramework.REACT_NATIVE) {
                rnBundleIdTracker.value.addChangeListener(::notifyIfChanged)
            }
        }
    }
}

fun createSessionPartEnvelopeSource(
    symbolMap: Map<String, String>?,
    currentSessionPartSpan: CurrentSessionPartSpan,
    spanRepository: SpanRepository,
    otelPayloadMapper: OtelPayloadMapper?,
    processStateTracker: ProcessStateTracker,
    clock: Clock,
    errorHandler: ErrorHandler,
    envelopeMetadataSource: EnvelopeMetadataSource,
    resourceSource: EnvelopeResourceSource,
): SessionPartEnvelopeSource {
    val partPayloadSource = EmbTrace.trace("part-payload-source-init") {
        SessionPartPayloadSourceImpl(
            symbolMap,
            currentSessionPartSpan,
            spanRepository,
            otelPayloadMapper,
            processStateTracker,
            clock,
            errorHandler,
        )
    }
    return SessionPartEnvelopeSourceImpl(envelopeMetadataSource, resourceSource, partPayloadSource)
}

fun createLogEnvelopeSource(
    logSink: LogSink,
    envelopeMetadataSource: EnvelopeMetadataSource,
    resourceSource: EnvelopeResourceSource,
    cachedLogEnvelopeStore: CachedLogEnvelopeStore?,
): LogEnvelopeSource {
    val logPayloadSource = EmbTrace.trace("log-payload-source-init") {
        LogPayloadSourceImpl(logSink)
    }
    return LogEnvelopeSourceImpl(envelopeMetadataSource, resourceSource, logPayloadSource, cachedLogEnvelopeStore)
}

fun createMetadataService(
    resourceSource: EnvelopeResourceSource,
    context: Context,
    configService: ConfigService,
    store: KeyValueStore,
    clock: Clock,
    worker: BackgroundWorker,
): MetadataService = EmbraceMetadataService(lazyOf(resourceSource), context, configService, store, clock, worker)

fun createPayloadResurrectionService(
    intakeService: IntakeService,
    payloadStorageService: PayloadStorageService,
    cacheStorageService: PayloadStorageService,
    cachedLogEnvelopeStore: CachedLogEnvelopeStore,
    errorHandler: ErrorHandler,
    serializer: PlatformSerializer,
): PayloadResurrectionService = PayloadResurrectionServiceImpl(
    intakeService = intakeService,
    payloadStorageService = payloadStorageService,
    cacheStorageService = cacheStorageService,
    cachedLogEnvelopeStore = cachedLogEnvelopeStore,
    errorHandler = errorHandler,
    serializer = serializer,
)
