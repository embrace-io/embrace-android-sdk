package io.embrace.android.embracesdk.internal.payload.extensions

import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.payload.UserInfo
import io.embrace.android.embracesdk.internal.prefs.PreferencesService

internal const val PERSONA_PAYER = "payer"
internal const val PERSONA_FIRST_DAY_USER = "first_day"

/**
 * Creates an instance of [UserInfo] from the cache.
 *
 * @param preferencesService the preferences service
 * @return user info created from the cache and configuration
 */
internal fun PreferencesService.getStoredUserInfo(): UserInfo {
    Systrace.traceSynchronous("load-user-info-from-pref") {
        val id = userIdentifier
        val name = username
        val email = userEmailAddress
        val personas: MutableSet<String> = HashSet()
        userPersonas?.let(personas::addAll)
        @Suppress("DEPRECATION") // still need to store it, event thought it's deprecated..
        customPersonas?.let(personas::addAll)

        personas.remove(PERSONA_PAYER)
        if (userPayer) {
            personas.add(PERSONA_PAYER)
        }
        personas.remove(PERSONA_FIRST_DAY_USER)
        if (isUsersFirstDay()) {
            personas.add(PERSONA_FIRST_DAY_USER)
        }
        return UserInfo(id, email, name, personas)
    }
}
