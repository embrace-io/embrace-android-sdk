package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.prefs.PreferencesService

/**
 * Information about the user of the app, provided by the developer performing the integration.
 */
internal data class UserInfo(

    @SerializedName("id")
    val userId: String? = null,

    @SerializedName("em")
    val email: String? = null,

    @SerializedName("un")
    val username: String? = null,

    @SerializedName("per")
    val personas: Set<String>? = null
) {

    companion object {
        const val PERSONA_NEW_USER = "new_user"
        const val PERSONA_POWER_USER = "power_user"
        const val PERSONA_LOGGED_IN = "logged_in"
        const val PERSONA_VIP = "vip"
        const val PERSONA_CREATOR = "content_creator"
        const val PERSONA_TESTER = "tester"
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
