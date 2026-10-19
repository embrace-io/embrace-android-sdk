package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitResourceUsageTracker

internal typealias ModuleGraphSupplier = (
    context: Context,
    preStartServices: PreStartServices,
    configLoadServices: ConfigLoadServices,
    sdkStartTimeMs: Long,
    sdkInitResourceUsageTracker: SdkInitResourceUsageTracker,
) -> InitializedModuleGraph
