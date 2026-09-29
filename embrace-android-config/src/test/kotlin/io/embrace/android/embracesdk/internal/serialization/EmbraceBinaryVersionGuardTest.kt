@file:OptIn(ExperimentalSerializationApi::class)

package io.embrace.android.embracesdk.internal.serialization

import io.embrace.android.embracesdk.internal.config.cache.CachedConfiguration
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards every `@Serializable` type that is persisted via [EmbraceBinary] against accidental
 * schema changes.
 *
 * [EmbraceBinary] writes fields positionally with no tags, so the on-disk layout is dictated
 * entirely by a type's [SerialDescriptor]. A change to any property (name, order, type, nullability)
 * or to any nested `@Serializable` type silently invalidates previously-cached bytes. The
 * [BinaryVersion] uid exists so that such a change forces a deliberate cache invalidation.
 *
 * This test recomputes the structural fingerprint of each guarded type from its descriptor and
 * asserts it still matches the pinned [BinaryVersion.uid]. When it fails, the schema changed: if the
 * change is intentional, update the `@BinaryVersion(uid = ...)` annotation to the reported value
 * (which invalidates any cache entries written by older builds).
 */
internal class EmbraceBinaryVersionGuardTest {

    /** Every root type stored via [EmbraceBinary]. Add new cached schemas here. */
    private val guardedSchemas: List<KSerializer<*>> = listOf(
        serializer<CachedConfiguration>(),
    )

    @Test
    fun `binary cache schemas have not changed without a version bump`() {
        guardedSchemas.forEach { serializer ->
            val descriptor = serializer.descriptor
            val pinned = pinnedUid(descriptor)
            val fingerprint = fingerprint(descriptor)
            assertEquals(
                "Binary cache schema '${descriptor.serialName}' has changed. If this is intentional, " +
                    "bump its @BinaryVersion to $fingerprint (this invalidates previously-cached entries).",
                fingerprint,
                pinned,
            )
        }
    }

    private fun pinnedUid(descriptor: SerialDescriptor): Long {
        val version = descriptor.annotations.filterIsInstance<BinaryVersion>().firstOrNull()
        requireNotNull(version) {
            "Guarded type '${descriptor.serialName}' is missing a @BinaryVersion annotation."
        }
        return version.uid
    }

    /**
     * Produces a 64-bit structural fingerprint of [descriptor] by walking the full graph of nested
     * `@Serializable` types it references and hashing each type's serial name, kind, nullability and
     * every element's name, optionality and (recursively) element descriptor. Cycles are broken so
     * self-referential types terminate.
     */
    private fun fingerprint(descriptor: SerialDescriptor): Long =
        fnv1a64(buildString { describe(descriptor, mutableSetOf()) })

    private fun StringBuilder.describe(descriptor: SerialDescriptor, path: MutableSet<String>) {
        append(descriptor.serialName)
        append('|').append(descriptor.kind)
        append('|').append(descriptor.isNullable)
        if (!path.add(descriptor.serialName)) {
            // Already on the current recursion path: break the cycle.
            append("|<recursion>")
            return
        }
        append('(')
        for (i in 0 until descriptor.elementsCount) {
            append(descriptor.getElementName(i))
            append('?').append(descriptor.isElementOptional(i))
            append('=')
            describe(descriptor.getElementDescriptor(i), path)
            append(';')
        }
        append(')')
        path.remove(descriptor.serialName)
    }

    private fun fnv1a64(value: String): Long {
        var hash = FNV_OFFSET_BASIS
        for (char in value) {
            hash = hash xor char.code.toLong()
            hash *= FNV_PRIME
        }
        return hash
    }

    private companion object {
        private const val FNV_OFFSET_BASIS = -3750763034362895579L // 0xcbf29ce484222325
        private const val FNV_PRIME = 1099511628211L // 0x100000001b3
    }
}
