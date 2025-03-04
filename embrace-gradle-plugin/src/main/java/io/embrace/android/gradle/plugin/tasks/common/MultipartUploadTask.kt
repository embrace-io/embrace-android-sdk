package io.embrace.android.gradle.plugin.tasks.common

import io.embrace.android.gradle.plugin.network.OkHttpNetworkService
import io.embrace.android.gradle.plugin.tasks.EmbraceUploadTask
import io.embrace.android.gradle.plugin.tasks.EmbraceUploadTaskImpl
import io.embrace.android.gradle.plugin.tasks.handleHttpCallResult
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

/**
 * Task in charge of uploading a compressed file as a multipart request.
 */
abstract class MultipartUploadTask @Inject constructor(
    objectFactory: ObjectFactory,
) : EmbraceUploadTask, EmbraceUploadTaskImpl(objectFactory) {

    @get:InputFiles
    @get:SkipWhenEmpty
    val uploadFile: RegularFileProperty = objectFactory.fileProperty()

    @TaskAction
    fun onRun() {
        val params = requestParams.get()
        val networkService = OkHttpNetworkService(params.baseUrl)
        val result = networkService.uploadFile(params, uploadFile.asFile.get())
        handleHttpCallResult(result, params)
    }
}
