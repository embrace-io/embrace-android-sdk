package io.embrace.android.embracesdk.utils

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

    internal class Result(
        val success: Boolean,
        val message: String
    )

    /**
     * Compares a Json obtained from an InputStream and another one from a String.
     */
    fun areEquals(expected: InputStream?, observed: String): Result {
        val expectedText = InputStreamReader(expected, StandardCharsets.UTF_8).readText()
        val sb = StringBuilder()
        return Result(
            success = areJsonStringEquals(expectedText, observed, sb),
            message = sb.toString()
        )
    }

    /**
     * Compares two json strings.
     */
    private fun areJsonStringEquals(
        expected: String,
        observed: String,
        sb: StringBuilder
    ): Boolean {
        val expectedJson = JsonParser.parseString(expected)
        val observedJson = JsonParser.parseString(observed)
        return areJsonElementsEquals(expectedJson, observedJson, sb)
    }

    private fun areJsonElementsEquals(
        jsonElement1: JsonElement,
        jsonElement2: JsonElement,
        sb: StringBuilder
    ): Boolean {
        when {
            jsonElement1.isIgnored() || jsonElement2.isIgnored() -> return true
            jsonElement1.isJsonObject && jsonElement2.isJsonObject -> {
                return areJsonObjectsEquals(
                    jsonElement1.asJsonObject,
                    jsonElement2.asJsonObject,
                    sb
                )
            }

            jsonElement1.isJsonArray && jsonElement2.isJsonArray -> {
                return areJsonArraysEquals(jsonElement1.asJsonArray, jsonElement2.asJsonArray, sb)
            }

            jsonElement1.isJsonPrimitive && jsonElement2.isJsonPrimitive -> {
                return areJsonPrimitiveEquals(
                    jsonElement1.asJsonPrimitive,
                    jsonElement2.asJsonPrimitive,
                    sb
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
    private fun areJsonObjectsEquals(
        jsonObject1: JsonObject,
        jsonObject2: JsonObject,
        sb: StringBuilder
    ): Boolean {
        val entrySet1 = jsonObject1.entrySet()
        val entrySet2 = jsonObject2.entrySet()

        if (entrySet1 != null && entrySet2 != null && entrySet2.size == entrySet1.size) {
            entrySet1.forEachIndexed { _, entry ->
                try {
                    jsonObject2.get(entry.key)
                } catch (ex: Exception) {
                    sb.append("JsonValidator exception reading entry.key ${entry.key}. ")
                }
                try {
                    if (!areJsonElementsEquals(entry.value, jsonObject2.get(entry.key), sb)) {
                        sb.append("Match failed for key: ${entry.key}. ")
                        return false
                    }
                } catch (ex: Exception) {
                    sb.append("JsonValidator exception reading entry.key ${entry.key}. ")
                    return false
                }
            }
            return true
        } else {
            sb.append("Different entry set size. ")
            sb.append("expected jsonObject1: ${jsonObject1.size()} $jsonObject1. ")
            sb.append("observed jsonObject2: ${jsonObject2.size()} $jsonObject2. ")
            return false
        }
    }

    /**
     * Two JsonArrays are equal if each element in the array is equal to the one in the other array.
     * Arrays ordered in a different way are considered different.
     */
    private fun areJsonArraysEquals(
        jsonArray1: JsonArray,
        jsonArray2: JsonArray,
        sb: StringBuilder
    ): Boolean {
        if (jsonArray1.size() != jsonArray2.size()) {
            sb.append("Different arrays size. ")
            return false
        }

        jsonArray1.forEach { array1Entry ->
            var found = false
            jsonArray2.forEach { array2Entry ->
                // use another string builder to avoid appending to the main one
                if (areJsonElementsEquals(array1Entry, array2Entry, StringBuilder())) {
                    found = true
                }
            }
            if (!found) {
                sb.append("Array element not found in observed array: $array1Entry.")
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
        jsonPrimitive2: JsonPrimitive,
        sb: StringBuilder
    ): Boolean {
        val arePrimitiveEquals = jsonPrimitive1 == jsonPrimitive2
        if (!arePrimitiveEquals) {
            sb.append(
                "Different values. Expected: ${jsonPrimitive1.asJsonPrimitive} " +
                    "Actual: ${jsonPrimitive2.asJsonPrimitive}. "
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
