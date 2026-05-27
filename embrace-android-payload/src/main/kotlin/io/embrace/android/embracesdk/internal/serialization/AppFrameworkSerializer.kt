package io.embrace.android.embracesdk.internal.serialization

import io.embrace.android.embracesdk.internal.payload.AppFramework
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializes [AppFramework] as its integer [AppFramework.value] (1/2/3/4) and decodes via
 * [AppFramework.fromInt], mapping unknown values to null. Mirrors the previous Moshi
 * `AppFrameworkAdapter`.
 */
internal object AppFrameworkSerializer : KSerializer<AppFramework?> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(
            "io.embrace.android.embracesdk.internal.payload.AppFramework",
            PrimitiveKind.INT,
        )

    override fun serialize(encoder: Encoder, value: AppFramework?) {
        encoder.encodeInt(requireNotNull(value) { "AppFramework must not be null." }.value)
    }

    override fun deserialize(decoder: Decoder): AppFramework? =
        AppFramework.fromInt(decoder.decodeInt())
}
