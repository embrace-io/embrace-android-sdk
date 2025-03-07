package io.embrace.android.gradle.plugin.buildreporter

import io.embrace.android.gradle.plugin.agp.AgpWrapper
import io.embrace.android.gradle.plugin.config.PluginBehavior
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import io.embrace.android.gradle.plugin.network.OkHttpNetworkService
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceSpec
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener

/**
 * Initiates a HTTP request to send build telemetry data to Embrace.
 */
abstract class BuildTelemetryService :
    BuildService<BuildTelemetryService.Params>,
    AutoCloseable,
    OperationCompletionListener {

    interface Params : BuildServiceParameters {
        val request: Property<BuildTelemetryRequest>
        val baseUrl: Property<String>
        val telemetryDisabled: Property<Boolean>
    }

    /**
     * It gets called moments before build is about to finish.
     */
    override fun close() {
        if (parameters.telemetryDisabled.get()) {
            return
        }
        val networkService = OkHttpNetworkService(parameters.baseUrl.get())
        networkService.postBuildTelemetry(parameters.request.get())
    }

    override fun onFinish(event: FinishEvent?) {
    }

    companion object {

        fun register(
            project: Project,
            variantConfigurations: ListProperty<VariantConfig>,
            behavior: PluginBehavior,
            agpWrapper: AgpWrapper,
        ): Provider<BuildTelemetryService> {
            val serviceProvider: Provider<BuildTelemetryService> =
                project.gradle.sharedServices
                    .registerIfAbsent(
                        BuildTelemetryService::class.java.name,
                        BuildTelemetryService::class.java
                    ) { serviceSpec: BuildServiceSpec<Params> ->
                        serviceSpec.parameters { params: Params ->
                            val telemetryProvider = BuildTelemetryCollector().collect(
                                project,
                                behavior,
                                project.providers,
                                variantConfigurations,
                                agpWrapper
                            )
                            params.request.set(telemetryProvider)
                            params.baseUrl.set(behavior.baseUrl)
                            params.telemetryDisabled.set(behavior.isTelemetryDisabled)
                        }
                    }

            // subscribe for tasks events
            project.objects.newInstance(
                BuildEventsListenerRegistryProvider::class.java
            ).getBuildEventsListenerRegistry().onTaskCompletion(serviceProvider)
            return serviceProvider
        }
    }
}
