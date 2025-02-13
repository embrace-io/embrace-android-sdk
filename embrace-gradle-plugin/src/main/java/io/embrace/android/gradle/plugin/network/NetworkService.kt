package io.embrace.android.gradle.plugin.network

import io.embrace.android.gradle.plugin.buildreporter.BuildTelemetryRequest
import io.embrace.android.gradle.plugin.tasks.common.RequestParams
import io.embrace.android.gradle.plugin.tasks.ndk.NdkUploadHandshakeRequest
import java.io.File

/**
 * Interface used to define a contract for network requests and response callbacks.
 */
interface NetworkService {

    fun postBuildTelemetry(request: BuildTelemetryRequest): HttpCallResult

    fun postNdkHandshake(
        appId: String,
        handshake: NdkUploadHandshakeRequest
    ): HttpCallResult

    fun uploadFile(params: RequestParams, file: File): HttpCallResult

    fun uploadRnSourcemapFile(params: RequestParams, file: File): HttpCallResult

    fun uploadNdkSymbolFile(
        params: RequestParams,
        file: File,
        variantName: String,
        arch: String,
        id: String,
    ): HttpCallResult
}
