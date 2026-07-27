package io.embrace.android.embracesdk.internal.config.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Overrides for the limits applied to OTel data capture. Any value that is not specified falls back
 * to the limit declared by the local config.
 */
@Serializable
data class OtelLimitsRemoteConfig(

    @SerialName("max_internal_name_length")
    val maxInternalNameLength: Int? = null,

    @SerialName("max_name_length")
    val maxNameLength: Int? = null,

    @SerialName("max_custom_event_count")
    val maxCustomEventCount: Int? = null,

    @SerialName("max_system_event_count")
    val maxSystemEventCount: Int? = null,

    @SerialName("max_custom_attribute_count")
    val maxCustomAttributeCount: Int? = null,

    @SerialName("max_system_attribute_count")
    val maxSystemAttributeCount: Int? = null,

    @SerialName("max_event_attribute_count")
    val maxEventAttributeCount: Int? = null,

    @SerialName("max_custom_link_count")
    val maxCustomLinkCount: Int? = null,

    @SerialName("max_system_link_count")
    val maxSystemLinkCount: Int? = null,

    @SerialName("max_internal_attribute_key_length")
    val maxInternalAttributeKeyLength: Int? = null,

    @SerialName("max_internal_attribute_value_length")
    val maxInternalAttributeValueLength: Int? = null,

    @SerialName("max_custom_attribute_key_length")
    val maxCustomAttributeKeyLength: Int? = null,

    @SerialName("max_custom_attribute_value_length")
    val maxCustomAttributeValueLength: Int? = null,

    @SerialName("exception_event_name")
    val exceptionEventName: String? = null,
)
