package io.embrace.android.embracesdk.internal.payload

/**
 * Typed value of an [EnvelopeResource] attribute.
 */
@JvmInline
value class EnvelopeResourceValue private constructor(val value: Any) {

    val stringValue: String? get() = value as? String
    val booleanValue: Boolean? get() = value as? Boolean
    val longValue: Long? get() = value as? Long
    val intValue: Int? get() = longValue?.toInt()

    companion object {
        fun of(value: String): EnvelopeResourceValue = EnvelopeResourceValue(value)
        fun of(value: Boolean): EnvelopeResourceValue = EnvelopeResourceValue(value)
        fun of(value: Long): EnvelopeResourceValue = EnvelopeResourceValue(value)
    }
}
