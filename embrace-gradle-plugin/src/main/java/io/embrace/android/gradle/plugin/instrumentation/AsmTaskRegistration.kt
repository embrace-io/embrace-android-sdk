package io.embrace.android.gradle.plugin.instrumentation

import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import io.embrace.android.gradle.plugin.tasks.registration.EmbraceTaskRegistration
import io.embrace.android.gradle.plugin.tasks.registration.RegistrationParams

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
            }
        } catch (exception: Exception) {
            project.logger.error("An error has occurred while performing ASM bytecode transformation.", exception)
            if (behavior.failBuildOnUploadErrors.get()) {
                throw exception
            }
        }
    }
}
