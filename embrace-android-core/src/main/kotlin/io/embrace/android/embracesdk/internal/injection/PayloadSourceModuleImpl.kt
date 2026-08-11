package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import android.content.pm.ApplicationInfo
import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.internal.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.internal.capture.metadata.EmbraceMetadataService
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.capture.metadata.RnBundleIdTracker
import io.embrace.android.embracesdk.internal.capture.metadata.RnBundleIdTrackerImpl
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.envelope.log.LogEnvelopeSource
import io.embrace.android.embracesdk.internal.envelope.log.LogEnvelopeSourceImpl
import io.embrace.android.embracesdk.internal.envelope.log.LogPayloadSourceImpl
import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSourceImpl
import io.embrace.android.embracesdk.internal.envelope.metadata.FlutterSdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.metadata.NativeSdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.metadata.ReactNativeSdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.metadata.UnitySdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.resource.DeviceImpl
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSourceImpl
import io.embrace.android.embracesdk.internal.envelope.session.OtelPayloadMapper
import io.embrace.android.embracesdk.internal.envelope.session.SessionPartEnvelopeSource
import io.embrace.android.embracesdk.internal.envelope.session.SessionPartEnvelopeSourceImpl
import io.embrace.android.embracesdk.internal.envelope.session.SessionPartPayloadSourceImpl
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.resurrection.PayloadResurrectionService
import io.embrace.android.embracesdk.internal.resurrection.PayloadResurrectionServiceImpl
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.worker.Worker

class PayloadSourceModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
    essentialServiceModule: EssentialServiceModule,
    configService: ConfigService,
    otelModule: OpenTelemetryModule,
    otelPayloadMapper: OtelPayloadMapper?,
    deliveryModule: DeliveryModule?,
) : PayloadSourceModule {

    override val rnBundleIdTracker: RnBundleIdTracker by lazy {
        RnBundleIdTrackerImpl(
            coreModule.context,
            configService,
            coreModule.store,
            workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker),
        )
    }

    private val partPayloadSource = EmbTrace.trace("session-payload-source") {
        SessionPartPayloadSourceImpl(
            configService.nativeSymbolMap,
            otelModule.currentSessionPartSpan,
            otelModule.spanRepository,
            otelPayloadMapper,
            essentialServiceModule.processStateTracker,
            initModule.clock,
            initModule.logger,
        )
    }

    private val logPayloadSource = LogPayloadSourceImpl(otelModule.logSink)

    override val hostedSdkVersionInfo: HostedSdkVersionInfo = when (configService.appFramework) {
        AppFramework.REACT_NATIVE -> ReactNativeSdkVersionInfo(coreModule.store)
        AppFramework.UNITY -> UnitySdkVersionInfo(coreModule.store)
        AppFramework.FLUTTER -> FlutterSdkVersionInfo(coreModule.store)
        else -> NativeSdkVersionInfo()
    }

    private val appEnvironment: AppEnvironment = AppEnvironment(
        isDebug = with(coreModule.context.applicationInfo) {
            flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        },
    )

    override val resourceSource: EnvelopeResourceSource = EmbTrace.trace("resource-source") {
        EnvelopeResourceSourceImpl(
            hosted = hostedSdkVersionInfo,
            environment = appEnvironment.environment,
            configService = configService,
            device = EmbTrace.trace("deviceImpl") {
                DeviceImpl(
                    windowManagerProvider = { coreModule.context.getSystemServiceSafe(Context.WINDOW_SERVICE) },
                    backgroundWorker = workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker),
                    systemInfo = initModule.systemInfo,
                    logger = initModule.logger,
                )
            },
            rnBundleIdProvider = { rnBundleIdTracker.getReactNativeBundleId() },
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE.toIntOrNull(),
        )
    }

    private val metadataSource = EmbTrace.trace("metadata-source") {
        EnvelopeMetadataSourceImpl { essentialServiceModule.userService.getUserInfo() }
    }

    override val sessionPartEnvelopeSource: SessionPartEnvelopeSource =
        SessionPartEnvelopeSourceImpl(metadataSource, resourceSource, partPayloadSource)

    override val logEnvelopeSource: LogEnvelopeSource =
        LogEnvelopeSourceImpl(metadataSource, resourceSource, logPayloadSource, deliveryModule?.cachedLogEnvelopeStore)

    override val metadataService: MetadataService = EmbTrace.trace("metadata-service-init") {
        EmbraceMetadataService(
            lazyOf(resourceSource),
            coreModule.context,
            configService,
            coreModule.store,
            initModule.clock,
            workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker),
        )
    }

    override val payloadResurrectionService: PayloadResurrectionService? = if (deliveryModule == null) {
        null
    } else {
        PayloadResurrectionServiceImpl(
            intakeService = deliveryModule.intakeService,
            payloadStorageService = deliveryModule.payloadStorageService,
            cacheStorageService = deliveryModule.cacheStorageService,
            cachedLogEnvelopeStore = deliveryModule.cachedLogEnvelopeStore,
            logger = initModule.logger,
            serializer = initModule.jsonSerializer,
        )
    }
}
