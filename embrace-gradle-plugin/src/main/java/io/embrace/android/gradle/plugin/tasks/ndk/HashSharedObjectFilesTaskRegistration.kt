package io.embrace.android.gradle.plugin.tasks.ndk

import io.embrace.android.gradle.plugin.gradle.registerTask
import io.embrace.android.gradle.plugin.tasks.registration.EmbraceTaskRegistration
import io.embrace.android.gradle.plugin.tasks.registration.RegistrationParams
import org.gradle.api.provider.Provider

class HashSharedObjectFilesTaskRegistration(
    private val compressionTaskProvider: Provider<CompressSharedObjectFilesTask>
) : EmbraceTaskRegistration {
    override fun register(params: RegistrationParams) {
        params.execute()
    }

    fun RegistrationParams.execute() {
        // Register the hashing task
        project.registerTask(
            HashSharedObjectFilesTask.NAME,
            HashSharedObjectFilesTask::class.java,
            data
        ) { task ->
            task.compressedSharedObjectFilesDirectory.set(
                compressionTaskProvider.flatMap { it.compressedSharedObjectFilesDirectory }
            )
            task.architecturesToHashedSharedObjectFilesMap.set(
                project.layout.buildDirectory.file("intermediates/embrace/hashes/${data.name}/hashes.json")
            )
            task.dependsOn(compressionTaskProvider)
        }
    }
}
