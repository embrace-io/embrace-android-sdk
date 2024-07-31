package io.embrace.android.embracesdk.internal.network.logging

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.internal.payload.NetworkCapturedCall
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.utils.Provider
import kotlin.math.max

/**
 * Determines if a network call body should be captured based on the network rules obtained from the remote config.
 */
internal class EmbraceNetworkCaptureService(
    private val sessionIdTracker: SessionIdTracker,
    private val preferencesService: PreferencesService,
    private val networkCaptureDataSource: Provider<NetworkCaptureDataSource>,
    private val configService: ConfigService,
    private val serializer: PlatformSerializer,
    private val logger: EmbLogger
) : NetworkCaptureService {

    companion object {
        const val NETWORK_ERROR_CODE = -1
    }

    private val networkCaptureEncryptionManager = lazy { NetworkCaptureEncryptionManager(logger) }

    /**
     * Returns the network capture rule that matches the URL and method of the network call.
     * The rule must be apply only the number of times set on NetworkCaptureRule.max_count.
     * The rule expire_in field must be > 0. Otherwise the rule is expired and shouldn't be apply.
     */
    override fun getNetworkCaptureRules(url: String, method: String): Set<NetworkCaptureRuleRemoteConfig> {
        val networkCaptureRules = configService.networkBehavior.getNetworkCaptureRules().toMutableSet()
        if (networkCaptureRules.isEmpty()) {
            logger.logDebug("No network capture rules")
            return emptySet()
        }

        // Embrace data endpoint cannot be captured, even if there is a rule for that.
        val appId = configService.sdkModeBehavior.appId
        if (url.contentEquals(configService.sdkEndpointBehavior.getData(appId))) {
            logger.logDebug("Cannot intercept Embrace endpoints")
            return emptySet()
        }

        val applicableRules = networkCaptureRules.filter { rule ->
            rule.method.contains(method) && rule.urlRegex.toRegex().containsMatchIn(url) && rule.expiresIn > 0
        }.toMutableSet()

        val rulesToRemove = mutableSetOf<NetworkCaptureRuleRemoteConfig>()
        applicableRules.forEach { rule ->
            if (preferencesService.isNetworkCaptureRuleOver(rule.id)) {
                rulesToRemove.add(rule)
            }
        }

        networkCaptureRules.removeAll(rulesToRemove)
        applicableRules.removeAll(rulesToRemove)

        logger.logDebug("Capture rule is: $applicableRules")
        return applicableRules
    }

    /**
     * Logs the network captured data only if it matches the duration and status code set on the Network Rule.
     */
    override fun logNetworkCapturedData(
        url: String,
        httpMethod: String,
        statusCode: Int,
        startTime: Long,
        endTime: Long,
        networkCaptureData: NetworkCaptureData?,
        errorMessage: String?
    ) {
        val duration = max(endTime - startTime, 0)

        getNetworkCaptureRules(url, httpMethod).forEach { rule ->

            if (shouldApplyRule(rule, duration, statusCode)) {
                val requestBody = parseBody(networkCaptureData?.capturedRequestBody, rule.maxSize)
                val responseBody =
                    networkCaptureData?.dataCaptureErrorMessage ?: parseBody(networkCaptureData?.capturedResponseBody, rule.maxSize)
                preferencesService.decreaseNetworkCaptureRuleRemainingCount(rule.id, rule.maxCount)

                val capturedNetworkCall = NetworkCapturedCall(
                    duration = duration,
                    endTime = endTime,
                    httpMethod = httpMethod,
                    matchedUrl = rule.urlRegex,
                    requestBody = requestBody,
                    requestBodySize = networkCaptureData?.requestBodySize,
                    requestQuery = networkCaptureData?.requestQueryParams,
                    requestQueryHeaders = networkCaptureData?.requestHeaders,
                    requestSize = networkCaptureData?.requestBodySize,
                    responseBody = responseBody,
                    responseBodySize = networkCaptureData?.responseBodySize,
                    responseHeaders = networkCaptureData?.responseHeaders,
                    responseSize = networkCaptureData?.responseBodySize,
                    responseStatus = statusCode,
                    sessionId = sessionIdTracker.getActiveSessionId(),
                    startTime = startTime,
                    url = url,
                    errorMessage = errorMessage
                )

                val networkLog = getNetworkPayload(capturedNetworkCall)

                // we will create an event with the network request type
                networkCaptureDataSource().logNetworkCapturedCall(
                    networkLog
                )

                // if the network captured match at least one rule criteria, we logged that body and finish the foreach.
                return
            } else {
                logger.logDebug("The captured data doesn't match the rule criteria")
            }
        }
    }

    private fun getNetworkPayload(capturedNetworkCall: NetworkCapturedCall): NetworkCapturedCall {
        return if (configService.networkBehavior.isCaptureBodyEncryptionEnabled()) {
            val encryptedPayload = encryptNetworkCall(capturedNetworkCall)
            NetworkCapturedCall(encryptedPayload = encryptedPayload)
        } else {
            capturedNetworkCall
        }
    }

    private fun encryptNetworkCall(capturedNetworkCall: NetworkCapturedCall): String? {
        val capturePublicKey = configService.networkBehavior.getCapturePublicKey() ?: return null
        return networkCaptureEncryptionManager.value.encrypt(
            serializer.toJson(capturedNetworkCall),
            capturePublicKey
        )
    }

    private fun shouldApplyRule(rule: NetworkCaptureRuleRemoteConfig, duration: Long, statusCode: Int): Boolean {
        return if (rule.statusCodes.contains(statusCode)) {
            val dur = rule.duration
            if (dur == null || dur == 0L) {
                true
            } else {
                duration >= dur
            }
        } else {
            false
        }
    }

    /**
     * Transform the ByteArray body to a String.
     * Trim the String body if needed (maxSize is set in the network rule).
     */
    private fun parseBody(body: ByteArray?, maxSize: Long): String? {
        body?.also {
            val endIndex = if (it.size > maxSize) maxSize else it.size
            return it.decodeToString(0, endIndex.toInt(), false)
        }
        return null
    }
}
