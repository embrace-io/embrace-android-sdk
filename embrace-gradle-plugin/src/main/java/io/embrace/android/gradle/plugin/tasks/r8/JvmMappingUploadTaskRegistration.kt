package io.embrace.android.gradle.plugin.tasks.r8

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.Variant
import io.embrace.android.gradle.plugin.agp.AgpUtils.isDexguard
import io.embrace.android.gradle.plugin.gradle.registerTask
import io.embrace.android.gradle.plugin.gradle.safeFlatMap
import io.embrace.android.gradle.plugin.gradle.safeMap
import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import io.embrace.android.gradle.plugin.tasks.common.FileCompressionTask
import io.embrace.android.gradle.plugin.tasks.common.MultipartUploadTask
import io.embrace.android.gradle.plugin.tasks.common.RequestParams
import io.embrace.android.gradle.plugin.tasks.registration.MappingTaskRegistration
import io.embrace.android.gradle.plugin.tasks.registration.RegistrationParams
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import java.io.File

class JvmMappingUploadTaskRegistration : MappingTaskRegistration() {

    companion object {
        private const val COMPRESS_TASK_NAME = "jvmMappingCompressionTaskFor"
        private const val UPLOAD_TASK_NAME = "jvmMappingUploadTaskFor"
        private const val FILE_NAME_MAPPING_TXT = "mapping.txt"
    }

    override fun registerForMappingTask(params: RegistrationParams, anchorTask: TaskProvider<Task>) {
        val anchorTaskWithoutVariant = anchorTaskNameWithoutVariant(anchorTask, params.data)
        val mappingFile = fetchJvmMappingFile(anchorTask, params.variant)

        val compressionTaskName = "${COMPRESS_TASK_NAME}For${anchorTaskWithoutVariant}OnVariant"
        val compressionTask = params.project.registerTask(
            compressionTaskName,
            FileCompressionTask::class.java,
            params.data,
        ) { task: FileCompressionTask ->
            // automatically link as a task dependency
            task.originalFile.fileProvider(mappingFile)
            task.compressedFile.convention(
                params.project.layout.buildDirectory.file(
                    "outputs/embrace/mapping/compressed/${params.data.name}/$anchorTaskWithoutVariant/$FILE_NAME_MAPPING_TXT",
                ),
            )
        }

        val uploadTaskName = "${UPLOAD_TASK_NAME}For${anchorTaskWithoutVariant}OnVariant"
        val uploadTask = params.project.registerTask(
            uploadTaskName,
            MultipartUploadTask::class.java,
            params.data,
        ) { task ->
            val variantConfig = params.variantConfigurationsListProperty.get().first { it.variantName == params.data.name }
            // buildIdProvider is ValueSource-backed: Gradle re-evaluates it on every build even
            // when the configuration cache is active, ensuring a fresh build ID each time.
            task.requestParams.set(
                params.buildIdProvider.map { buildId ->
                    RequestParams(
                        appId = variantConfig.embraceConfig?.appId.orEmpty(),
                        apiToken = variantConfig.embraceConfig?.apiToken.orEmpty(),
                        endpoint = EmbraceEndpoint.PROGUARD,
                        fileName = FILE_NAME_MAPPING_TXT,
                        buildId = buildId,
                        baseUrl = params.behavior.baseUrl,
                        failBuildOnUploadErrors = params.behavior.failBuildOnUploadErrors.get(),
                    )
                },
            )

            // link output of compression task to the input of this task
            // dependencies to mapping file compression will be added automatically
            task.uploadFile.set(
                compressionTask.flatMap { it.compressedFile },
            )
        }

        uploadTask.finalizeAnchorTask(anchorTask)
    }

    private fun fetchJvmMappingFile(
        obfuscationTask: TaskProvider<Task>,
        variant: Variant,
    ): Provider<File> {
        return if (isDexguard(obfuscationTask)) {
            fetchDexguardMappingFile(obfuscationTask)
        } else {
            variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE)
                .map { it.asFile }
        }
    }

    private fun fetchDexguardMappingFile(
        obfuscationTask: TaskProvider<Task>,
    ): Provider<File> {
        return obfuscationTask.safeFlatMap { task ->
            task.outputs.files.asFileTree.filter {
                it.name == "mapping.txt"
            }.elements.safeMap {
                it.firstOrNull()?.asFile
            }
        }
    }
}
