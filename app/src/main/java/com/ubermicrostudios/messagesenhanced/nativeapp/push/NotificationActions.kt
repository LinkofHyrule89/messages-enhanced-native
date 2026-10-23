package com.ubermicrostudios.messagesenhanced.nativeapp.push

/** Intent contract shared by the notification actions and the receiver that handles them. */
object NotificationActions {
    const val ACTION_REPLY = "com.ubermicrostudios.messagesenhanced.nativeapp.NOTIF_REPLY"
    const val ACTION_READ = "com.ubermicrostudios.messagesenhanced.nativeapp.NOTIF_READ"
    const val ACTION_DISMISSED = "com.ubermicrostudios.messagesenhanced.nativeapp.NOTIF_DISMISSED"

    const val EXTRA_CONV = "conv"
    const val EXTRA_TITLE = "title"
    const val EXTRA_GROUP = "group"

    const val KEY_REPLY = "reply_text"
}
