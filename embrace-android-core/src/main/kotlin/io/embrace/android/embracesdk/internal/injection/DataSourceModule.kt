package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.arch.DataCaptureOrchestrator
import io.embrace.android.embracesdk.internal.arch.EmbraceFeatureRegistry

/**
 * Declares all the data sources that are used by the Embrace SDK.
 *
 * Data will then automatically be captured by the SDK.
 */
interface DataSourceModule {
    val embraceFeatureRegistry: EmbraceFeatureRegistry
    val dataCaptureOrchestrator: DataCaptureOrchestrator
}
