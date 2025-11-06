package io.embrace.android.embracesdk.internal.network.logging

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.limits.NoopLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.comms.api.ApiUrlBuilder
import io.embrace.android.embracesdk.internal.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.internal.instrumentation.network.HttpNetworkRequest
import io.embrace.android.embracesdk.internal.payload.NetworkCapturedCall
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import kotlin.math.max

internal class NetworkCaptureDataSourceImpl(
    args: InstrumentationArgs,
    private val sessionIdTracker: SessionIdTracker,
    private val keyValueStore: KeyValueStore,
    private val urlBuilder: ApiUrlBuilder?,
    private val serializer: PlatformSerializer,
) : NetworkCaptureDataSource, DataSourceImpl(
    args = args,
    limitStrategy = NoopLimitStrategy,
) {

    private val networkCaptureEncryptionManager = lazy { NetworkCaptureEncryptionManager(logger) }

    internal companion object {
        const val NETWORK_ERROR_CODE = -1
        const val NETWORK_CAPTURE_RULE_PREFIX_KEY = "io.embrace.networkcapturerule"
    }

    override fun recordNetworkRequest(request: HttpNetworkRequest) {
        val body = request.body
        if (body == null || !configService.networkBehavior.isUrlEnabled(request.url)) {
            return
        }
        logNetworkCapturedData(
            request.url, // TODO: This used the non-stripped URL, is that correct?
            request.httpMethod,
            request.statusCode ?: NETWORK_ERROR_CODE,
            request.startTime,
            request.endTime,
            body,
            request.errorMessage
        )
    }

    override fun shouldCaptureNetworkBody(url: String, method: String): Boolean {
        return getNetworkCaptureRules(url, method).isNotEmpty()
    }

    /**
     * Logs the network captured data only if it matches the duration and status code set on the Network Rule.
     */
    private fun logNetworkCapturedData(
        url: String,
        httpMethod: String,
        statusCode: Int,
        startTime: Long,
        endTime: Long,
        requestBody: HttpNetworkRequest.HttpRequestBody,
        errorMessage: String?,
    ) {
        val duration = max(endTime - startTime, 0)

        getNetworkCaptureRules(url, httpMethod).forEach { rule ->

            if (shouldApplyRule(rule, duration, statusCode)) {
                val responseBody =
                    requestBody.dataCaptureErrorMessage ?: parseBody(
                        requestBody.capturedResponseBody,
                        rule.maxSize
                    )
                decreaseNetworkCaptureRuleRemainingCount(rule.id, rule.maxCount)

                val capturedNetworkCall = NetworkCapturedCall(
                    duration = duration,
                    endTime = endTime,
                    httpMethod = httpMethod,
                    matchedUrl = rule.urlRegex,
                    requestBody = parseBody(requestBody.capturedRequestBody, rule.maxSize),
                    requestBodySize = requestBody.requestBodySize,
                    requestQuery = requestBody.requestQueryParams,
                    requestQueryHeaders = requestBody.requestHeaders,
                    requestSize = requestBody.requestBodySize,
                    responseBody = responseBody,
                    responseBodySize = requestBody.responseBodySize,
                    responseHeaders = requestBody.responseHeaders,
                    responseSize = requestBody.responseBodySize,
                    responseStatus = statusCode,
                    sessionId = sessionIdTracker.getActiveSessionId(),
                    startTime = startTime,
                    url = url,
                    errorMessage = errorMessage
                )

                val networkLog = getNetworkPayload(capturedNetworkCall)
                logNetworkCapturedCall(networkLog)

                // if the network captured match at least one rule criteria, we logged that body and finish the foreach.
                return
            }
        }
    }

    /**
     * Creates a log with data from a captured network request.
     */
    internal fun logNetworkCapturedCall(call: NetworkCapturedCall) {
        captureTelemetry {
            addLog(
                SchemaType.NetworkCapturedRequest(
                    duration = call.duration,
                    endTime = call.endTime,
                    httpMethod = call.httpMethod,
                    matchedUrl = call.matchedUrl,
                    networkId = call.networkId,
                    requestBody = call.requestBody,
                    requestBodySize = call.requestBodySize,
                    requestQuery = call.requestQuery,
                    requestQueryHeaders = call.requestQueryHeaders,
                    requestSize = call.requestSize,
                    responseBody = call.responseBody,
                    responseBodySize = call.responseBodySize,
                    responseHeaders = call.responseHeaders,
                    responseSize = call.responseSize,
                    responseStatus = call.responseStatus,
                    sessionId = call.sessionId,
                    startTime = call.startTime,
                    url = call.url,
                    errorMessage = call.errorMessage,
                    encryptedPayload = call.encryptedPayload
                ),
                LogSeverity.INFO,
                call.networkId
            )
        }
    }

    /**
     * Returns the network capture rule that matches the URL and method of the network call.
     * The rule must be apply only the number of times set on NetworkCaptureRule.max_count.
     * The rule expire_in field must be > 0. Otherwise the rule is expired and shouldn't be apply.
     */
    internal fun getNetworkCaptureRules(url: String, method: String): Set<NetworkCaptureRuleRemoteConfig> {
        val networkCaptureRules = configService.networkBehavior.getNetworkCaptureRules().toMutableSet()
        if (networkCaptureRules.isEmpty()) {
            return emptySet()
        }

        // Embrace data endpoint cannot be captured, even if there is a rule for that.
        urlBuilder?.baseDataUrl?.let {
            if (url.startsWith(it)) {
                return emptySet()
            }
        }

        val applicableRules = networkCaptureRules.filter { rule ->
            rule.method.contains(method) && rule.urlRegex.toRegex().containsMatchIn(url) && rule.expiresIn > 0
        }.toMutableSet()

        val rulesToRemove = mutableSetOf<NetworkCaptureRuleRemoteConfig>()
        applicableRules.forEach { rule ->
            if (isNetworkCaptureRuleOver(rule.id)) {
                rulesToRemove.add(rule)
            }
        }

        networkCaptureRules.removeAll(rulesToRemove)
        applicableRules.removeAll(rulesToRemove)

        return applicableRules
    }

    private fun isNetworkCaptureRuleOver(id: String): Boolean {
        return getNetworkCaptureRuleRemainingCount(id, 1) <= 0
    }

    private fun decreaseNetworkCaptureRuleRemainingCount(id: String, maxCount: Int) {
        keyValueStore.edit {
            putInt(NETWORK_CAPTURE_RULE_PREFIX_KEY + id, getNetworkCaptureRuleRemainingCount(id, maxCount) - 1)
        }
    }

    private fun getNetworkCaptureRuleRemainingCount(id: String, maxCount: Int): Int {
        return keyValueStore.getInt(NETWORK_CAPTURE_RULE_PREFIX_KEY + id) ?: maxCount
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
        val capturePublicKey = configService.networkBehavior.getNetworkBodyCapturePublicKey() ?: return null
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
