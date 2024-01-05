package io.embrace.android.embracesdk.utils

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Performs a comparison between two Json sources.
 */
internal object JsonValidator {

    /**
     * Compares a Json obtained from an InputStream and another one from a String.
     */
    fun areEquals(expected: InputStream?, observed: String): Boolean {
        val expectedText = InputStreamReader(expected, StandardCharsets.UTF_8).readText()
        return areJsonStringEquals(expectedText, observed)
    }

    /**
     * Compares two json strings.
     */
    private fun areJsonStringEquals(expected: String, observed: String): Boolean {
        val expectedJson = JsonParser.parseString(expected)
        val observedJson = JsonParser.parseString(observed)
        Log.i(
            JsonValidator.javaClass.simpleName,
            "Expected JSON: $expectedJson. \n Actual JSON: $observedJson"
        )

        return areJsonElementsEquals(expectedJson, observedJson)
    }

    private fun areJsonElementsEquals(
        jsonElement1: JsonElement,
        jsonElement2: JsonElement
    ): Boolean {
        when {
            jsonElement1.isIgnored() || jsonElement2.isIgnored() -> return true
            jsonElement1.isJsonObject && jsonElement2.isJsonObject -> {
                return areJsonObjectsEquals(jsonElement1.asJsonObject, jsonElement2.asJsonObject)
            }
            jsonElement1.isJsonArray && jsonElement2.isJsonArray -> {
                return areJsonArraysEquals(jsonElement1.asJsonArray, jsonElement2.asJsonArray)
            }
            jsonElement1.isJsonPrimitive && jsonElement2.isJsonPrimitive -> {
                return areJsonPrimitiveEquals(
                    jsonElement1.asJsonPrimitive,
                    jsonElement2.asJsonPrimitive
                )
            }
            jsonElement1.isJsonNull && jsonElement2.isJsonNull -> {
                return true
            }
            else -> return false
        }
    }

    /**
     * Two JsonObjects are equals if each element in the EntrySet is equal to the one
     * in the other JsonObject.
     */
    private fun areJsonObjectsEquals(jsonObject1: JsonObject, jsonObject2: JsonObject): Boolean {
        val entrySet1 = jsonObject1.entrySet()
        val entrySet2 = jsonObject2.entrySet()

        if (entrySet1 != null && entrySet2 != null && entrySet2.size == entrySet1.size) {
            entrySet1.forEachIndexed { _, entry ->
                try {
                    Log.e(JsonValidator.javaClass.simpleName, "entry.key " + entry.key)
                    jsonObject2.get(entry.key)
                } catch (ex: Exception) {
                    Log.e(JsonValidator.javaClass.simpleName, "entry.key " + entry.key)
                }
                try {
                    if (!areJsonElementsEquals(entry.value, jsonObject2.get(entry.key))) {
                        Log.e(
                            JsonValidator.javaClass.simpleName,
                            "Match failed for key: ${entry.key}"
                        )
                        return false
                    }
                } catch (ex: Exception) {
                    Log.e(JsonValidator.javaClass.simpleName, "entry.key " + entry.key)
                    return false
                }
            }
            return true
        } else {
            Log.e(JsonValidator.javaClass.simpleName, "Different entry set size.")
            Log.e(JsonValidator.javaClass.simpleName, "expected jsonObject1: " + jsonObject1.size() + " " + jsonObject1)
            Log.e(JsonValidator.javaClass.simpleName, "observed jsonObject2: " + jsonObject2.size() + " " + jsonObject2)
            return false
        }
    }

    /**
     * Two JsonArrays are equal if each element in the array is equal to the one in the other array.
     * Arrays ordered in a different way are considered different.
     */
    private fun areJsonArraysEquals(jsonArray1: JsonArray, jsonArray2: JsonArray): Boolean {
        if (jsonArray1.size() != jsonArray2.size()) {
            Log.e(JsonValidator.javaClass.simpleName, "Different arrays size.")
            return false
        }

        jsonArray1.forEachIndexed { index, entry ->
            if (!areJsonElementsEquals(entry, jsonArray2.get(index))) {
                Log.e(
                    JsonValidator.javaClass.simpleName,
                    "Different array value at position: $index."
                )
                return false
            }
        }
        return true
    }

    /**
     * Compares two JsonPrimitives.
     */
    private fun areJsonPrimitiveEquals(
        jsonPrimitive1: JsonPrimitive,
        jsonPrimitive2: JsonPrimitive
    ): Boolean {
        val arePrimitiveEquals = jsonPrimitive1 == jsonPrimitive2
        if (!arePrimitiveEquals) {
            Log.e(
                JsonValidator.javaClass.simpleName,
                "Different values. Expected: ${jsonPrimitive1.asJsonPrimitive} " +
                    "Actual: ${jsonPrimitive2.asJsonPrimitive}"
            )
        }
        return arePrimitiveEquals
    }
}

public fun JsonElement.isIgnored(): Boolean =
    this.isJsonPrimitive &&
        this.asJsonPrimitive.isString &&
        this.asJsonPrimitive.asString == IGNORE_KEYWORD

private const val IGNORE_KEYWORD = "__EMBRACE_TEST_IGNORE__"
