package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.SharedObjectLoader

interface NativeCoreModule {
    val sharedObjectLoader: SharedObjectLoader
}
