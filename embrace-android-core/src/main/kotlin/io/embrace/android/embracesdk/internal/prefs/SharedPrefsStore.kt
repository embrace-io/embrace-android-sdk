@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal.prefs

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.KeyValueStoreEditor
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Creates the SDK's [KeyValueStore], backed by the default [SharedPreferences].
 *
 * Loading the underlying preferences blocks on disk, so callers should hold this behind a [Lazy] and
 * share that one instance rather than resolving it eagerly.
 */
fun createKeyValueStore(context: Context, serializer: PlatformSerializer): KeyValueStore =
    EmbTrace.trace(sectionName = "key-value-store-init", recordDuration = true) {
        SharedPrefsStore(PreferenceManager.getDefaultSharedPreferences(context), serializer)
    }

internal class SharedPrefsStore(
    private val impl: SharedPreferences,
    private val serializer: PlatformSerializer,
) : KeyValueStore {

    private val openBatch = ThreadLocal<Batch?>()
    private val firstAccessTraced = AtomicBoolean(false)

    override fun getString(key: String): String? {
        return pending(key) { impl.getString(key, null) }
    }

    override fun getInt(key: String): Int? {
        return pending(key) {
            val defaultValue: Int = -1
            when (val value = impl.getInt(key, defaultValue)) {
                defaultValue -> null
                else -> value
            }
        }
    }

    override fun getLong(key: String): Long? {
        return pending(key) {
            val defaultValue: Long = -1L
            when (val value = impl.getLong(key, defaultValue)) {
                defaultValue -> null
                else -> value
            }
        }
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return pending<Boolean>(key) { impl.getBoolean(key, defaultValue) } ?: defaultValue
    }

    override fun getStringSet(key: String): Set<String>? {
        return pending(key) { impl.getStringSet(key, null) }
    }

    override fun getStringMap(key: String): Map<String, String>? {
        return pending(key) {
            val mapString = impl.getString(key, null) ?: return@pending null
            serializer.fromJson(mapString, mapSerializer)
        }
    }

    override fun editAndCommit(action: KeyValueStoreEditor.() -> Unit) {
        val batch = openBatch.get()
        if (batch != null) {
            batch.action()
        } else {
            SharedPrefsStoreEditor(measureFirstAccess { impl.edit() }, serializer).use {
                it.action()
            }
        }
    }

    override fun batch(action: () -> Unit) {
        if (openBatch.get() != null) { // a batch is already open on this thread, so it owns the commit
            action()
            return
        }
        val batch = Batch()
        openBatch.set(batch)
        try {
            action()
        } finally {
            openBatch.remove()
            batch.flush()
        }
    }

    /**
     * Returns the value buffered by the batch open on this thread, falling back to [read] if there
     * is no open batch or the batch hasn't written [key].
     *
     * [read] is `noinline` so it can be handed to [measureFirstAccess]
     */
    private inline fun <reified T> pending(key: String, noinline read: () -> T?): T? {
        val batch = openBatch.get() ?: return measureFirstAccess(read)
        val write = batch.writes[key] ?: return measureFirstAccess(read)
        return write.value as? T
    }

    /**
     * A calling of [read] that measures the first invocation, which is the access of [SharedPreferences]
     * from this class. That measurement should include the loading of the shared preferences file if
     * that call was the first to load it.
     */
    private fun <T> measureFirstAccess(read: () -> T): T =
        if (firstAccessTraced.compareAndSet(false, true)) {
            EmbTrace.trace(sectionName = "prefs-first-read", recordDuration = true) { read() }
        } else {
            read()
        }

    private fun Batch.flush() {
        if (writes.isEmpty()) {
            return
        }
        SharedPrefsStoreEditor(measureFirstAccess { impl.edit() }, serializer).use { editor ->
            writes.values.forEach { it.write(editor) }
        }
    }

    /**
     * Buffers writes so they can be replayed against a real editor in one commit.
     */
    private class Batch : KeyValueStoreEditor {

        val writes = LinkedHashMap<String, PendingWrite>()

        override fun putString(key: String, value: String?) {
            writes[key] = PendingWrite(value) { it.putString(key, value) }
        }

        override fun putInt(key: String, value: Int?) {
            writes[key] = PendingWrite(value) { it.putInt(key, value) }
        }

        override fun putLong(key: String, value: Long?) {
            writes[key] = PendingWrite(value) { it.putLong(key, value) }
        }

        override fun putBoolean(key: String, value: Boolean?) {
            writes[key] = PendingWrite(value ?: false) { it.putBoolean(key, value) }
        }

        override fun putStringSet(key: String, value: Set<String>?) {
            writes[key] = PendingWrite(value) { it.putStringSet(key, value) }
        }

        override fun putStringMap(key: String, value: Map<String, String>?) {
            writes[key] = PendingWrite(value) { it.putStringMap(key, value) }
        }

        override fun close() {
            // the batch is committed when the outermost batch block exits, not per edit
        }
    }

    private class PendingWrite(
        val value: Any?,
        val write: (KeyValueStoreEditor) -> Unit,
    )

    private companion object {
        val mapSerializer = MapSerializer(String.serializer(), String.serializer())
    }
}
