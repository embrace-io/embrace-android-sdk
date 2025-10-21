package io.embrace.android.embracesdk.internal.arch.attrs

/**
 * Embrace-specific implementation of an OTel Attribute key. Contains business logic on how the key name should be prefixed.
 */
@JvmInline
value class EmbraceAttributeKey private constructor(
    val name: String,
) {

    companion object {

        /**
         * Prefix added to all attribute keys for all attributes with a specific meaning to the Embrace platform
         */
        private const val EMBRACE_ATTRIBUTE_NAME_PREFIX = "emb."

        /**
         * Prefix added to all Embrace attribute keys that are meant to be internal to Embrace
         */
        private const val EMBRACE_PRIVATE_ATTRIBUTE_NAME_PREFIX = "emb.private."

        fun create(
            id: String,
            isPrivate: Boolean = false,
        ): EmbraceAttributeKey {
            val prefix = when {
                isPrivate -> EMBRACE_PRIVATE_ATTRIBUTE_NAME_PREFIX
                else -> EMBRACE_ATTRIBUTE_NAME_PREFIX
            }
            val name = prefix + id
            return EmbraceAttributeKey(name)
        }
    }
}
