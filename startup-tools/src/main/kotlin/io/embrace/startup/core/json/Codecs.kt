package io.embrace.startup.core.json

import kotlinx.serialization.json.Json

/**
 * The one JSON configuration every reader and writer in the toolchain uses.
 *
 * Why each setting is what it is:
 * - `ignoreUnknownKeys`: the stores were written by Python over several weeks and carry fields such
 *   as `_comment` that are documentation, not data. A reader that rejects them cannot read history.
 * - `explicitNulls = true`: the Python built each record as a complete dict and `json.dumps` wrote
 *   every key, `null` included (`"app_build_id": null` on a run with no APK digest). Readers treat
 *   absent and null alike, but the records on disk should look like the ones the Python wrote; the
 *   side-by-side ingest of a fresh campaign was the only difference between the two toolchains' records
 *   once the engine label was excluded. Fields a given producer never writes are marked
 *   `@EncodeDefault(NEVER)` on the type instead (see `Derived`).
 * - `allowSpecialFloatingPointValues`: Python's `json` module emits bare `NaN`/`Infinity`, which
 *   strict JSON parsers reject; a store record containing one would otherwise be unreadable.
 * - `encodeDefaults`: a record must be complete on disk, not "complete given the code that wrote it".
 *
 * kotlinx-serialization has no key-sorting option, so output is NOT byte-comparable with Python's
 * `sort_keys=True`. Parity tests therefore compare parsed JSON trees, never serialized strings; see
 * the plan's validation layer A.
 */
val StartupJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = true
    allowSpecialFloatingPointValues = true
    encodeDefaults = true
}
