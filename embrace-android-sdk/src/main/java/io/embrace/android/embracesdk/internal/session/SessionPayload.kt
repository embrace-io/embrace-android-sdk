package io.embrace.android.embracesdk.internal.session

import com.squareup.moshi.Json

internal data class SessionPayload(
    /**
     * A map of symbols that are associated with the session.
     * We use this to associate the symbolication files that have been uploaded with UUIDs with the stacktrace module names,
     * which don’t have UUIDs in them. Previous name: s.sb
     */

    @Json(name = "shared_lib_symbol_mapping")
    val sharedLibSymbolMapping: Map<String, String>? = null
)
