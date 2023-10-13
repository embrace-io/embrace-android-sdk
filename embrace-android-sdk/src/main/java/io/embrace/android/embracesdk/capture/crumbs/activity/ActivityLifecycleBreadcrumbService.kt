package io.embrace.android.embracesdk.capture.crumbs.activity

import io.embrace.android.embracesdk.arch.DataCaptureService
import io.embrace.android.embracesdk.payload.ActivityLifecycleData

internal interface ActivityLifecycleBreadcrumbService :
    DataCaptureService<List<ActivityLifecycleData>?>
