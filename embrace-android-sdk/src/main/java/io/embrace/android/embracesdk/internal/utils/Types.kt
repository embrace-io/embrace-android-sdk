package io.embrace.android.embracesdk.internal.utils

import android.content.Context
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.injection.AndroidServicesModule
import io.embrace.android.embracesdk.injection.CoreModule
import io.embrace.android.embracesdk.injection.DataCaptureServiceModule
import io.embrace.android.embracesdk.injection.DeliveryModule
import io.embrace.android.embracesdk.injection.EssentialServiceModule
import io.embrace.android.embracesdk.injection.InitModule
import io.embrace.android.embracesdk.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.injection.StorageModule
import io.embrace.android.embracesdk.injection.SystemServiceModule
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
 * Defines the constructor for [CoreModuleImpl]
 */
internal typealias CoreModuleSupplier = (Context, Embrace.AppFramework) -> CoreModule

/**
 * Defines the constructor for [WorkerThreadModuleImpl]
 */
internal typealias WorkerThreadModuleSupplier = (InitModule) -> WorkerThreadModule

/**
 * Defines the constructor for [SystemServiceModuleImpl]
 */
internal typealias SystemServiceModuleSupplier = (CoreModule, VersionChecker) -> SystemServiceModule

/**
 * Defines the constructor for [AndroidServicesModuleImpl]
 */
internal typealias AndroidServicesModuleSupplier = (InitModule, CoreModule, WorkerThreadModule) -> AndroidServicesModule

/**
 * Defines the constructor for [StorageModuleImpl]
 */
internal typealias StorageModuleSupplier = (InitModule, CoreModule, WorkerThreadModule) -> StorageModule

/**
 * Defines the constructor for [EssentialServiceModuleImpl]
 */
internal typealias EssentialServiceModuleSupplier = (
    InitModule,
    CoreModule,
    WorkerThreadModule,
    SystemServiceModule,
    AndroidServicesModule,
    StorageModule,
    String?,
    Boolean,
    () -> ConfigService?
) -> EssentialServiceModule

/**
 * Defines the constructor for [DataCaptureServiceModuleImpl]
 */
internal typealias DataCaptureServiceModuleSupplier = (
    InitModule,
    OpenTelemetryModule,
    CoreModule,
    SystemServiceModule,
    EssentialServiceModule,
    WorkerThreadModule,
    VersionChecker
) -> DataCaptureServiceModule

/**
 * Defines the constructor for [DeliveryModuleImpl]
 */
internal typealias DeliveryModuleSupplier = (CoreModule, WorkerThreadModule, StorageModule, EssentialServiceModule) -> DeliveryModule
