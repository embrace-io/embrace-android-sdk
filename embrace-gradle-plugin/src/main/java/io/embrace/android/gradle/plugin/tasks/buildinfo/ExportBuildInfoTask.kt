package io.embrace.android.gradle.plugin.tasks.buildinfo

import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import io.embrace.android.gradle.plugin.tasks.EmbraceTask
import io.embrace.android.gradle.plugin.util.serialization.MoshiSerializer
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/**
 * Writes the parameters that would be used to upload the mapping file and NDK symbols for a
 * variant into a JSON file, so they can be reused by external tooling.
 */
@DisableCachingByDefault(because = "Writes build metadata and does not benefit from caching")
abstract class ExportBuildInfoTask @Inject constructor(
    objectFactory: ObjectFactory,
) : DefaultTask(), EmbraceTask {

    @get:Input
    override val variantData: Property<AndroidCompactedVariantData> =
        objectFactory.property(AndroidCompactedVariantData::class.java)

    private val serializer = MoshiSerializer()

    @get:Input
    val buildId: Property<String> = objectFactory.property(String::class.java)

    @get:Input
    val appId: Property<String> = objectFactory.property(String::class.java)

    @get:OutputFile
    val outputFile: RegularFileProperty = objectFactory.fileProperty()

    @TaskAction
    fun onRun() {
        val buildInfo = BuildInfoExport(
            buildId = buildId.get(),
            appId = appId.get(),
            variantName = variantData.get().name,
        )
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            outputStream().use { serializer.toJson(buildInfo, BuildInfoExport::class.java, it) }
        }
    }

    companion object {
        const val NAME: String = "exportBuildInfoTaskFor"
        const val FILE_NAME: String = "embrace-build-info.json"
    }
}
