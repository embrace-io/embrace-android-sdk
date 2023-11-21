package io.embrace.android.embracesdk.capture.crumbs

import android.app.Activity
import android.os.Bundle
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.PushNotificationBreadcrumb.NotificationType
import io.embrace.android.embracesdk.session.lifecycle.ActivityLifecycleListener

/**
 * In charge of handling all notifications related functionality.
 */
internal class PushNotificationCaptureService(
    private val breadCrumbService: BreadcrumbService,
    private val logger: InternalEmbraceLogger
) : ActivityLifecycleListener {

    companion object Utils {

        enum class PRIORITY(val priority: Int) {
            PRIORITY_UNKNOWN(0),
            PRIORITY_HIGH(1),
            PRIORITY_NORMAL(2)
        }

        private const val RESERVED_PREFIX_COM_GOOGLE_FIREBASE = "com.google.firebase"
        private const val RESERVED_PREFIX_PAYLOAD_KEYS = "google."
        private const val RESERVED_PREFIX_NOTIFICATION_KEYS = "gcm."
        private const val RESERVED_FROM = "from"
        private const val RESERVED_MESSAGE_TYPE = "message_type"
        private const val RESERVED_COLLAPSE_KEY = "collapse_key"
        private const val RESERVED_GOOGLE_MESSAGE_ID = "google.message_id"
        private const val RESERVED_GOOGLE_DELIVERED_PRIORITY = "google.delivered_priority"

        /**
         * This is so to have compatibility with com.google.firebase.messaging.RemoteMessage.
         * For some reason they don't use String, but convert it to int instead. It is either doing
         * this, or adding com.google.firebase:firebase-messaging as a dependency on the sdk and
         * add some more complex code.
         */

        fun getMessagePriority(priority: String?) =
            when (priority) {
                "high" -> PRIORITY.PRIORITY_HIGH.priority
                "normal" -> PRIORITY.PRIORITY_NORMAL.priority
                else -> PRIORITY.PRIORITY_UNKNOWN.priority
            }

        fun extractDeveloperDefinedPayload(bundle: Bundle): Map<String, String> {
            val keySet = bundle.keySet() ?: return emptyMap()
            return keySet.filter {
                // let's filter all google reserved words, leaving us with user defined keys
                !it.startsWith(RESERVED_PREFIX_PAYLOAD_KEYS) &&
                    !it.startsWith(RESERVED_PREFIX_NOTIFICATION_KEYS) &&
                    !it.startsWith(RESERVED_PREFIX_COM_GOOGLE_FIREBASE) &&
                    it != RESERVED_FROM &&
                    it != RESERVED_MESSAGE_TYPE &&
                    it != RESERVED_COLLAPSE_KEY
            }.associateWith { bundle.getString(it) ?: "" }
        }
    }

    /**
     * Saves captured push notification information into session payload
     *
     * @param title    the title of the notification as a string (or null)
     * @param body     the body of the notification as a string (or null)
     * @param topic    the notification topic (if a user subscribed to one), or null
     * @param id       A unique ID identifying the message
     * @param notificationPriority the priority of the message (as resolved on the device)
     * @param messageDeliveredPriority the priority of the message (as resolved on the server)
     * @param type the notification type
     */
    fun logPushNotification(
        title: String?,
        body: String?,
        topic: String?,
        id: String?,
        notificationPriority: Int?,
        messageDeliveredPriority: Int,
        type: NotificationType
    ) {
        breadCrumbService.logPushNotification(
            title,
            body,
            topic,
            id,
            notificationPriority,
            messageDeliveredPriority,
            type
        )
    }

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        if (isComingFromPushNotification(activity)) {
            logger.logInfo("Coming from a Firebase push notification")
            with(activity.intent.extras) {
                if (this == null) {
                    logger.logWarning(
                        "It seems like we are coming from a Google Push " +
                            "Notification, but intent extras is null. Will not be able to log it " +
                            "to our dashboard."
                    )
                    return
                }

                logPushNotification(
                    // ** all these fields do not come as part of the Intent ** //
                    title = null,
                    body = null,
                    notificationPriority = null,
                    // ** //
                    topic = getString(RESERVED_FROM),
                    id = getString(RESERVED_GOOGLE_MESSAGE_ID),
                    messageDeliveredPriority = getMessagePriority(
                        getString(RESERVED_GOOGLE_DELIVERED_PRIORITY)
                    ),
                    type = determineNotificationType(this)
                )
            }
        }
    }

    private fun determineNotificationType(bundle: Bundle): NotificationType {
        val hasData = extractDeveloperDefinedPayload(bundle).isNotEmpty()

        // whenever we come through this flow of push notification, we know certainly that it has
        // notification block. This is because if the push notification is data only
        // (w/o notification block), then it wouldn't come through this flow, but through
        // FirebaseSwazzledHooks._onMessageReceived instead. Now it is a matter of determining if
        // it's a notification only or notification + data.
        return if (hasData) {
            NotificationType.NOTIFICATION_AND_DATA
        } else {
            NotificationType.NOTIFICATION
        }
    }

    /**
     * It determines if this Activity is coming from a Google push notification.
     */
    private fun isComingFromPushNotification(activity: Activity): Boolean {
        return activity.intent?.extras?.keySet()?.containsAll(
            listOf(RESERVED_FROM, RESERVED_GOOGLE_MESSAGE_ID)
        ) ?: false
    }
}
