package io.embrace.android.gradle.plugin.instrumentation

import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import io.embrace.android.gradle.plugin.gradle.nullSafeMap
import io.embrace.android.gradle.plugin.gradle.tryGetTaskProvider
import io.embrace.android.gradle.plugin.tasks.ndk.EncodeFileToBase64Task
import io.embrace.android.gradle.plugin.tasks.registration.EmbraceTaskRegistration
import io.embrace.android.gradle.plugin.tasks.registration.RegistrationParams
import io.embrace.android.gradle.plugin.util.capitalizedString

class AsmTaskRegistration : EmbraceTaskRegistration {
    override fun register(params: RegistrationParams) {
        params.execute()
    }

    private fun RegistrationParams.execute() {
        try {
            variant.instrumentation.setAsmFramesComputationMode(FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS)
            variant.instrumentation.transformClassesWith(
                EmbraceClassVisitorFactory::class.java,
                InstrumentationScope.ALL
            ) { params: BytecodeInstrumentationParams ->
                params.config.set(
                    variantConfigurationsListProperty.map { variantConfigs ->
                        variantConfigs.first { it.variantName == variant.name }
                    }
                )
                params.disabled.set(
                    project.provider {
                        behavior.isPluginDisabledForVariant(variant.name) || behavior.isInstrumentationDisabledForVariant(variant.name)
                    }
                )
                params.classInstrumentationFilter.set(
                    ClassInstrumentationFilter(behavior.instrumentation.ignoredClasses)
                )
                params.shouldInstrumentFirebaseMessaging.set(behavior.instrumentation.fcmPushNotificationsEnabled)
                params.shouldInstrumentWebview.set(behavior.instrumentation.webviewEnabled)
                params.shouldInstrumentOkHttp.set(behavior.instrumentation.okHttpEnabled)
                params.shouldInstrumentOnLongClick.set(behavior.instrumentation.onLongClickEnabled)
                params.shouldInstrumentOnClick.set(behavior.instrumentation.onClickEnabled)

                project.afterEvaluate {
                    // Find the Asm transformation task by name and make it depend on encodeSharedObjectFilesTask
                    val encodeFileToBase64Task = project.tryGetTaskProvider(
                        "${EncodeFileToBase64Task.NAME}${data.name.capitalizedString()}",
                        EncodeFileToBase64Task::class.java
                    ) ?: return@afterEvaluate

                    val asmTransformationTask = project.tryGetTaskProvider(
                        "transform${variant.name.capitalizedString()}ClassesWithAsm"
                    ) ?: error("Unable to find ASM transformation task for variant ${variant.name}.")

                    asmTransformationTask.configure { it.dependsOn(encodeFileToBase64Task) }

                    params.encodedSharedObjectFilesMap.set(
                        encodeFileToBase64Task.nullSafeMap { encodeTask ->
                            encodeTask.outputFile.asFile.orNull?.let { file ->
                                file.takeIf { it.exists() }
                                    ?.bufferedReader()
                                    ?.use { it.readText() }
                            }
                        }
                    )
                }
            }
        } catch (exception: Exception) {
            project.logger.error("An error has occurred while performing ASM bytecode transformation.", exception)
            if (behavior.failBuildOnUploadErrors.get()) {
                throw exception
            }
        }
    }
}
