package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.DeviceArchitecture
import io.embrace.android.embracesdk.internal.DeviceArchitectureImpl
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.capture.envelope.resource.DeviceImpl
import io.embrace.android.embracesdk.internal.capture.envelope.session.OtelPayloadMapperImpl
import io.embrace.android.embracesdk.internal.capture.envelope.session.SessionPayloadSourceImpl
import io.embrace.android.embracesdk.internal.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.internal.capture.metadata.EmbraceMetadataService
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.capture.metadata.RnBundleIdTracker
import io.embrace.android.embracesdk.internal.capture.metadata.RnBundleIdTrackerImpl
import io.embrace.android.embracesdk.internal.envelope.log.LogEnvelopeSource
import io.embrace.android.embracesdk.internal.envelope.log.LogEnvelopeSourceImpl
import io.embrace.android.embracesdk.internal.envelope.log.LogPayloadSourceImpl
import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSourceImpl
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSourceImpl
import io.embrace.android.embracesdk.internal.envelope.session.SessionEnvelopeSource
import io.embrace.android.embracesdk.internal.envelope.session.SessionEnvelopeSourceImpl
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.WorkerName

internal class PayloadSourceModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
    systemServiceModule: SystemServiceModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    configModule: ConfigModule,
    nativeModuleProvider: Provider<NativeModule?>,
    otelModule: OpenTelemetryModule,
    anrModule: AnrModule,
) : PayloadSourceModule {

    override val rnBundleIdTracker: RnBundleIdTracker by singleton {
        RnBundleIdTrackerImpl(
            coreModule.buildInfo,
            coreModule.context,
            configModule.configService,
            androidServicesModule.preferencesService,
            workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION),
            initModule.logger
        )
    }

    private val sessionPayloadSource by singleton {
        SessionPayloadSourceImpl(
            { nativeModuleProvider()?.nativeThreadSamplerService?.getNativeSymbols() },
            otelModule.spanSink,
            otelModule.currentSessionSpan,
            otelModule.spanRepository,
            OtelPayloadMapperImpl(anrModule.anrOtelMapper) { nativeModuleProvider()?.nativeAnrOtelMapper },
            initModule.logger
        )
    }

    private val logPayloadSource by singleton {
        LogPayloadSourceImpl(otelModule.logSink)
    }

    override val sessionEnvelopeSource: SessionEnvelopeSource by singleton {
        SessionEnvelopeSourceImpl(metadataSource, resourceSource, sessionPayloadSource)
    }

    override val logEnvelopeSource: LogEnvelopeSource by singleton {
        LogEnvelopeSourceImpl(metadataSource, resourceSource, logPayloadSource)
    }

    override val deviceArchitecture: DeviceArchitecture by singleton {
        DeviceArchitectureImpl()
    }

    override val hostedSdkVersionInfo: HostedSdkVersionInfo by singleton {
        HostedSdkVersionInfo(
            androidServicesModule.preferencesService,
            configModule.configService.appFramework
        )
    }

    override val resourceSource by singleton {
        EnvelopeResourceSourceImpl(
            hostedSdkVersionInfo,
            AppEnvironment(coreModule.context.applicationInfo).environment,
            coreModule.buildInfo,
            coreModule.packageVersionInfo,
            configModule.configService.appFramework,
            deviceArchitecture,
            DeviceImpl(
                systemServiceModule.windowManager,
                androidServicesModule.preferencesService,
                workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION),
                initModule.systemInfo,
                { nativeModuleProvider()?.cpuInfoDelegate },
                initModule.logger
            ),
            rnBundleIdTracker
        )
    }

    override val metadataSource by singleton {
        EnvelopeMetadataSourceImpl(essentialServiceModule.userService::getUserInfo)
    }

    override val metadataService: MetadataService by singleton {
        Systrace.traceSynchronous("metadata-service-init") {
            EmbraceMetadataService(
                resourceSource,
                metadataSource,
                coreModule.context,
                systemServiceModule.storageManager,
                configModule.configService,
                androidServicesModule.preferencesService,
                workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION),
                initModule.clock,
                initModule.logger
            )
        }
    }
}
