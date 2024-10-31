package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * The session payload, containing the session itself and different objects tied to this session
 *
 * @param spans A list of spans that have completed since the last session, including the session
 * span, which contains metadata about the session represented by this payload. The spans included
 * here may not have started in this session, but they ended during it.
 * @param spanSnapshots A list of spans that are still active at the time of the session's end.
 * @param sharedLibSymbolMapping A map of symbols that are associated with the session. We use this
 * to associate the symbolication files that have been uploaded with UUIDs with the stacktrace module
 * names, which don’t have UUIDs in them. Previous name: s.sb
 */
@JsonClass(generateAdapter = true)
data class SessionPayload(

    /* A list of spans that have completed since the last session, including the session span,
    which contains metadata about the session represented by this payload. The spans included
    here may not have started in this session, but they ended during it. */
    @Json(name = "spans")
    val spans: List<Span>? = null,

    /* A list of spans that are still active at the time of the session's end. */
    @Json(name = "span_snapshots")
    val spanSnapshots: List<Span>? = null,

    /**
     * A map of symbols that are associated with the session.
     * We use this to associate the symbolication files that have been uploaded with UUIDs with the stacktrace module names,
     * which don’t have UUIDs in them. Previous name: s.sb
     */
    @Json(name = "shared_lib_symbol_mapping")
    val sharedLibSymbolMapping: Map<String, String>? = null,
)
