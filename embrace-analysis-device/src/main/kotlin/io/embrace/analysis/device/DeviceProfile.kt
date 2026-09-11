package io.embrace.analysis.device

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a device IS, as the reference-set probe reads it: API level, release, vendor, SoC family,
 * cluster topology, RAM and storage class. Compared field by field at ingest to detect drift - an OS
 * upgrade or a replaced handset changes the profile and must become a new device key, because
 * silently comparing across it is the classic way to invent or hide a regression.
 *
 * It lives with the device module rather than with the store's record types because it describes the
 * device, not the record; the store and the reference set carry one, the probe produces one.
 *
 * Every field is nullable with a null default for one reason: the three salvaged 8.3.0 sweep records
 * carry an EMPTY profile (`{}`), and a reader that cannot decode them cannot read the store. A
 * complete profile is required at ingest time; [isComplete] is the check. Fields are
 * `@EncodeDefault(NEVER)` so a run with no provenance stores `{}`, not seven nulls.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DeviceProfile(
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("api_level") val apiLevel: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val release: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val vendor: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("soc_family") val socFamily: String? = null,
    /** Per-cluster maximum CPU frequency in kHz, ascending. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val clusters: List<Long>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("ram_class") val ramClass: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("storage_class") val storageClass: String? = null,
) {
    val isComplete: Boolean
        get() = apiLevel != null && release != null && vendor != null && socFamily != null &&
            clusters != null && ramClass != null && storageClass != null
}
