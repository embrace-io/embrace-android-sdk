package io.embrace.android.exampleapp.ui.examples.bytecode

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import com.google.firebase.messaging.RemoteMessage
import io.embrace.android.exampleapp.R

class BytecodeFcmPushNotificationActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bytecode_fcm_push_notification)

        findViewById<View>(R.id.btn_bytecode_fcm_push_notification).setOnClickListener {
            val msg = RemoteMessage.Builder("my-id")
                .setMessageId("my-message-id")
                .setMessageType("my-message-type")
                .setData(mapOf("key" to "value"))
                .build()
            ExamplePushNotificationService().onMessageReceived(msg)
        }
    }
}
