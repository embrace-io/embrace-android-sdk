package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.capture.envelope.log.LogEnvelopeSource
import io.embrace.android.embracesdk.internal.capture.envelope.log.LogEnvelopeSourceImpl
import io.embrace.android.embracesdk.internal.capture.envelope.log.LogPayloadSourceImpl
import io.embrace.android.embracesdk.internal.capture.envelope.metadata.EnvelopeMetadataSourceImpl
import io.embrace.android.embracesdk.internal.capture.envelope.resource.DeviceImpl
import io.embrace.android.embracesdk.internal.capture.envelope.resource.EnvelopeResourceSourceImpl
import io.embrace.android.embracesdk.internal.capture.envelope.session.SessionEnvelopeSource
import io.embrace.android.embracesdk.internal.capture.envelope.session.SessionEnvelopeSourceImpl
import io.embrace.android.embracesdk.internal.capture.envelope.session.SessionPayloadSourceImpl
import io.embrace.android.embracesdk.internal.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.internal.capture.webview.WebViewService
import io.embrace.android.embracesdk.internal.ndk.NativeModule
import io.embrace.android.embracesdk.internal.session.properties.SessionPropertiesService
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.WorkerName

internal class PayloadModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
    androidServicesModule: io.embrace.android.embracesdk.internal.injection.AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    systemServiceModule: SystemServiceModule,
    workerThreadModule: WorkerThreadModule,
    nativeModule: NativeModule,
    otelModule: OpenTelemetryModule,
    anrModule: AnrModule,
    sessionPropertiesServiceProvider: Provider<SessionPropertiesService>,
    webViewServiceProvider: Provider<WebViewService>,
) : PayloadModule {

    private val backgroundWorker =
        workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION)

    private val metadataSource by singleton {
        EnvelopeMetadataSourceImpl(essentialServiceModule.userService)
    }

    private val resourceSource by singleton {
        EnvelopeResourceSourceImpl(
            essentialServiceModule.hostedSdkVersionInfo,
            AppEnvironment(coreModule.context.applicationInfo).environment,
            coreModule.buildInfo,
            coreModule.packageInfo,
            essentialServiceModule.configService.appFramework,
            essentialServiceModule.deviceArchitecture,
            DeviceImpl(
                systemServiceModule.windowManager,
                androidServicesModule.preferencesService,
                backgroundWorker,
                initModule.systemInfo,
                essentialServiceModule.cpuInfoDelegate,
                initModule.logger
            ),
            essentialServiceModule.metadataService
        )
    }

    private val sessionPayloadSource by singleton {
        SessionPayloadSourceImpl(
            nativeModule.nativeThreadSamplerService,
            otelModule.spanSink,
            otelModule.currentSessionSpan,
            otelModule.spanRepository,
            anrModule.anrOtelMapper,
            nativeModule.nativeAnrOtelMapper,
            initModule.logger,
            webViewServiceProvider,
            sessionPropertiesServiceProvider
        )
    }

    private val logPayloadSource by singleton {
        LogPayloadSourceImpl(
            otelModule.logSink
        )
    }

    override val sessionEnvelopeSource: SessionEnvelopeSource by singleton {
        SessionEnvelopeSourceImpl(metadataSource, resourceSource, sessionPayloadSource)
    }

    override val logEnvelopeSource: LogEnvelopeSource by singleton {
        LogEnvelopeSourceImpl(metadataSource, resourceSource, logPayloadSource)
    }
}
