package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.capture.crumbs.BreadcrumbDataSource
import io.embrace.android.embracesdk.internal.capture.telemetry.InternalErrorDataSource
import io.embrace.android.embracesdk.internal.instrumentation.crash.CrashFileMarker
import io.embrace.android.embracesdk.internal.instrumentation.crash.LastRunCrashVerifier

interface FeatureModule {
    val breadcrumbDataSource: BreadcrumbDataSource?
    val internalErrorDataSource: InternalErrorDataSource?
    val lastRunCrashVerifier: LastRunCrashVerifier
    val crashMarker: CrashFileMarker
}
