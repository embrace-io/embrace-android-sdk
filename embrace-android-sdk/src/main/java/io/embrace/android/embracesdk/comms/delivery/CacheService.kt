package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.internal.utils.SerializationAction
import io.embrace.android.embracesdk.payload.SessionMessage

/**
 * Handles the caching of objects.
 */
internal interface CacheService {
    /**
     * Caches the specified object.
     *
     * @param name   the name of the object to cache
     * @param objectToCache the object to cache
     * @param clazz  the class of the object to cache
     * @param <T>    the type of the object
     */
    fun <T> cacheObject(name: String, objectToCache: T, clazz: Class<T>)

    /**
     * Reads the specified object from the cache, if it exists.
     *
     * @param name  the name of the object to read from the cache
     * @param clazz the class of the cached object
     * @param <T>   the type of the cached object
     * @return optionally the object, if it can be read successfully
     */
    fun <T> loadObject(name: String, clazz: Class<T>): T?

    /**
     * Caches a payload to disk.
     *
     * @param name   the name of this cache in disk
     * @param action action that writes bytes
     */
    fun cachePayload(name: String, action: SerializationAction)

    /**
     * Serializes a session object to disk via a stream. This saves memory when the session is large & the return value isn't used
     * (e.g. for a crash & periodic caching). If an existing session already exists, it will only be replaced if the new [SessionMessage]
     * is successfully written to disk
     */
    fun writeSession(name: String, sessionMessage: SessionMessage)

    /**
     * Provides a function that writes the bytes from a cached file, if it exists, to an
     * outputstream
     *
     * @param name  the name of the file to read
     * @return a function that writes the byte array, if it can be read successfully
     */
    fun loadPayload(name: String): SerializationAction

    /**
     * Delete a file from the cache
     *
     * @param name  the name of the file to delete
     */
    fun deleteFile(name: String): Boolean

    /**
     * Get file IDs for all cached session files. This method also normalizes the file names and removes and temporarily files left behind
     * when the app unexpectedly terminates.
     *
     * @return list of file names
     */
    fun normalizeCacheAndGetSessionFileIds(): List<String>

    /**
     * Loads the old format of pending API calls.
     */
    fun loadOldPendingApiCalls(name: String): List<PendingApiCall>?

    /**
     * Transform the current saved session with the given name using the given [transformer] and save it in its place
     */
    fun transformSession(name: String, transformer: (SessionMessage) -> SessionMessage)
}
