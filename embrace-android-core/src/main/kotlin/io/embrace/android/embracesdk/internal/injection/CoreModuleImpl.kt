package io.embrace.android.embracesdk.internal.injection

import android.app.Application
import android.content.Context
import io.embrace.android.embracesdk.internal.AndroidResourcesService
import io.embrace.android.embracesdk.internal.BuildInfo
import io.embrace.android.embracesdk.internal.EmbraceAndroidResourcesService
import io.embrace.android.embracesdk.internal.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.payload.PackageVersionInfo
import io.embrace.android.embracesdk.internal.registry.ServiceRegistry

public class CoreModuleImpl(
    ctx: Context,
    logger: EmbLogger
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
        ServiceRegistry(logger)
    }

    override val resources: AndroidResourcesService by singleton {
        EmbraceAndroidResourcesService(context)
    }

    override val isDebug: Boolean by lazy {
        AppEnvironment(context.applicationInfo).isDebug
    }

    override val buildInfo: BuildInfo by lazy {
        BuildInfo.fromResources(resources, context.packageName)
    }
}
