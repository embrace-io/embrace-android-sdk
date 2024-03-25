package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import org.junit.Assert.assertEquals

private val serializer = EmbraceSerializer()

/**
 * Asserts that the JSON representation of the object matches the contents of a golden file
 * stored in the resources directory.
 *
 * This is implemented by converting both the supplied object & resource file into a Map
 * representation. The two maps are then compared.
 */
internal inline fun <reified T> assertJsonMatchesGoldenFile(resourceName: String, obj: T) {
    val json = ResourceReader.readResourceAsText(resourceName)
    val expected = serializer.fromJson(json, Map::class.java)
    val observedJson = serializer.toJson(obj)
    println(observedJson)
    val observed = serializer.fromJson(observedJson, Map::class.java)
    assertEquals(expected, observed)
}

/**
 * Deserializes a JSON file stored in the resources directory.
 */
internal inline fun <reified T> deserializeJsonFromResource(resourceName: String): T {
    val json = ResourceReader.readResourceAsText(resourceName)
    return serializer.fromJson(json, T::class.java)
}

/**
 * Deserializes an empty JSON object into the supplied type.
 */
internal inline fun <reified T> deserializeEmptyJsonString(): T {
    return serializer.fromJson("{}", T::class.java)
}
