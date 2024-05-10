package io.embrace.android.embracesdk.injection

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.internal.AndroidResourcesService
import io.embrace.android.embracesdk.internal.BuildInfo
import io.embrace.android.embracesdk.internal.BuildInfo.Companion.fromResources
import io.embrace.android.embracesdk.internal.EmbraceAndroidResourcesService
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.registry.ServiceRegistry

/**
 * Contains a core set of dependencies that are required by most services/classes in the SDK.
 * This includes a reference to the application context, a clock, logger, etc...
 */
internal interface CoreModule {

    /**
     * Reference to the context. This will always return the application context so won't leak.
     */
    val context: Context

    val packageInfo: PackageInfo

    /**
     * Reference to the current application.
     */
    val application: Application

    /**
     * The framework the SDK is running on
     */
    val appFramework: AppFramework

    /**
     * Returns the service registry. This is used to register services that need to be closed
     */
    val serviceRegistry: ServiceRegistry

    /**
     * Returns the serializer used to serialize data to JSON
     */
    val jsonSerializer: EmbraceSerializer

    /**
     * Returns an service to retrieve Android resources
     */
    val resources: AndroidResourcesService

    /**
     * Whether the application is a debug build
     */
    val isDebug: Boolean

    val buildInfo: BuildInfo
}

internal class CoreModuleImpl(
    ctx: Context,
    override val appFramework: AppFramework,
    logger: EmbLogger
) : CoreModule {

    override val context: Context by singleton {
        when (ctx) {
            is Application -> ctx
            else -> ctx.applicationContext
        }
    }

    @Suppress("DEPRECATION")
    override val packageInfo: PackageInfo
        get() = context.packageManager.getPackageInfo(context.packageName, 0)

    override val application by singleton { context as Application }

    override val serviceRegistry: ServiceRegistry by singleton {
        ServiceRegistry(logger)
    }

    override val jsonSerializer: EmbraceSerializer by singleton {
        EmbraceSerializer()
    }

    override val resources: AndroidResourcesService by singleton {
        EmbraceAndroidResourcesService(context)
    }

    override val isDebug: Boolean by lazy {
        AppEnvironment(context.applicationInfo).isDebug
    }

    override val buildInfo: BuildInfo by lazy {
        fromResources(resources, context.packageName)
    }
}
