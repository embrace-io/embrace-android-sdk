package io.embrace.android.gradle.plugin.instrumentation

import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import io.embrace.android.gradle.plugin.EmbraceLogger
import io.embrace.android.gradle.plugin.gradle.lazyTaskLookup
import io.embrace.android.gradle.plugin.gradle.safeFlatMap
import io.embrace.android.gradle.plugin.tasks.ndk.EncodeFileToBase64Task
import io.embrace.android.gradle.plugin.tasks.reactnative.GenerateRnSourcemapTask
import io.embrace.android.gradle.plugin.tasks.registration.EmbraceTaskRegistration
import io.embrace.android.gradle.plugin.tasks.registration.RegistrationParams
import io.embrace.android.gradle.plugin.util.capitalizedString

class AsmTaskRegistration : EmbraceTaskRegistration {

    private val logger = EmbraceLogger(AsmTaskRegistration::class.java)

    override fun register(params: RegistrationParams) {
        params.execute()
    }

    private fun RegistrationParams.execute() {
        try {
            logger.info("Setting up ASM transformation.")
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
                params.shouldInstrumentAutoSdkInitialization.set(behavior.instrumentation.autoSdkInitializationEnabled)
                params.shouldInstrumentOkHttp.set(behavior.instrumentation.okHttpEnabled)
                params.shouldInstrumentOnLongClick.set(behavior.instrumentation.onLongClickEnabled)
                params.shouldInstrumentOnClick.set(behavior.instrumentation.onClickEnabled)
                params.applicationInitTimingEnabled.set(behavior.instrumentation.applicationInitTimingEnabled)

                val encodeFileToBase64Task = project.lazyTaskLookup<EncodeFileToBase64Task>(
                    "${EncodeFileToBase64Task.NAME}${data.name.capitalizedString()}"
                )

                params.encodedSharedObjectFilesMap.set(
                    encodeFileToBase64Task.safeFlatMap {
                        it?.outputFile ?: project.provider { null }
                    }
                )

                val reactNativeTask = project.lazyTaskLookup<GenerateRnSourcemapTask>(
                    "${GenerateRnSourcemapTask.NAME}${data.name.capitalizedString()}"
                )

                params.reactNativeBundleId.set(
                    reactNativeTask.safeFlatMap {
                        it?.bundleIdOutputFile ?: project.provider { null }
                    }
                )
            }
        } catch (exception: Exception) {
            logger.error("An error has occurred while performing ASM bytecode transformation.", exception)
            if (behavior.failBuildOnUploadErrors.get()) {
                throw exception
            }
        }
    }
}
