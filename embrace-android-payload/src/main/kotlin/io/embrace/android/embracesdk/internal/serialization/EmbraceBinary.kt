@file:OptIn(ExperimentalSerializationApi::class)

package io.embrace.android.embracesdk.internal.serialization

import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import java.io.ByteArrayOutputStream
import java.io.DataInput
import java.io.DataInputStream
import java.io.DataOutput
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Untagged binary format for cache storage. Elements are encoded positionally with no field tags,
 * so a decode depends entirely on the type's [SerialDescriptor] and any schema change invalidates
 * previously-written bytes; callers must treat a decode failure as a cache miss and fall back to a
 * robust format (e.g. [embraceJson]).
 *
 * Each stream is prefixed with the type's [BinaryVersion] uid, and a decode rejects any payload
 * whose stored uid does not match the type's current uid. A [BinaryVersion] is mandatory on any
 * `@Serializable` root type processed by this format.
 */
sealed class EmbraceBinary(
    override val serializersModule: SerializersModule = EmptySerializersModule(),
) : BinaryFormat {

    companion object Default : EmbraceBinary()

    override fun <T> encodeToByteArray(serializer: SerializationStrategy<T>, value: T): ByteArray {
        val output = ByteArrayOutputStream()
        encodeToStream(serializer, value, output)
        return output.toByteArray()
    }

    override fun <T> decodeFromByteArray(deserializer: DeserializationStrategy<T>, bytes: ByteArray): T =
        decodeFromStream(deserializer, bytes.inputStream())

    /** Encodes [value] with a [BinaryVersion] uid header. The stream is flushed but not closed. */
    fun <T> encodeToStream(serializer: SerializationStrategy<T>, value: T, outputStream: OutputStream) {
        val data = DataOutputStream(outputStream)
        data.writeLong(cacheUid(serializer.descriptor))
        DataOutputEncoder(data, serializersModule).encodeSerializableValue(serializer, value)
        data.flush()
    }

    /**
     * Decodes a [T], throwing [SerializationException] if the stored [BinaryVersion] uid does not
     * match the type's current uid. The stream is not closed.
     */
    fun <T> decodeFromStream(deserializer: DeserializationStrategy<T>, inputStream: InputStream): T {
        val data = DataInputStream(inputStream)
        val storedUid = data.readLong()
        val expectedUid = cacheUid(deserializer.descriptor)
        if (storedUid != expectedUid) {
            throw SerializationException(
                "EmbraceBinary cache version mismatch for '${deserializer.descriptor.serialName}': " +
                    "stored=$storedUid expected=$expectedUid. Treat as a cache miss.",
            )
        }
        return DataInputDecoder(data, serializersModule = serializersModule)
            .decodeSerializableValue(deserializer)
    }

    private fun cacheUid(descriptor: SerialDescriptor): Long {
        val version = descriptor.annotations.filterIsInstance<BinaryVersion>().firstOrNull()
        requireNotNull(version) {
            "Type '${descriptor.serialName}' has no @CacheVersion and cannot be stored with EmbraceBinary."
        }
        return version.uid
    }
}

/** Shared instance, mirroring [embraceJson]. */
internal val embraceBinary: EmbraceBinary = EmbraceBinary

inline fun <reified T> EmbraceBinary.encodeToStream(value: T, outputStream: OutputStream): Unit =
    encodeToStream(serializer<T>(), value, outputStream)

inline fun <reified T> EmbraceBinary.decodeFromStream(inputStream: InputStream): T =
    decodeFromStream(serializer<T>(), inputStream)

private class DataOutputEncoder(
    private val output: DataOutput,
    override val serializersModule: SerializersModule,
) : AbstractEncoder() {
    override fun encodeBoolean(value: Boolean) = output.writeByte(if (value) 1 else 0)
    override fun encodeByte(value: Byte) = output.writeByte(value.toInt())
    override fun encodeShort(value: Short) = output.writeShort(value.toInt())
    override fun encodeInt(value: Int) = output.writeInt(value)
    override fun encodeLong(value: Long) = output.writeLong(value)
    override fun encodeFloat(value: Float) = output.writeFloat(value)
    override fun encodeDouble(value: Double) = output.writeDouble(value)
    override fun encodeChar(value: Char) = output.writeChar(value.code)
    override fun encodeString(value: String) = output.writeUTF(value)
    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) = output.writeInt(index)

    override fun beginCollection(descriptor: SerialDescriptor, collectionSize: Int): CompositeEncoder {
        encodeInt(collectionSize)
        return this
    }

    override fun encodeNull() = encodeBoolean(false)
    override fun encodeNotNullMark() = encodeBoolean(true)
}

private class DataInputDecoder(
    private val input: DataInput,
    private var elementsCount: Int = 0,
    override val serializersModule: SerializersModule,
) : AbstractDecoder() {
    private var elementIndex = 0

    override fun decodeBoolean(): Boolean = input.readByte().toInt() != 0
    override fun decodeByte(): Byte = input.readByte()
    override fun decodeShort(): Short = input.readShort()
    override fun decodeInt(): Int = input.readInt()
    override fun decodeLong(): Long = input.readLong()
    override fun decodeFloat(): Float = input.readFloat()
    override fun decodeDouble(): Double = input.readDouble()
    override fun decodeChar(): Char = input.readChar()
    override fun decodeString(): String = input.readUTF()
    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = input.readInt()

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex == elementsCount) return CompositeDecoder.DECODE_DONE
        return elementIndex++
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder =
        DataInputDecoder(input, descriptor.elementsCount, serializersModule)

    override fun decodeSequentially(): Boolean = true

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int =
        decodeInt().also { elementsCount = it }

    override fun decodeNotNullMark(): Boolean = decodeBoolean()
}
