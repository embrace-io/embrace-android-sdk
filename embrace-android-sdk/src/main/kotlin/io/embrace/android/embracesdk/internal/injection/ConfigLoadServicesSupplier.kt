package io.embrace.android.embracesdk.internal.injection

import android.content.Context

internal typealias ConfigLoadServicesSupplier = (
    context: Context,
    preStartServices: PreStartServices,
) -> ConfigLoadServices
