package io.embrace.android.gradle.plugin.tasks.r8

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.Variant
import io.embrace.android.gradle.plugin.agp.AgpUtils.isDexguard
import io.embrace.android.gradle.plugin.gradle.isTaskRegistered
import io.embrace.android.gradle.plugin.gradle.nullSafeMap
import io.embrace.android.gradle.plugin.gradle.registerTask
import io.embrace.android.gradle.plugin.gradle.safeFlatMap
import io.embrace.android.gradle.plugin.gradle.tryGetTaskProvider
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import io.embrace.android.gradle.plugin.tasks.common.FileCompressionTask
import io.embrace.android.gradle.plugin.tasks.common.MultipartUploadTask
import io.embrace.android.gradle.plugin.tasks.common.RequestParams
import io.embrace.android.gradle.plugin.tasks.registration.EmbraceTaskRegistration
import io.embrace.android.gradle.plugin.tasks.registration.RegistrationParams
import io.embrace.android.gradle.plugin.util.capitalizedString
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import java.io.File

class JvmMappingUploadTaskRegistration : EmbraceTaskRegistration {

    companion object {
        private const val COMPRESS_TASK_NAME = "jvmMappingCompressionTaskFor"
        private const val UPLOAD_TASK_NAME = "jvmMappingUploadTaskFor"
        private const val FILE_NAME_MAPPING_TXT = "mapping.txt"
    }

    override fun register(params: RegistrationParams) {
        with(params) {
            project.afterEvaluate {
                val tasks = fetchJvmMappingTasks(project, data)
                tasks.forEach { task ->
                    register(
                        project,
                        data,
                        task,
                        fetchJvmMappingFile(task, variant),
                        baseUrl,
                        variantConfigurationsListProperty
                    )
                }
            }
        }
    }

    private fun register(
        project: Project,
        variant: AndroidCompactedVariantData,
        anchorTask: TaskProvider<Task>,
        mappingFile: Provider<File?>,
        baseUrl: String,
        variantConfigurationsListProperty: ListProperty<VariantConfig>,
    ): TaskProvider<MultipartUploadTask> {
        val anchorTaskWithoutVariant = anchorTask.name.replace(variant.name, "", true)
        val compressionTaskName = "${COMPRESS_TASK_NAME}For${anchorTaskWithoutVariant}OnVariant"
        val compressionTask = project.registerTask(
            compressionTaskName,
            FileCompressionTask::class.java,
            variant
        ) { task: FileCompressionTask ->
            // automatically link as a task dependency
            task.originalFile.fileProvider(mappingFile)
            task.compressedFile.convention(
                project.layout.buildDirectory.file(
                    "outputs/embrace/mapping/compressed/${variant.name}/$FILE_NAME_MAPPING_TXT"
                )
            )
        }

        val uploadTaskName = "${UPLOAD_TASK_NAME}For${anchorTaskWithoutVariant}OnVariant"
        val uploadTask = project.registerTask(
            uploadTaskName,
            MultipartUploadTask::class.java,
            variant
        ) { task ->
            val variantConfig = variantConfigurationsListProperty.get().first { it.variantName == variant.name }
            task.requestParams.set(
                project.provider {
                    RequestParams(
                        appId = variantConfig.embraceConfig?.appId.orEmpty(),
                        apiToken = variantConfig.embraceConfig?.apiToken.orEmpty(),
                        endpoint = EmbraceEndpoint.PROGUARD,
                        fileName = FILE_NAME_MAPPING_TXT,
                        buildId = variantConfig.buildId,
                        baseUrl = baseUrl,
                    )
                }
            )

            // link output of compression task to the input of this task
            // dependencies to mapping file compression will be added automatically
            task.uploadFile.fileProvider(
                compressionTask.nullSafeMap {
                    it.compressedFile.orNull?.asFile
                }
            )
        }

        // set us (Embrace) as a dependency of the obfuscation task
        anchorTask.configure { task ->
            task.finalizedBy(uploadTask)
        }
        return uploadTask
    }

    private fun fetchJvmMappingFile(
        obfuscationTask: TaskProvider<Task>,
        variant: Variant,
    ): Provider<File?> {
        return if (isDexguard(obfuscationTask)) {
            fetchDexguardMappingFile(obfuscationTask)
        } else {
            variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE)
                .map { it.asFile }
        }
    }

    private fun fetchDexguardMappingFile(
        obfuscationTask: TaskProvider<Task>,
    ): Provider<File?> {
        return obfuscationTask.safeFlatMap { task ->
            task.outputs.files.asFileTree.filter {
                it.name == "mapping.txt"
            }.elements.nullSafeMap {
                it.firstOrNull()?.asFile
            }
        }
    }

    /**
     * It fetches all available obfuscation tasks. In practice, a single task will be
     * executed but multiple tasks may be registered and configured. Because of this, we are
     * forced to return a list of tasks since we are in configuration phase.
     */
    private fun fetchJvmMappingTasks(
        project: Project,
        variant: AndroidCompactedVariantData,
    ): List<TaskProvider<Task>> {
        val name = variant.name.capitalizedString()
        val targetObfuscationTasks = listOf(
            "dexguardApk$name",
            "dexguardAab$name",
            "transformClassesAndResourcesWithProguardFor$name",
            "minify${name}WithProguard",
            "transformClassesAndResourcesWithR8For$name",
            "minify${name}WithR8"
        )
        val tasks = targetObfuscationTasks.filter { taskName ->
            isTaskRegistered(project.tryGetTaskProvider(taskName))
        }
        return tasks.mapNotNull { project.tryGetTaskProvider(it) }
    }
}
