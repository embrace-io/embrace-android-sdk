package io.embrace.android.gradle.plugin.tasks.reactnative

import com.squareup.moshi.JsonWriter
import io.embrace.android.gradle.plugin.Logger
import io.embrace.android.gradle.plugin.hash.calculateMD5ForFile
import io.embrace.android.gradle.plugin.network.OkHttpNetworkService
import io.embrace.android.gradle.plugin.tasks.BuildResourceWriter
import io.embrace.android.gradle.plugin.tasks.EmbraceUploadTask
import io.embrace.android.gradle.plugin.tasks.EmbraceUploadTaskImpl
import io.embrace.android.gradle.plugin.tasks.handleHttpCallResult
import okio.buffer
import okio.gzip
import okio.sink
import okio.source
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Task to upload React Native sourcemap artefacts to Embrace.
 */
abstract class EmbraceRnSourcemapGeneratorTask @Inject constructor(
    objectFactory: ObjectFactory,
) : EmbraceUploadTask, EmbraceUploadTaskImpl(objectFactory) {

    private val logger = Logger(EmbraceRnSourcemapGeneratorTask::class.java)

    @get:OutputDirectory
    val generatedEmbraceResourcesDirectory: DirectoryProperty = objectFactory.directoryProperty()

    @get:Optional
    @get:InputFile
    val sourcemap: RegularFileProperty = objectFactory.fileProperty()

    @get:Optional
    @get:InputFile
    val bundleFile: RegularFileProperty = objectFactory.fileProperty()

    @get:OutputFile
    val sourcemapAndBundleFile: RegularFileProperty = objectFactory.fileProperty()

    @get:Input
    val reactProperties: MapProperty<String, Any> = objectFactory.mapProperty(
        String::class.java,
        Any::class.java
    ).convention(emptyMap())

    @TaskAction
    fun onRun() {
        val rnFilesFinderUtil = RnFilesFinder(
            reactProperties.get(),
            project.layout.buildDirectory.get().asFile
        )

        val bundleFile = rnFilesFinderUtil.fetchJSBundleFile(
            bundleFile.orNull?.asFile
        )
        if (bundleFile == null) {
            logger.error("Couldn't find the JSBundle. React native files were not uploaded.")
            return
        }

        val bundleId = calculateMD5ForFile(bundleFile)

        /**
         * In old React Native Versions, the source map is not exposed as output in the task.
         * If the source map is not present, we will search for it in the known location
         */
        val sourceMapFile: File? = rnFilesFinderUtil.fetchSourceMapFile(
            sourcemap.orNull?.asFile,
            variantData.get()
        )

        if (sourceMapFile == null || !sourceMapFile.exists()) {
            logger.error("Couldn't find the Source Map. React native files were not uploaded.")
            return
        }

        val sourceMapAndBundleJsonFile = sourcemapAndBundleFile.asFile.get()

        val jsonFile = generateBundleZipFile(
            bundleFile,
            sourceMapFile,
            sourceMapAndBundleJsonFile
        )
        uploadBundleFiles(jsonFile, bundleId)
        injectSymbolsAsResources(bundleId)
    }

    private fun injectSymbolsAsResources(bundleId: String) {
        try {
            val resValues = mapOf(
                BUNDLE_INFO_APP_ID to bundleId
            )
            val dir = File(generatedEmbraceResourcesDirectory.asFile.get(), "values")
            BuildResourceWriter().writeBuildInfoFile(
                File(dir, FILE_RN_INFO_XML),
                resValues
            )
        } catch (ex: IOException) {
            throw IllegalStateException("The build info file generation failed.", ex)
        }
    }

    private fun uploadBundleFiles(jsonFile: File, bundleId: String) {
        val networkService = OkHttpNetworkService(requestParams.get().baseUrl)
        val result = networkService.uploadRnSourcemapFile(
            requestParams.get().copy(buildId = bundleId),
            jsonFile
        )
        handleHttpCallResult(result, requestParams.get())
    }

    private fun generateBundleZipFile(
        bundleFile: File,
        sourceMapFile: File,
        sourceMapAndBundleJsonFile: File,
    ): File {
        try {
            sourceMapAndBundleJsonFile.parentFile.mkdirs()
            sourceMapAndBundleJsonFile.sink().gzip().buffer().use { sink ->
                JsonWriter.of(sink).use { jsonWriter ->
                    with(jsonWriter) {
                        beginObject()
                        name(KEY_NAME_BUNDLE)
                        bundleFile.source().buffer().use { value(it.readUtf8()) }
                        name(KEY_NAME_SOURCE_MAP)
                        sourceMapFile.source().buffer().use { value(it.readUtf8()) }
                        endObject()
                    }
                }
            }
        } catch (e: Exception) {
            val msg = "Failed to generate bundle zip file with bundleFile: $bundleFile and sourceMapFile: $sourceMapFile"
            logger.error(msg)
            throw IllegalStateException(msg, e)
        }
        return sourceMapAndBundleJsonFile
    }

    companion object {
        private const val KEY_NAME_BUNDLE = "bundle"
        private const val KEY_NAME_SOURCE_MAP = "sourcemap"
        private const val FILE_RN_INFO_XML: String = "rn_sourcemap.xml"
        private const val BUNDLE_INFO_APP_ID = "emb_rn_bundle_id"
    }
}
