package io.embrace.android.embracesdk

import android.content.Context
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.payload.AppFramework


/*** Hooks that get package-private info from Embrace for test infra purposes ***/

internal object EmbraceHooks {

    internal fun start(
        context: Context,
        @Suppress("DEPRECATION") appFramework: Embrace.AppFramework,
        configServiceProvider: (framework: AppFramework) -> ConfigService
    ) {
        Embrace.getImpl().start(context, appFramework, configServiceProvider)
    }

    internal fun getActivityService() = checkNotNull(Embrace.getImpl().activityService)

    internal fun setImpl(impl: EmbraceImpl) {
        Embrace.setImpl(impl)
    }

    internal fun stop() {
        Embrace.getImpl().stop()
    }
}

