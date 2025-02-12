package io.embrace.android.gradle.plugin.tasks.ndk

import io.embrace.android.gradle.plugin.Logger
import io.embrace.android.gradle.plugin.network.HttpCallResult
import io.embrace.android.gradle.plugin.network.NetworkService

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
    private val networkService: NetworkService
) {
    private val logger = Logger(NdkUploadHandshake::class.java)

    fun getRequestedSymbols(request: NdkUploadHandshakeRequest): Map<String, List<String>>? {
        try {
            val uploadResult = networkService.postNdkHandshake(
                appId = request.appId,
                handshake = request,
            )
            if (uploadResult is HttpCallResult.Success<*>) {
                val response = uploadResult.body as? NdkUploadHandshakeResponse ?: return null
                val symbolsToUpload = response.symbols
                return if (symbolsToUpload.isNullOrEmpty()) {
                    logger.info("No NDK files requested. Skipping NDK symbols upload.")
                    null
                } else {
                    logger.info("Requested NDK symbols: " + response.symbols)
                    symbolsToUpload
                }
            }
            return null
        } catch (ex: Exception) {
            logger.error("Failed to perform NDK Handshake. ", ex)
            return null
        }
    }
}
