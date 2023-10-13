package io.embrace.android.embracesdk.capture.powersave

import io.embrace.android.embracesdk.arch.DataCaptureService
import io.embrace.android.embracesdk.payload.PowerModeInterval
import java.io.Closeable

internal interface PowerSaveModeService : DataCaptureService<List<PowerModeInterval>?>, Closeable
