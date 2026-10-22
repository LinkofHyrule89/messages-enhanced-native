package com.ubermicrostudios.messagesenhanced.nativeapp

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.ubermicrostudios.messagesenhanced.nativeapp.push.MessagingService
import com.ubermicrostudios.messagesenhanced.nativeapp.util.EmojiCatalog
import com.ubermicrostudios.messagesenhanced.nativeapp.push.Push
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MessagesEnhancedApp : Application(), ImageLoaderFactory {

    @Inject lateinit var imageLoader: ImageLoader

    override fun onCreate() {
        super.onCreate()
        EmojiCatalog.load(this)
        Coil.setImageLoader(imageLoader)
        runCatching { MessagingService.ensureChannels(this) }
        if (Push.loadConfig(this) != null) Push.initSaved(this)
    }

    override fun newImageLoader(): ImageLoader = imageLoader
}
