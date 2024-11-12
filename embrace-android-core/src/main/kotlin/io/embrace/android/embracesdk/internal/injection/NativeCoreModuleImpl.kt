package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.SharedObjectLoaderImpl

internal class NativeCoreModuleImpl(initModule: InitModule) : NativeCoreModule {

    override val sharedObjectLoader: SharedObjectLoader by singleton {
        SharedObjectLoaderImpl(initModule.logger)
    }
}
