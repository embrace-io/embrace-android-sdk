package io.embrace.android.embracesdk.internal.injection

import android.app.Application
import android.content.Context
import io.embrace.android.embracesdk.internal.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.internal.envelope.BuildInfo
import io.embrace.android.embracesdk.internal.envelope.CpuAbi
import io.embrace.android.embracesdk.internal.envelope.PackageVersionInfo
import io.embrace.android.embracesdk.internal.registry.ServiceRegistry

/**
 * Contains a core set of dependencies that are required by most services/classes in the SDK.
 * This includes a reference to the application context, a clock, logger, etc...
 */
interface CoreModule {

    /**
     * Reference to the context. This will always return the application context so won't leak.
     */
    val context: Context

    val packageVersionInfo: PackageVersionInfo

    /**
     * Reference to the current application.
     */
    val application: Application

    /**
     * Returns the service registry. This is used to register services that need to be closed
     */
    val serviceRegistry: ServiceRegistry

    /**
     * Whether the application is a debug build
     */
    val appEnvironment: AppEnvironment

    val buildInfo: BuildInfo

    /**
     * The primary CPU architecture. We assume that for the vast majority of devices
     * all CPUs have the same ABI (technically not true, but a reasonable enough for
     * simpler data analysis)
     */
    val cpuAbi: CpuAbi
}
