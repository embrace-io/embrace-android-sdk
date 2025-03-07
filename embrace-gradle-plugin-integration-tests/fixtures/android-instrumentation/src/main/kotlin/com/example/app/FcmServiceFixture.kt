package com.example.app

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FcmServiceFixture : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
    }
}
