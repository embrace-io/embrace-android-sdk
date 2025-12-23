package io.embrace.android.embracesdk.internal.config

import io.embrace.android.embracesdk.fakes.FAKE_DEVICE_ID
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.KeyValueStoreEditor

internal class FakeDeviceIdStore : KeyValueStore {
    override fun getString(key: String): String = FAKE_DEVICE_ID

    override fun getInt(key: String): Int {
        throw UnsupportedOperationException()
    }

    override fun getLong(key: String): Long {
        throw UnsupportedOperationException()
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        throw UnsupportedOperationException()
    }

    override fun getStringSet(key: String): Set<String> {
        throw UnsupportedOperationException()
    }

    override fun getStringMap(key: String): Map<String, String> {
        throw UnsupportedOperationException()
    }

    override fun edit(action: KeyValueStoreEditor.() -> Unit) {
        throw UnsupportedOperationException()
    }

    override fun incrementAndGet(key: String): Int {
        throw UnsupportedOperationException()
    }
}
