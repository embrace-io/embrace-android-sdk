package io.embrace.android.embracesdk.capture.user

import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.UserInfo
import io.embrace.android.embracesdk.payload.UserInfo.Companion.ofStored
import io.embrace.android.embracesdk.prefs.PreferencesService
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateListener
import java.util.regex.Pattern

internal class EmbraceUserService(
    private val preferencesService: PreferencesService,
    private val logger: InternalEmbraceLogger
) : ProcessStateListener, UserService {

    @Volatile

    internal var info: UserInfo = ofStored(preferencesService)

    override fun loadUserInfoFromDisk(): UserInfo? {
        return try {
            ofStored(preferencesService)
        } catch (ex: Exception) {
            logger.logError("Failed to load user info from persistent storage.", ex, true)
            null
        }
    }

    override fun getUserInfo(): UserInfo = info.copy()

    override fun setUserIdentifier(userId: String?) {
        val currentUserId = info.userId
        if (currentUserId != null && currentUserId == userId) {
            return
        }
        info = info.copy(userId = userId)
        preferencesService.userIdentifier = userId
    }

    override fun clearUserIdentifier() {
        setUserIdentifier(null)
    }

    override fun setUsername(username: String?) {
        val currentUserName = info.username
        if (currentUserName != null && currentUserName == username) {
            return
        }
        info = info.copy(username = username)
        preferencesService.username = username
    }

    override fun clearUsername() {
        setUsername(null)
    }

    override fun setUserEmail(email: String?) {
        val currentEmail = info.email
        if (currentEmail != null && currentEmail == email) {
            return
        }
        info = info.copy(email = email)
        preferencesService.userEmailAddress = email
    }

    override fun clearUserEmail() {
        setUserEmail(null)
    }

    override fun setUserAsPayer() {
        addUserPersona(UserInfo.PERSONA_PAYER)
    }

    override fun clearUserAsPayer() {
        clearUserPersona(UserInfo.PERSONA_PAYER)
    }

    override fun addUserPersona(persona: String?) {
        if (persona == null) {
            return
        }
        if (!VALID_PERSONA.matcher(persona).matches()) {
            logger.logWarning("Ignoring persona " + persona + " as it does not match " + VALID_PERSONA.pattern())
            return
        }
        val currentPersonas = info.personas
        if (currentPersonas != null) {
            if (currentPersonas.size >= PERSONA_LIMIT) {
                logger.logWarning("Cannot set persona as the limit of " + PERSONA_LIMIT + " has been reached")
                return
            }
            if (currentPersonas.contains(persona)) {
                return
            }
        }

        val newPersonas: Set<String> = info.personas?.plus(persona) ?: mutableSetOf(persona)
        info = info.copy(personas = newPersonas)
        preferencesService.userPersonas = newPersonas
    }

    override fun clearUserPersona(persona: String?) {
        if (persona == null) {
            return
        }
        val currentPersonas = info.personas
        if (currentPersonas != null && !currentPersonas.contains(persona)) {
            logger.logWarning("Persona '$persona' is not set")
            return
        }

        val newPersonas: Set<String> = info.personas?.minus(persona) ?: mutableSetOf()
        info = info.copy(personas = newPersonas)
        preferencesService.userPersonas = newPersonas
    }

    override fun clearAllUserPersonas() {
        val currentPersonas = info.personas
        if (currentPersonas != null && currentPersonas.isEmpty()) {
            return
        }
        val personas: MutableSet<String> = HashSet()
        if (preferencesService.userPayer) {
            personas.add(UserInfo.PERSONA_PAYER)
        }
        if (preferencesService.isUsersFirstDay()) {
            personas.add(UserInfo.PERSONA_FIRST_DAY_USER)
        }
        info = info.copy(personas = personas)
        preferencesService.userPersonas = personas
    }

    override fun clearAllUserInfo() {
        clearUserIdentifier()
        clearUserEmail()
        clearUsername()
        clearAllUserPersonas()
    }

    companion object {
        // Valid persona regex representation.
        val VALID_PERSONA: Pattern = Pattern.compile("^[a-zA-Z0-9_]{1,32}$")

        // Maximum number of allowed personas.
        const val PERSONA_LIMIT = 10
    }
}
