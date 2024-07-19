package io.embrace.android.embracesdk.internal.injection

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import io.embrace.android.embracesdk.internal.AndroidResourcesService
import io.embrace.android.embracesdk.internal.BuildInfo
import io.embrace.android.embracesdk.internal.registry.ServiceRegistry

/**
 * Contains a core set of dependencies that are required by most services/classes in the SDK.
 * This includes a reference to the application context, a clock, logger, etc...
 */
public interface CoreModule {

    /**
     * Reference to the context. This will always return the application context so won't leak.
     */
    public val context: Context

    public val packageInfo: PackageInfo

    /**
     * Reference to the current application.
     */
    public val application: Application

    /**
     * Returns the service registry. This is used to register services that need to be closed
     */
    public val serviceRegistry: ServiceRegistry

    /**
     * Returns an service to retrieve Android resources
     */
    public val resources: AndroidResourcesService

    /**
     * Whether the application is a debug build
     */
    public val isDebug: Boolean

    public val buildInfo: BuildInfo
}
