package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import io.embrace.android.embracesdk.internal.instance.BufferingSdkInstance

internal typealias ModuleGraphSupplier = (
    context: Context,
    bootstrapServices: BootstrapServices,
    buffer: BufferingSdkInstance,
) -> InitializedModuleGraph
