@file:JvmName("AsmTasks")

package io.embrace.android.gradle.plugin.instrumentation

import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.variant.AndroidComponentsExtension
import io.embrace.android.gradle.plugin.config.PluginBehavior
import io.embrace.android.gradle.plugin.extension.EmbraceExtensionInternal
import org.gradle.api.Project
import org.gradle.api.UnknownDomainObjectException

/**
 * Registers an ASM class visitor for all build variants, which ensures that the
 * relevant classes are instrumented.
 */
fun registerAsmTasks(
    project: Project,
    behavior: PluginBehavior
) {
    // register for asm
    project.extensions.getByType(AndroidComponentsExtension::class.java).onVariants { variant ->
        val scope = behavior.instrumentation.scope
        project.logger.info("Registered ASM task for ${variant.name} with scope=${scope.name}")

        // compute frames automatically only for modified methods
        variant.instrumentation.setAsmFramesComputationMode(
            FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS
        )
        try {
            // register the ASM class visitor factory
            variant.instrumentation.transformClassesWith(
                EmbraceClassVisitorFactory::class.java,
                scope
            ) { params: BytecodeInstrumentationParams ->
                project.logger.debug("Configuring ASM instrumentation")
                val embraceExtensionInternal = checkNotNull(
                    project.extensions.findByType(EmbraceExtensionInternal::class.java)
                )

                params.config.set(
                    project.provider {
                        val variantExtension = try {
                            embraceExtensionInternal.variants.getByName(variant.name)
                        } catch (exc: UnknownDomainObjectException) {
                            project.logger.lifecycle(
                                "Variant ${variant.name} not found in EmbraceExtensionInternal." +
                                    "Following variants are available: ${
                                        embraceExtensionInternal.variants.asMap.values.joinToString(
                                            ","
                                        )
                                    }"
                            )
                            throw exc
                        }

                        variantExtension.config.get()
                    }
                )
                params.logLevel.set(
                    project.provider {
                        behavior.logLevel
                    }
                )
                params.disabled.set(
                    project.provider {
                        behavior.isPluginDisabledForVariant(variant.name) ||
                            behavior.isInstrumentationDisabledForVariant(variant.name)
                    }
                )
                params.classInstrumentationFilter.set(
                    ClassInstrumentationFilter(behavior.instrumentation.ignoredClasses)
                )
                params.invalidate.set(
                    when {
                        behavior.instrumentation.invalidateBytecode -> System.currentTimeMillis()
                        else -> -1L // use a predictable input each time
                    }
                )
                params.shouldInstrumentFirebaseMessaging.set(behavior.instrumentation.fcmPushNotificationsEnabled)
                params.shouldInstrumentWebview.set(behavior.instrumentation.webviewEnabled)
                params.shouldInstrumentOkHttp.set(behavior.instrumentation.okHttpEnabled)
                params.shouldInstrumentOnLongClick.set(behavior.instrumentation.onLongClickEnabled)
                params.shouldInstrumentOnClick.set(behavior.instrumentation.onClickEnabled)
            }
            project.logger.debug("Asm transformClassesWith successfully called.")
        } catch (e: TransformClassesWithReflectionException) {
            project.logger.warn(
                "There was a reflection issue while performing ASM bytecode transformation.\nThis " +
                    "shouldn't affect build output.",
                e
            )
        }
    }
}
