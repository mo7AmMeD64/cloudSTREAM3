package com.kodastream.app.push

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kodastream.app.MainActivity
import com.kodastream.app.R
import com.kodastream.app.data.AppPrefs

/**
 * One broadcast topic for announcements. The app never stores a device token
 * or a user id, and no send key ships in the APK: sending stays in the Firebase
 * console, so a leaked APK cannot push messages.
 */
object Push {
    private const val TOPIC = "announcements"
    const val CHANNEL_ID = "announcements" // also in AndroidManifest default channel

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notifications_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notifications_channel_description)
            },
        )
    }

    /** The pref and the FCM subscription move together, so the switch never lies. */
    fun setEnabled(context: Context, enabled: Boolean) {
        AppPrefs.setNotificationsEnabled(context, enabled)
        val messaging = FirebaseMessaging.getInstance()
        if (enabled) {
            ensureChannel(context)
            messaging.subscribeToTopic(TOPIC)
        } else {
            messaging.unsubscribeFromTopic(TOPIC)
        }
    }

    /** Re-asserts the subscription on launch; subscribing twice is harmless. */
    fun sync(context: Context) {
        ensureChannel(context)
        if (AppPrefs.notificationsEnabled(context)) {
            FirebaseMessaging.getInstance().subscribeToTopic(TOPIC)
        }
    }

    /** The switch state: both the app's choice and the system permission. */
    fun isOn(context: Context): Boolean =
        AppPrefs.notificationsEnabled(context) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    @SuppressLint("MissingPermission") // areNotificationsEnabled() covers both the API 33 grant and the app toggle.
    fun show(context: Context, title: String?, body: String?) {
        if (title.isNullOrBlank() && body.isNullOrBlank()) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel(context)

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(open)
        if (body != null) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }
        NotificationManagerCompat.from(context)
            .notify(System.currentTimeMillis().toInt(), builder.build())
    }
}

/** Foreground deliveries: the system displays background notification messages itself. */
class PushService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        Push.show(
            this,
            message.notification?.title ?: message.data["title"],
            message.notification?.body ?: message.data["body"],
        )
    }
}
