package io.embrace.android.gradle.plugin.tasks.ndk

import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import io.embrace.android.gradle.plugin.network.HttpCallResult
import io.embrace.android.gradle.plugin.network.NetworkService
import io.embrace.android.gradle.plugin.tasks.handleHttpCallResult

/**
 * This class is responsible for performing the NDK upload handshake.
 * Given a list of symbols found during the build, it will return a list of symbols that should be uploaded.
 *
 * Example request. SymbolsOnHandshakeRequest:{
 *     "app": "xxxxx",
 *     "token": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
 *     "variant": "release",
 *     "archs": {
 *       "x86_64": {
 *         "libnative.so": "f067beecae4f901c81c642d002810944460efd7b"
 *       },
 *       "x86": {
 *         "libnative.so": "3c84ca4cf150a346db8d195426e520b7a45a0118"
 *       },
 *       "armeabi-v7a": {
 *         "libnative.so": "b621b4bac764b4a1d6166984d63d9958187439a6"
 *       },
 *       "arm64-v8a": {
 *         "libnative.so": "7d8c51cd16d00a369a1b923e1e9aed88c501beee"
 *       }
 *     }
 * }
 *
 * Example response. SymbolsOnHandshakeResponse:{
 *     "archs": {
 *       "arm64-v8a": [
 *         "libnative.so"
 *       ],
 *       "armeabi-v7a": [
 *         "libnative.so"
 *       ],
 *       "x86": [
 *         "libnative.so"
 *       ],
 *       "x86_64": [
 *         "libnative.so"
 *       ]
 *     }
 * }
 * When no symbols are requested, the service will return:
 * {
 *    "archs": {}
 * }
 * and getRequestedSymbols will return null.
 */
class NdkUploadHandshake(
    private val networkService: NetworkService,
) {

    fun getRequestedSymbols(request: NdkUploadHandshakeRequest, failBuildOnUploadErrors: Boolean): Map<String, List<String>>? {
        val result = networkService.postNdkHandshake(
            appId = request.appId,
            handshake = request,
        )
        handleHttpCallResult(result, EmbraceEndpoint.NDK_HANDSHAKE, failBuildOnUploadErrors)

        if (result is HttpCallResult.Success<*>) {
            val response = result.body as? NdkUploadHandshakeResponse ?: return null
            val symbolsToUpload = response.symbols
            return if (symbolsToUpload.isNullOrEmpty()) {
                null
            } else {
                symbolsToUpload
            }
        }
        return null
    }
}
