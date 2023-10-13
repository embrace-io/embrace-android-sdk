package io.embrace.android.embracesdk.network.http;

/**
 * Exposes the connectivity state of the implementer
 */
interface ConnectionState {

    /**
     * @return true if this object is in a connected state
     */
    boolean isConnected();
}
