package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.DeviceArchitecture
import io.embrace.android.embracesdk.internal.DeviceArchitectureImpl
import io.embrace.android.embracesdk.internal.Systrace
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
import io.embrace.android.embracesdk.internal.envelope.resource.DeviceImpl
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSourceImpl
import io.embrace.android.embracesdk.internal.envelope.session.OtelPayloadMapper
import io.embrace.android.embracesdk.internal.envelope.session.SessionEnvelopeSource
import io.embrace.android.embracesdk.internal.envelope.session.SessionEnvelopeSourceImpl
import io.embrace.android.embracesdk.internal.envelope.session.SessionPayloadSourceImpl
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
    nativeCoreModuleProvider: Provider<NativeCoreModule?>,
    nativeSymbolsProvider: Provider<Map<String, String>?>,
    otelModule: OpenTelemetryModule,
    otelPayloadMapperProvider: Provider<OtelPayloadMapper>
) : PayloadSourceModule {

    override val rnBundleIdTracker: RnBundleIdTracker by singleton {
        RnBundleIdTrackerImpl(
            coreModule.buildInfoService.getBuildInfo(),
            coreModule.context,
            configModule.configService,
            androidServicesModule.preferencesService,
            workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker),
            initModule.logger
        )
    }

    private val sessionPayloadSource by singleton {
        Systrace.traceSynchronous("session-payload-source") {
            SessionPayloadSourceImpl(
                nativeSymbolsProvider,
                otelModule.spanSink,
                otelModule.currentSessionSpan,
                otelModule.spanRepository,
                otelPayloadMapperProvider(),
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
        Systrace.traceSynchronous("resource-source") {
            EnvelopeResourceSourceImpl(
                hostedSdkVersionInfo,
                AppEnvironment(coreModule.context.applicationInfo).environment,
                Systrace.traceSynchronous("buildInfo") { coreModule.buildInfoService.getBuildInfo() },
                Systrace.traceSynchronous("packageInfo") { coreModule.packageVersionInfo },
                configModule.configService.appFramework,
                deviceArchitecture,
                Systrace.traceSynchronous("deviceImpl") {
                    DeviceImpl(
                        systemServiceModule.windowManager,
                        androidServicesModule.preferencesService,
                        workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker),
                        initModule.systemInfo,
                        { nativeCoreModuleProvider()?.cpuInfoDelegate },
                        initModule.logger
                    )
                },
                rnBundleIdTracker
            )
        }
    }

    override val metadataSource by singleton {
        Systrace.traceSynchronous("metadata-source") {
            EnvelopeMetadataSourceImpl { essentialServiceModule.userService.getUserInfo() }
        }
    }

    override val metadataService: MetadataService by singleton {
        Systrace.traceSynchronous("metadata-service-init") {
            EmbraceMetadataService(
                lazy { resourceSource },
                metadataSource,
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
}
