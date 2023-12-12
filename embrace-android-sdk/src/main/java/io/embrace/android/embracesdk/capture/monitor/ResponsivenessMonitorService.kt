package io.embrace.android.embracesdk.capture.monitor

import io.embrace.android.embracesdk.anr.detection.ResponsivenessMonitor
import io.embrace.android.embracesdk.arch.DataCaptureService
import io.embrace.android.embracesdk.payload.ResponsivenessSnapshot

/**
 * A [DataCaptureService] that returns a list of [ResponsivenessMonitor.Snapshot] that represent various components whose responsiveness
 * is of interest.
 */
internal interface ResponsivenessMonitorService : DataCaptureService<List<ResponsivenessSnapshot>>
