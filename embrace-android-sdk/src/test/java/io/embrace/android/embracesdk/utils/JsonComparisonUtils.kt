package io.embrace.android.embracesdk.utils

import com.google.gson.JsonParser

internal object JsonComparisonUtils {

    fun compareJson(expectedJson: String, actualJson: String): Boolean {
        val expected = JsonParser.parseString(expectedJson)
        val observed = JsonParser.parseString(actualJson)
        return expected == observed
    }
}
