package com.ubermicrostudios.messagesenhanced.nativeapp

import android.app.Application
import android.os.Looper
import coil.Coil
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.ubermicrostudios.messagesenhanced.nativeapp.data.cache.InboxCache
import com.ubermicrostudios.messagesenhanced.nativeapp.push.MessagingService
import com.ubermicrostudios.messagesenhanced.nativeapp.push.Push
import com.ubermicrostudios.messagesenhanced.nativeapp.update.UpdateCheckWorker
import com.ubermicrostudios.messagesenhanced.nativeapp.util.EmojiCatalog
import com.ubermicrostudios.messagesenhanced.nativeapp.util.bindPhoneDisplay
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MessagesEnhancedApp : Application(), ImageLoaderFactory {

    @Inject lateinit var imageLoader: ImageLoader
    @Inject lateinit var inboxCache: InboxCache

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        bindPhoneDisplay(this)
        Coil.setImageLoader(imageLoader)
        inboxCache.warm()
        val app = this
        Looper.getMainLooper().queue.addIdleHandler {
            appScope.launch(Dispatchers.IO) {
                runCatching { EmojiCatalog.load(app) }
                runCatching { MessagingService.ensureChannels(app) }
                runCatching {
                    if (Push.loadConfig(app) != null) Push.initSaved(app)
                }
                runCatching { UpdateCheckWorker.schedule(app) }
            }
            false
        }
    }

    override fun newImageLoader(): ImageLoader = imageLoader
}
