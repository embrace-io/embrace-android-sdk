package io.embrace.android.embracesdk.capture.thermalstate

import io.embrace.android.embracesdk.arch.DataCaptureService
import io.embrace.android.embracesdk.payload.ThermalState
import java.io.Closeable

internal interface ThermalStatusService : DataCaptureService<List<ThermalState>?>, Closeable
