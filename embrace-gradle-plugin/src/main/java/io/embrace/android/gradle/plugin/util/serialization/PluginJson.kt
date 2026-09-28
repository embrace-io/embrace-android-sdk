package io.embrace.android.gradle.plugin.util.serialization

import kotlinx.serialization.json.Json

/**
 * [Json] used for backend requests and responses, task output files and bundled resources. Nulls are omitted when
 * encoding and absent nullable fields decode as null. Unknown keys are ignored so the backend can add fields.
 */
internal val pluginJson: Json = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
}

/**
 * [Json] used for the user-authored embrace-config.json. Unknown keys are rejected so typos fail the build.
 */
internal val configFileJson: Json = Json(pluginJson) {
    ignoreUnknownKeys = false
}
