package io.embrace.android.embracesdk.internal.capture.session

import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.behavior.REDACTED_LABEL
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.telemetry.TelemetryService
import io.embrace.android.embracesdk.internal.utils.PropertyUtils

internal class UserSessionPropertiesServiceImpl(
    private val store: KeyValueStore,
    private val configService: ConfigService,
    destination: TelemetryDestination,
    private val telemetryService: TelemetryService,
) : UserSessionPropertiesService {

    private var listener: ((Map<String, String>) -> Unit)? = null
    private val props = EmbraceUserSessionProperties(store, configService, destination, telemetryService)

    override fun addProperty(originalKey: String, originalValue: String, permanent: Boolean): Boolean {
        if (!isValidKey(originalKey)) {
            return false
        }
        val sanitizedKey = PropertyUtils.truncate(originalKey, SESSION_PROPERTY_KEY_LIMIT)
        if (sanitizedKey != originalKey) {
            telemetryService.trackAppliedLimit("session_property_key", AppliedLimitType.TRUNCATE_STRING)
        }

        if (!isValidValue(originalValue)) {
            return false
        }

        val sanitizedValue = if (configService.sensitiveKeysBehavior.isSensitiveKey(sanitizedKey)) {
            REDACTED_LABEL
        } else {
            val truncatedValue = PropertyUtils.truncate(originalValue, SESSION_PROPERTY_VALUE_LIMIT)
            if (truncatedValue != originalValue) {
                telemetryService.trackAppliedLimit("session_property_value", AppliedLimitType.TRUNCATE_STRING)
            }
            truncatedValue
        }

        val scope = when {
            permanent -> PropertyScope.PERMANENT
            else -> PropertyScope.USER_SESSION
        }
        val added = props.add(sanitizedKey, sanitizedValue, scope)
        if (added) {
            listener?.invoke(props.get())
        }
        return added
    }

    override fun removeProperty(originalKey: String): Boolean {
        if (!isValidKey(originalKey)) {
            return false
        }
        val sanitizedKey = PropertyUtils.truncate(originalKey, SESSION_PROPERTY_KEY_LIMIT)

        val removed = props.remove(sanitizedKey)
        if (removed) {
            listener?.invoke(props.get())
        }
        return removed
    }

    override fun getProperties(): Map<String, String> = props.get()

    override fun cleanupAfterSessionEnd() {
        props.onNewUserSession()
    }

    override fun prepareForNewSession() {
        props.addPropsForNewSessionSpan()
    }

    override fun addChangeListener(listener: (Map<String, String>) -> Unit) {
        this.listener = listener
    }

    private fun isValidKey(key: String?): Boolean = !key.isNullOrEmpty()

    private fun isValidValue(key: String?): Boolean = key != null

    private companion object {
        /**
         * The maximum number of characters of a session property key
         */
        private const val SESSION_PROPERTY_KEY_LIMIT = 128

        /**
         * The maximum number of characters of a session property value
         */
        private const val SESSION_PROPERTY_VALUE_LIMIT = 1024
    }
}
