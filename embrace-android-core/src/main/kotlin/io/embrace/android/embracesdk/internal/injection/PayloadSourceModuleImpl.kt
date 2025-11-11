package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.DeviceArchitecture
import io.embrace.android.embracesdk.internal.capture.metadata.EmbraceMetadataService
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.capture.metadata.RnBundleIdTracker
import io.embrace.android.embracesdk.internal.capture.metadata.RnBundleIdTrackerImpl
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
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSourceImpl
import io.embrace.android.embracesdk.internal.envelope.session.OtelPayloadMapper
import io.embrace.android.embracesdk.internal.envelope.session.SessionEnvelopeSource
import io.embrace.android.embracesdk.internal.envelope.session.SessionEnvelopeSourceImpl
import io.embrace.android.embracesdk.internal.envelope.session.SessionPayloadSourceImpl
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.resurrection.PayloadResurrectionService
import io.embrace.android.embracesdk.internal.resurrection.PayloadResurrectionServiceImpl
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.Worker

internal class PayloadSourceModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
    systemServiceModule: SystemServiceModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    configModule: ConfigModule,
    nativeSymbolsProvider: Provider<Map<String, String>?>,
    otelModule: OpenTelemetryModule,
    otelPayloadMapperProvider: Provider<OtelPayloadMapper>,
    deliveryModule: DeliveryModule,
) : PayloadSourceModule {

    override val rnBundleIdTracker: RnBundleIdTracker by singleton {
        RnBundleIdTrackerImpl(
            coreModule.buildInfo,
            coreModule.context,
            configModule.configService,
            androidServicesModule.preferencesService,
            workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker)
        )
    }

    private val sessionPayloadSource by singleton {
        EmbTrace.trace("session-payload-source") {
            SessionPayloadSourceImpl(
                nativeSymbolsProvider,
                otelModule.spanSink,
                otelModule.currentSessionSpan,
                otelModule.spanRepository,
                otelPayloadMapperProvider(),
                essentialServiceModule.processStateService,
                initModule.clock,
                initModule.logger
            )
        }
    }

    private val logPayloadSource by singleton {
        LogPayloadSourceImpl(otelModule.logSink)
    }

    override val sessionEnvelopeSource: SessionEnvelopeSource by singleton {
        SessionEnvelopeSourceImpl(metadataSource, resourceSource, sessionPayloadSource)
    }

    override val logEnvelopeSource: LogEnvelopeSource by singleton {
        LogEnvelopeSourceImpl(metadataSource, resourceSource, logPayloadSource, deliveryModule.cachedLogEnvelopeStore)
    }

    override val deviceArchitecture: DeviceArchitecture by singleton {
        DeviceArchitecture()
    }

    override val hostedSdkVersionInfo: HostedSdkVersionInfo by singleton {
        val store = androidServicesModule.store
        when (configModule.configService.appFramework) {
            AppFramework.REACT_NATIVE -> ReactNativeSdkVersionInfo(store)
            AppFramework.UNITY -> UnitySdkVersionInfo(store)
            AppFramework.FLUTTER -> FlutterSdkVersionInfo(store)
            else -> NativeSdkVersionInfo()
        }
    }

    private val resourceSource by singleton {
        EmbTrace.trace("resource-source") {
            EnvelopeResourceSourceImpl(
                hostedSdkVersionInfo,
                coreModule.appEnvironment.environment,
                EmbTrace.trace("buildInfo") { coreModule.buildInfo },
                EmbTrace.trace("packageInfo") { coreModule.packageVersionInfo },
                configModule.configService.appFramework,
                deviceArchitecture,
                EmbTrace.trace("deviceImpl") {
                    DeviceImpl(
                        systemServiceModule.windowManager,
                        androidServicesModule.store,
                        workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker),
                        initModule.systemInfo,
                        initModule.logger
                    )
                },
                rnBundleIdTracker
            )
        }
    }

    private val metadataSource by singleton {
        EmbTrace.trace("metadata-source") {
            EnvelopeMetadataSourceImpl { essentialServiceModule.userService.getUserInfo() }
        }
    }

    override val metadataService: MetadataService by singleton {
        EmbTrace.trace("metadata-service-init") {
            EmbraceMetadataService(
                lazy { resourceSource },
                coreModule.context,
                lazy { systemServiceModule.storageManager },
                configModule.configService,
                androidServicesModule.preferencesService,
                workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker),
                initModule.clock,
                initModule.logger
            )
        }
    }

    override val payloadResurrectionService: PayloadResurrectionService? by singleton {
        val intakeService = deliveryModule.intakeService ?: return@singleton null
        val cacheStorageService = deliveryModule.cacheStorageService ?: return@singleton null
        val cachedLogEnvelopeStore = deliveryModule.cachedLogEnvelopeStore ?: return@singleton null
        PayloadResurrectionServiceImpl(
            intakeService = intakeService,
            cacheStorageService = cacheStorageService,
            cachedLogEnvelopeStore = cachedLogEnvelopeStore,
            logger = initModule.logger,
            serializer = initModule.jsonSerializer
        )
    }
}
