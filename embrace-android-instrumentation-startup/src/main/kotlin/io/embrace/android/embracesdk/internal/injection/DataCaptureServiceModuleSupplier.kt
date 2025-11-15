package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.utils.VersionChecker

/**
 * Function that returns an instance of [DataCaptureServiceModule]. Matches the signature of the constructor for
 * [DataCaptureServiceModuleImpl]
 */
typealias DataCaptureServiceModuleSupplier = (
    args: InstrumentationArgs,
    versionChecker: VersionChecker,
) -> DataCaptureServiceModule

fun createDataCaptureServiceModule(
    args: InstrumentationArgs,
    versionChecker: VersionChecker,
): DataCaptureServiceModule = DataCaptureServiceModuleImpl(
    args,
    versionChecker
)
