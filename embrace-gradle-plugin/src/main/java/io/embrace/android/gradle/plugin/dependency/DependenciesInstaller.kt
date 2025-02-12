package io.embrace.android.gradle.plugin.dependency

import io.embrace.android.gradle.plugin.config.PluginBehavior
import io.embrace.embrace_gradle_plugin.BuildConfig
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute

const val INSTALL_EMBRACE_DEPENDENCIES_ATTRIBUTE = "io.embrace.install-dependencies"

/**
 * It installs embrace dependencies (if needed) into customer's project for given variant only.
 *
 * It is important to describe how this works.
 *
 * For embrace-android-sdk dependency, we will add it directly to their own dependencies. These are the
 * dependencies that the customer declared in their project (build.gradle). This dependency will be added as if the
 * customer had manually added in their build.gradle -> implementation "io.embrace:embrace-android-sdk:<version>.
 *
 * It is important to note that if any customer gets an exception while executing this, accusing that dependencies were
 * already resolved, then we won't be able to add our dependencies. This is not our fault, and we should tell the
 * customer to look for whoever is resolving dependencies at configuration time (it could be their own code or another
 * plugin) and change its behavior.
 * It is not suggested, and it is considered wrong to resolve dependencies at configuration time.
 */
fun Project.installDependenciesForVariant(
    variantName: String,
    behavior: PluginBehavior,
) {
    if (!behavior.autoAddEmbraceDependencies) {
        return
    }

    val targetConfigurations = listOf(
        fetchConfiguration("${variantName}CompileClasspath", this),
        fetchConfiguration("${variantName}RuntimeClasspath", this)
    )

    targetConfigurations.forEach { targetConfiguration ->
        targetConfiguration.configure { configuration ->
            try {
                val installEmbraceDependenciesAttributeValue = if (behavior.isPluginDisabledForVariant(variantName)) {
                    // do not install dependencies
                    "false"
                } else {
                    // add embrace core dependency
                    configuration.dependencies.addLater(
                        provider {
                            val embraceCoreSdkMetadata = EmbraceDependencyMetadata.Core(BuildConfig.VERSION)
                            project.dependencies.create(embraceCoreSdkMetadata.gradleShortNomenclature())
                        }
                    )

                    // add embrace core dependency
                    configuration.dependencies.addLater(
                        provider {
                            val embraceOkhttpMetadata = EmbraceDependencyMetadata.OkHttp(BuildConfig.VERSION)
                            project.dependencies.create(embraceOkhttpMetadata.gradleShortNomenclature())
                        }
                    )

                    // true so to tell Gradle to install Embrace dependencies (okhttp, jetpack, etc) through ComponentMetadataRule
                    "true"
                }

                // set through this consumer configuration 's attribute if we want (or not) to install dependencies
                configuration.attributes {
                    it.attribute(
                        Attribute.of(
                            INSTALL_EMBRACE_DEPENDENCIES_ATTRIBUTE,
                            String::class.java
                        ),
                        installEmbraceDependenciesAttributeValue
                    )
                }
            } catch (e: InvalidUserDataException) {
                logger.error(
                    "This happens because someone that gets executed before the embrace gradle plugin is resolving " +
                        "dependencies (either explicit or implicitly) at configuration time. We recommend to find who's " +
                        "doing that, and fix it. Gradle does not recommend resolving dependencies during configuration " +
                        "phase."
                )
                throw e
            }
        }
    }
}

/**
 * It fetches the configuration, or it creates it if it doesn't exist.
 */
private fun fetchConfiguration(configurationName: String, project: Project) = with(project) {
    try {
        return configurations.register(configurationName)
    } catch (e: InvalidUserDataException) {
        logger.debug("Configuration $configurationName already exists.")
        configurations.named(configurationName)
    }
}
