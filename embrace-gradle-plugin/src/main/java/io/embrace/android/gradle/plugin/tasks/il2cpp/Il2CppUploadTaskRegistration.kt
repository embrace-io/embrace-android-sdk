package io.embrace.android.gradle.plugin.tasks.il2cpp

import io.embrace.android.gradle.plugin.gradle.registerTask
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import io.embrace.android.gradle.plugin.tasks.common.FileCompressionTask
import io.embrace.android.gradle.plugin.tasks.common.MultipartUploadTask
import io.embrace.android.gradle.plugin.tasks.common.RequestParams
import io.embrace.android.gradle.plugin.tasks.registration.EmbraceTaskRegistration
import io.embrace.android.gradle.plugin.tasks.registration.RegistrationParams
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.TaskProvider
import java.io.File

/**
 * Registers all the tasks that are required to upload IL2CPP symbols.
 */
class Il2CppUploadTaskRegistration : EmbraceTaskRegistration {

    companion object {

        /**
         * The output directory for IL2CPP symbol information. Our Unity Editor script copies the
         * files to a consistent location for the convenience of the gradle plugin.
         */
        private const val IL2CPP_SYMBOLS_DIR =
            "unityLibrary/src/main/il2cppOutputProject/Source/il2cppOutput/Symbols"
    }

    override fun register(params: RegistrationParams) {
        with(params) {
            project.afterEvaluate {
                val task = Il2CppTaskSource().fetchTask(project, data) ?: return@afterEvaluate
                configureIl2CppTasks(
                    project,
                    task,
                    data,
                    variantConfigurationsListProperty,
                    baseUrl
                )
            }
        }
    }

    private fun configureIl2CppTasks(
        project: Project,
        ndkTaskProvider: TaskProvider<Task>,
        variant: AndroidCompactedVariantData,
        variantConfigurationsListProperty: ListProperty<VariantConfig>,
        baseUrl: String,
    ) {
        val il2cppSymbolsDir = File(project.rootDir, IL2CPP_SYMBOLS_DIR)
        val variantConfig = variantConfigurationsListProperty.get().first { it.variantName == variant.name }
        val lineNumberCompressionTaskProvider = configureFileCompressionTask(
            project,
            variant,
            Il2CppInfo.LineNumberMap,
            il2cppSymbolsDir,
            variantConfig,
        )
        configureFileUploadTask(
            project,
            variant,
            Il2CppInfo.LineNumberMap,
            ndkTaskProvider,
            lineNumberCompressionTaskProvider,
            variantConfig,
            baseUrl,
        )

        val methodMapCompressionTaskProvider = configureFileCompressionTask(
            project,
            variant,
            Il2CppInfo.MethodMap,
            il2cppSymbolsDir,
            variantConfig,
        )
        configureFileUploadTask(
            project,
            variant,
            Il2CppInfo.MethodMap,
            ndkTaskProvider,
            methodMapCompressionTaskProvider,
            variantConfig,
            baseUrl,
        )
    }

    private fun configureFileCompressionTask(
        project: Project,
        variant: AndroidCompactedVariantData,
        info: Il2CppInfo,
        il2cppSymbolsDir: File,
        variantConfig: VariantConfig,
    ): TaskProvider<FileCompressionTask> {
        val compressionTask = project.registerTask(
            info.compressionTaskName,
            FileCompressionTask::class.java,
            variant
        ) { task: FileCompressionTask ->
            val fileProvider = project.provider {
                File(il2cppSymbolsDir, info.filename)
            }
            task.onlyIf { fileProvider.get().exists() }
            task.originalFile.fileProvider(fileProvider)
            task.compressedFile.convention(
                project.layout.buildDirectory.file(
                    "outputs/embrace/il2cpp/compressed/${variantConfig.variantName}/${info.filename}"
                )
            )
        }
        return compressionTask
    }

    private fun configureFileUploadTask(
        project: Project,
        variant: AndroidCompactedVariantData,
        info: Il2CppInfo,
        ndkTaskProvider: TaskProvider<Task>,
        fileCompressionTask: TaskProvider<FileCompressionTask>,
        variantInfo: VariantConfig,
        baseUrl: String,
    ) {
        val uploadTask = project.registerTask(
            info.uploadTaskName,
            MultipartUploadTask::class.java,
            variant
        ) { task ->
            task.requestParams.set(
                project.provider {
                    RequestParams(
                        appId = variantInfo.embraceConfig?.appId.orEmpty(),
                        apiToken = variantInfo.embraceConfig?.apiToken.orEmpty(),
                        endpoint = info.endpoint,
                        fileName = info.filename,
                        buildId = variantInfo.buildId,
                        baseUrl = baseUrl,
                    )
                }
            )

            // link output of compression task to the input of this task
            // dependencies to mapping file compression will be added automatically
            val fileProvider = fileCompressionTask.map {
                it.compressedFile.asFile.get()
            }
            task.onlyIf { fileProvider.get().exists() }
            task.uploadFile.fileProvider(fileProvider)
        }

        // set us (Embrace) as a dependency of the native obfuscation task
        ndkTaskProvider.configure { task ->
            task.finalizedBy(uploadTask)
        }
    }
}
