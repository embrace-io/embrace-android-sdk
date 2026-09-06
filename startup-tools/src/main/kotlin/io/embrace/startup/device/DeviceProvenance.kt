package io.embrace.startup.device

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The corpus submission's device provenance from `submit_run.py`: ONLY the dimensions that explain
 * reproducibility failures, coarsely bucketed, and a salted non-reversible unit token in place of the
 * serial. The salt lives beside the corpus on this machine and never leaves it.
 */
class DeviceProvenance(private val adb: Adb) {

    /** Stable per-handset, non-reversible: sha256(salt + serial)[:16]; the salt is created on first use. */
    fun unitToken(serial: String?, corpusPath: Path): String {
        val saltFile = corpusPath.toAbsolutePath().parent.resolve(".unit-salt")
        val salt = if (Files.exists(saltFile)) {
            Files.readString(saltFile).trim()
        } else {
            val bytes = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
            bytes.joinToString("") { "%02x".format(it) }.also { Files.writeString(saltFile, it) }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest((salt + (serial ?: "unknown")).toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(TOKEN_CHARS)
    }

    fun collect(serial: String?, corpusPath: Path): JsonObject {
        val installed = adb.shell(serial, "pm", "list", "packages", "-3")
        val appCount = installed.lines().count { it.isNotBlank() }.takeIf { it > 0 }
        val df = adb.shell(serial, "df", "/data")
        val freePct = USED_PCT.find(df)?.groupValues?.get(1)?.toIntOrNull()?.let { PERCENT - it }
        val battery = adb.shell(serial, "dumpsys", "battery")
        val health = battery.lines().lastOrNull { "health:" in it }?.substringAfter(":")?.trim()
        return JsonObject(
            mapOf(
                "unit_id" to JsonPrimitive(unitToken(serial, corpusPath)),
                "model" to JsonPrimitive(adb.prop(serial, "ro.product.model")),
                "os_build" to JsonPrimitive(adb.prop(serial, "ro.build.fingerprint")),
                "api_level" to JsonPrimitive(adb.prop(serial, "ro.build.version.sdk")),
                "security_patch" to JsonPrimitive(adb.prop(serial, "ro.build.version.security_patch")),
                "skin_version" to JsonPrimitive(adb.prop(serial, "ro.build.display.id")),
                "kernel_version" to JsonPrimitive(adb.shell(serial, "uname", "-r")),
                "soc_family" to JsonPrimitive(adb.prop(serial, "ro.soc.model").ifEmpty { adb.prop(serial, "ro.board.platform") }),
                "storage_free_pct" to nullable(bucketPct(freePct)),
                "battery_health" to nullable(health),
                "installed_app_count" to nullable(bucketApps(appCount)),
                "device_settings" to JsonObject(
                    mapOf(
                        "animation_scale" to JsonPrimitive(globalSetting(serial, "animator_duration_scale")),
                        "background_process_limit" to JsonPrimitive(globalSetting(serial, "background_process_limit")),
                        "battery_saver" to JsonPrimitive(globalSetting(serial, "low_power")),
                    ),
                ),
            ),
        )
    }

    private fun globalSetting(serial: String?, name: String): String = adb.shell(serial, "settings", "get", "global", name)

    companion object {
        /** Coarse buckets: more identifying detail is not more useful. */
        fun bucketApps(count: Int?): String? = when {
            count == null -> null
            count < APPS_LOW -> "<50"
            count <= APPS_HIGH -> "50-150"
            else -> ">150"
        }

        fun bucketPct(value: Int?): String? {
            if (value == null) return null
            val lo = (value / PCT_BUCKET) * PCT_BUCKET
            return "$lo-${lo + PCT_BUCKET}%"
        }

        private fun nullable(s: String?) = if (s == null) JsonNull else JsonPrimitive(s)

        private val USED_PCT = Regex("(\\d+)%")
        private const val PERCENT = 100
        private const val PCT_BUCKET = 20
        private const val APPS_LOW = 50
        private const val APPS_HIGH = 150
        private const val SALT_BYTES = 16
        private const val TOKEN_CHARS = 16
    }
}
