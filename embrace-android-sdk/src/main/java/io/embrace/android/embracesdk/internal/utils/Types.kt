package io.embrace.android.embracesdk.internal.utils

import android.content.Context
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.injection.AndroidServicesModule
import io.embrace.android.embracesdk.injection.AnrModule
import io.embrace.android.embracesdk.injection.CoreModule
import io.embrace.android.embracesdk.injection.CrashModule
import io.embrace.android.embracesdk.injection.CustomerLogModule
import io.embrace.android.embracesdk.injection.DataCaptureServiceModule
import io.embrace.android.embracesdk.injection.DataContainerModule
import io.embrace.android.embracesdk.injection.DataSourceModule
import io.embrace.android.embracesdk.injection.DeliveryModule
import io.embrace.android.embracesdk.injection.EssentialServiceModule
import io.embrace.android.embracesdk.injection.InitModule
import io.embrace.android.embracesdk.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.injection.PayloadModule
import io.embrace.android.embracesdk.injection.SdkObservabilityModule
import io.embrace.android.embracesdk.injection.SessionModule
import io.embrace.android.embracesdk.injection.StorageModule
import io.embrace.android.embracesdk.injection.SystemServiceModule
import io.embrace.android.embracesdk.ndk.NativeModule
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import io.embrace.android.embracesdk.worker.WorkerThreadModule
import java.io.OutputStream

/**
 * Typealias for a function that writes to an [OutputStream]. This is used to make it
 * easier to pass around logic for serializing data to arbitrary streams.
 */
internal typealias SerializationAction = (outputStream: OutputStream) -> Unit

/**
 * When a local or remote config doesn't exist, this type can be used as a placeholder.
 */
internal typealias UnimplementedConfig = Unit?

/**
 * Function that returns an instance of [CoreModule]. Matches the signature of the constructor for [CoreModuleImpl]
 */
internal typealias CoreModuleSupplier = (context: Context, appFramework: Embrace.AppFramework) -> CoreModule

/**
 * Function that returns an instance of [WorkerThreadModule]. Matches the signature of the constructor for [WorkerThreadModuleImpl]
 */
internal typealias WorkerThreadModuleSupplier = (initModule: InitModule) -> WorkerThreadModule

/**
 * Function that returns an instance of [SystemServiceModule]. Matches the signature of the constructor for [SystemServiceModuleImpl]
 */
internal typealias SystemServiceModuleSupplier = (
    coreModule: CoreModule,
    versionChecker: VersionChecker
) -> SystemServiceModule

/**
 * Function that returns an instance of [AndroidServicesModule]. Matches the signature of the constructor for [AndroidServicesModuleImpl]
 */
internal typealias AndroidServicesModuleSupplier = (
    initModule: InitModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
) -> AndroidServicesModule

/**
 * Function that returns an instance of [StorageModule]. Matches the signature of the constructor for [StorageModuleImpl]
 */
internal typealias StorageModuleSupplier = (
    initModule: InitModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
) -> StorageModule

/**
 * Function that returns an instance of [EssentialServiceModule]. Matches the signature of the constructor for [EssentialServiceModuleImpl]
 */
internal typealias EssentialServiceModuleSupplier = (
    initModule: InitModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
    systemServiceModule: SystemServiceModule,
    androidServicesModule: AndroidServicesModule,
    storageModule: StorageModule,
    customAppId: String?,
    enableIntegrationTesting: Boolean,
    configServiceProvider: Provider<ConfigService?>,
) -> EssentialServiceModule

/**
 * Function that returns an instance of [DataCaptureServiceModule]. Matches the signature of the constructor for
 * [DataCaptureServiceModuleImpl]
 */
internal typealias DataCaptureServiceModuleSupplier = (
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    coreModule: CoreModule,
    systemServiceModule: SystemServiceModule,
    essentialServiceModule: EssentialServiceModule,
    workerThreadModule: WorkerThreadModule,
    versionChecker: VersionChecker
) -> DataCaptureServiceModule

/**
 * Function that returns an instance of [DeliveryModule]. Matches the signature of the constructor for [DeliveryModuleImpl]
 */
internal typealias DeliveryModuleSupplier = (
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
) -> DeliveryModule

/**
 * Function that returns an instance of [AnrModule]. Matches the signature of the constructor for [AnrModuleImpl]
 */

internal typealias AnrModuleSupplier = (
    initModule: InitModule,
    coreModule: CoreModule,
    essentialServiceModule: EssentialServiceModule,
    workerModule: WorkerThreadModule,
) -> AnrModule

/**
 * Function that returns an instance of [SdkObservabilityModule]. Matches the signature of the constructor for [SdkObservabilityModuleImpl]
 */

internal typealias SdkObservabilityModuleSupplier = (
    initModule: InitModule,
    essentialServiceModule: EssentialServiceModule
) -> SdkObservabilityModule

/**
 * Function that returns an instance of [CustomerLogModule]. Matches the signature of the constructor for [CustomerLogModuleImpl]
 */

internal typealias CustomerLogModuleSupplier = (
    initModule: InitModule,
    coreModule: CoreModule,
    openTelemetryModule: OpenTelemetryModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    deliveryModule: DeliveryModule,
    sessionProperties: EmbraceSessionProperties,
    workerThreadModule: WorkerThreadModule,
    payloadModule: PayloadModule,
) -> CustomerLogModule

/**
 * Function that returns an instance of [NativeModule]. Matches the signature of the constructor for [NativeModuleImpl]
 */

internal typealias NativeModuleSupplier = (
    coreModule: CoreModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    deliveryModule: DeliveryModule,
    androidServicesModule: AndroidServicesModule,
    sessionProperties: EmbraceSessionProperties,
    workerThreadModule: WorkerThreadModule
) -> NativeModule

/**
 * Function that returns an instance of [DataContainerModule]. Matches the signature of the constructor for [DataContainerModuleImpl]
 */
internal typealias DataContainerModuleSupplier = (
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
    systemServiceModule: SystemServiceModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    dataCaptureServiceModule: DataCaptureServiceModule,
    anrModule: AnrModule,
    customerLogModule: CustomerLogModule,
    deliveryModule: DeliveryModule,
    nativeModule: NativeModule,
    sessionProperties: EmbraceSessionProperties,
    startTime: Long
) -> DataContainerModule

/**
 * Function that returns an instance of [DataSourceModule]. Matches the signature of the constructor for [DataSourceModuleImpl]
 */

internal typealias DataSourceModuleSupplier = (
    essentialServiceModule: EssentialServiceModule,
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    systemServiceModule: SystemServiceModule,
    androidServicesModule: AndroidServicesModule,
    workerThreadModule: WorkerThreadModule,
) -> DataSourceModule

/**
 * Function that returns an instance of [SessionModule]. Matches the signature of the constructor for [SessionModuleImpl]
 */

internal typealias SessionModuleSupplier = (
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    nativeModule: NativeModule,
    dataContainerModule: DataContainerModule,
    deliveryModule: DeliveryModule,
    sessionProperties: EmbraceSessionProperties,
    dataCaptureServiceModule: DataCaptureServiceModule,
    customerLogModule: CustomerLogModule,
    sdkObservabilityModule: SdkObservabilityModule,
    workerThreadModule: WorkerThreadModule,
    dataSourceModule: DataSourceModule,
    payloadModule: PayloadModule
) -> SessionModule

/**
 * Function that returns an instance of [CrashModule]. Matches the signature of the constructor for [CrashModuleImpl]
 */

internal typealias CrashModuleSupplier = (
    initModule: InitModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    deliveryModule: DeliveryModule,
    nativeModule: NativeModule,
    sessionModule: SessionModule,
    anrModule: AnrModule,
    dataContainerModule: DataContainerModule,
    androidServicesModule: AndroidServicesModule
) -> CrashModule

/**
 * Function that returns an instance of [PayloadModule]. Matches the signature of the constructor for [PayloadModuleImpl]
 */
internal typealias PayloadModuleSupplier = (
    essentialServiceModule: EssentialServiceModule,
    nativeModule: NativeModule,
    otelModule: OpenTelemetryModule,
    sdkObservabilityModule: SdkObservabilityModule
) -> PayloadModule

/**
 * Function that returns an instance of T meant to represent a provider/supplier that does not require any input parameters
 */
internal typealias Provider<T> = () -> T
