package io.embrace.android.embracesdk.testframework.assertions

import org.json.JSONArray
import org.json.JSONObject

/**
 * Ignore any values with this key in the expected JSON, because the value is non-deterministic
 * between test runs.
 */
private const val IGNORE_VALUE = "__EMBRACE_TEST_IGNORE__"

/**
 * Performs a comparison between two Json sources.
 */
internal object JsonComparator {

    /**
     * Performs a recursive deep comparison of two JSON objects. If any values mismatch, details are
     * added to a list that is printed for debugging purposes.
     */
    fun compare(expected: JSONObject, observed: JSONObject, path: String = ""): List<String> {
        val mismatches = mutableListOf<String>()

        expected.keys().forEach { key ->
            val expectedValue = expected.get(key)
            val observedValue = observed.opt(key)

            val currentPath = when {
                path.isEmpty() -> key
                else -> "$path.$key"
            }
            if (expectedValue == IGNORE_VALUE) {
                return@forEach
            }
            if (observedValue == null) {
                mismatches.add("[$currentPath] Expected value '$expectedValue', observed value is missing")
                return@forEach
            }
            compareJsonValue(expectedValue, observedValue, mismatches, currentPath)
        }

        // Check for keys in observed JSON that are not present in expected JSON recursively
        findMissingExpectedKeys(observed, expected, mismatches)
        return mismatches
    }

    private fun compareJsonValue(
        expectedValue: Any,
        observedValue: Any?,
        mismatches: MutableList<String>,
        currentPath: String
    ) {
        if (expectedValue is JSONObject && observedValue is JSONObject) {
            mismatches.addAll(compare(expectedValue, observedValue, currentPath))
        } else if (expectedValue is JSONArray && observedValue is JSONArray) {
            if (expectedValue.length() != observedValue.length()) {
                mismatches.add("[$currentPath] Expected array size ${expectedValue.length()}, observed array size ${observedValue.length()}")
            } else {
                for (i in 0 until expectedValue.length()) {
                    val subPath = "$currentPath[$i]"
                    val expectedElement = expectedValue[i]
                    val observedElement = observedValue.opt(i)
                    if (expectedElement == IGNORE_VALUE) {
                        continue
                    }
                    if (expectedElement != observedElement) {
                        mismatches.add("[$subPath] Expected value '$expectedElement', observed value '$observedElement'")
                    }
                }
            }
        } else if (expectedValue != observedValue) {
            mismatches.add("[$currentPath] Expected value '$expectedValue', observed value '$observedValue'")
        }
    }

    private fun findMissingExpectedKeys(
        observed: JSONObject,
        expected: JSONObject,
        mismatches: MutableList<String>
    ) {
        observed.keys().forEach { observedKey ->
            val expectedValue = expected.opt(observedKey)
            if (expectedValue == null) {
                mismatches.add("[$observedKey] Key exists in observed JSON but not in expected JSON.")
            } else if (expectedValue is JSONObject && observed.get(observedKey) is JSONObject) {
                mismatches.addAll(
                    compare(
                        expectedValue,
                        observed.getJSONObject(observedKey),
                        observedKey
                    )
                )
            } else if (expectedValue is JSONArray && observed.get(observedKey) is JSONArray) {
                for (i in 0 until (observed.get(observedKey) as JSONArray).length()) {
                    val observedElement = (observed.get(observedKey) as JSONArray).opt(i)
                    if (observedElement is JSONObject) {
                        mismatches.addAll(
                            compare(
                                expectedValue.getJSONObject(0),
                                observedElement,
                                "$observedKey[$i]"
                            )
                        )
                    }
                }
            }
        }
    }
}