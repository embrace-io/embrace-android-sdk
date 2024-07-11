package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.capture.envelope.log.LogEnvelopeSource
import io.embrace.android.embracesdk.capture.envelope.log.LogEnvelopeSourceImpl
import io.embrace.android.embracesdk.capture.envelope.log.LogPayloadSourceImpl
import io.embrace.android.embracesdk.capture.envelope.metadata.EnvelopeMetadataSourceImpl
import io.embrace.android.embracesdk.capture.envelope.resource.DeviceImpl
import io.embrace.android.embracesdk.capture.envelope.resource.EnvelopeResourceSourceImpl
import io.embrace.android.embracesdk.capture.envelope.session.SessionEnvelopeSource
import io.embrace.android.embracesdk.capture.envelope.session.SessionEnvelopeSourceImpl
import io.embrace.android.embracesdk.capture.envelope.session.SessionPayloadSourceImpl
import io.embrace.android.embracesdk.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.capture.webview.WebViewService
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.ndk.NativeModule
import io.embrace.android.embracesdk.session.properties.SessionPropertiesService
import io.embrace.android.embracesdk.worker.WorkerName
import io.embrace.android.embracesdk.worker.WorkerThreadModule

/**
 * Modules containing classes that generate the payloads.
 */
internal interface PayloadModule {
    val sessionEnvelopeSource: SessionEnvelopeSource
    val logEnvelopeSource: LogEnvelopeSource
}

internal class PayloadModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
    androidServicesModule: AndroidServicesModule,
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
