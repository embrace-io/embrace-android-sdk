package io.embrace.android.embracesdk.internal.injection

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import io.embrace.android.embracesdk.internal.buildinfo.BuildInfoService
import io.embrace.android.embracesdk.internal.buildinfo.BuildInfoServiceImpl
import io.embrace.android.embracesdk.internal.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.internal.registry.ServiceRegistry

class CoreModuleImpl(
    ctx: Context,
    initModule: InitModule,
) : CoreModule {

    override val context: Context by singleton {
        when (ctx) {
            is Application -> ctx
            else -> ctx.applicationContext
        }
    }

    override val packageVersionInfo: PackageVersionInfo by singleton {
        PackageVersionInfo(context.packageManager.getPackageInfo(context.packageName, 0))
    }

    override val application: Application by singleton { context as Application }

    override val serviceRegistry: ServiceRegistry by singleton {
        ServiceRegistry()
    }

    override val appEnvironment: AppEnvironment by lazy {
        val isDebug: Boolean = with(context.applicationInfo) {
            flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        }
        AppEnvironment(isDebug)
    }

    override val buildInfoService: BuildInfoService by lazy {
        BuildInfoServiceImpl(initModule.instrumentedConfig)
    }
}
