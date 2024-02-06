package io.embrace.android.embracesdk.prefs

import android.content.SharedPreferences

internal class FakeSharedPreferences(private val throwExceptionOnGet: Boolean = false) : SharedPreferences {
    override fun getAll() = if (throwExceptionOnGet) {
        throw Exception("test exception")
    } else {
        mutableMapOf("testKey" to "testValue")
    }

    override fun getString(key: String?, defValue: String?) = if (throwExceptionOnGet) {
        throw Exception("test exception")
    } else {
        "testString"
    }

    override fun getStringSet(key: String?, defValues: MutableSet<String>?) = if (throwExceptionOnGet) {
        throw Exception("test exception")
    } else {
        mutableSetOf("testString")
    }

    override fun getInt(key: String?, defValue: Int) = if (throwExceptionOnGet) {
        throw Exception("test exception")
    } else {
        1
    }

    override fun getLong(key: String?, defValue: Long) = if (throwExceptionOnGet) {
        throw Exception("test exception")
    } else {
        1L
    }

    override fun getFloat(key: String?, defValue: Float) = if (throwExceptionOnGet) {
        throw Exception("test exception")
    } else {
        1f
    }

    override fun getBoolean(key: String?, defValue: Boolean) = if (throwExceptionOnGet) {
        throw Exception("test exception")
    } else {
        true
    }

    override fun contains(key: String?) = true

    override fun edit(): SharedPreferences.Editor = FakeSharedPreferencesEditor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        // no-op
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
        // no-op
    }
}

internal class FakeSharedPreferencesEditor : SharedPreferences.Editor {
    override fun putString(key: String?, value: String?): SharedPreferences.Editor {
        return this
    }

    override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
        return this
    }

    override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
        return this
    }

    override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
        return this
    }

    override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
        return this
    }

    override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
        return this
    }

    override fun remove(key: String?): SharedPreferences.Editor {
        return this
    }

    override fun clear(): SharedPreferences.Editor {
        return this
    }

    override fun commit(): Boolean {
        return true
    }

    override fun apply() {
        // no-op
    }
}
