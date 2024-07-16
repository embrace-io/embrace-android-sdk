package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.prefs.PreferencesService

/**
 * Information about the user of the app, provided by the developer performing the integration.
 */
@JsonClass(generateAdapter = true)
internal data class UserInfo(

    @Json(name = "id")
    val userId: String? = null,

    @Json(name = "em")
    val email: String? = null,

    @Json(name = "un")
    val username: String? = null,

    @Json(name = "per")
    val personas: Set<String>? = null
) {

    companion object {
        const val PERSONA_PAYER = "payer"
        const val PERSONA_FIRST_DAY_USER = "first_day"

        /**
         * Creates an instance of [UserInfo] from the cache.
         *
         * @param preferencesService the preferences service
         * @return user info created from the cache and configuration
         */
        @JvmStatic
        fun ofStored(preferencesService: PreferencesService): UserInfo {
            Systrace.traceSynchronous("load-user-info-from-pref") {
                val id = preferencesService.userIdentifier
                val name = preferencesService.username
                val email = preferencesService.userEmailAddress
                val personas: MutableSet<String> = HashSet()
                preferencesService.userPersonas?.let(personas::addAll)
                @Suppress("DEPRECATION") // still need to store it, event thought it's deprecated..
                preferencesService.customPersonas?.let(personas::addAll)

                personas.remove(PERSONA_PAYER)
                if (preferencesService.userPayer) {
                    personas.add(PERSONA_PAYER)
                }
                personas.remove(PERSONA_FIRST_DAY_USER)
                if (preferencesService.isUsersFirstDay()) {
                    personas.add(PERSONA_FIRST_DAY_USER)
                }
                return UserInfo(id, email, name, personas)
            }
        }
    }
}
