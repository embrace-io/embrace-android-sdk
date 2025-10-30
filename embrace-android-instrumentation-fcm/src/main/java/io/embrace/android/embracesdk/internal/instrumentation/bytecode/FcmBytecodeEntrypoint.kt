package io.embrace.android.embracesdk.internal.instrumentation.bytecode

import androidx.annotation.Keep
import com.google.firebase.messaging.RemoteMessage
import io.embrace.android.embracesdk.internal.instrumentation.fcm.fcmDataSource

@Keep
object FcmBytecodeEntrypoint {

    @JvmStatic
    @Keep
    @Suppress("unused")
    fun onMessageReceived(message: RemoteMessage) {
        fcmDataSource?.logPushNotification(message)
    }
}
