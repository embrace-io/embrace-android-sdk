package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * The public API that is used to set user information.
 */
@InternalApi
public interface UserApi {

    /**
     * The user ID. This would typically be some form of unique identifier such as a UUID or database key for the user.
     * Once set, it persists across app launches until it is otherwise cleared. Set it to null to clear it, for example
     * when the user logs out. Do not put personal identifying information such as email addresses in this field.
     *
     * Reads return null if no ID is set, the SDK hasn't started, or it has been disabled.
     */
    public var userIdentifier: String?
}
