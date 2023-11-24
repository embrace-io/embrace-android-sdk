package io.embrace.android.embracesdk.injection

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.internal.AndroidResourcesService
import io.embrace.android.embracesdk.internal.EmbraceAndroidResourcesService
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import io.embrace.android.embracesdk.internal.spans.SpansService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
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

    /**
     * Reference to the current application.
     */
    val application: Application

    /**
     * The framework the SDK is running on
     */
    val appFramework: AppFramework

    /**
     * Returns an interface that logs messages
     */
    val logger: InternalEmbraceLogger

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
}

internal class CoreModuleImpl(
    ctx: Context,
    override val appFramework: AppFramework,
    spansService: SpansService
) : CoreModule {

    override val context: Context by singleton {
        when (ctx) {
            is Application -> ctx
            else -> ctx.applicationContext
        }
    }

    override val application by singleton { context as Application }

    override val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger

    override val serviceRegistry: ServiceRegistry by singleton {
        ServiceRegistry(logger)
    }

    override val jsonSerializer: EmbraceSerializer by singleton {
        EmbraceSerializer(spansService = SpansService.systraceOnlySpansService)
    }

    override val resources: AndroidResourcesService by singleton {
        EmbraceAndroidResourcesService(context)
    }

    override val isDebug: Boolean by lazy { context.applicationInfo.isDebug() }
}

internal fun ApplicationInfo.isDebug() = flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
