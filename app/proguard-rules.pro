# Tink (via security-crypto) references compile-only annotations.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**
-dontwarn com.google.crypto.tink.**

# OkHttp / Okio. Platform sockets and optional security providers are referenced reflectively.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# kotlinx.serialization (Retrofit models and the GitHub release DTOs).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-dontwarn kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class **$$serializer {
    *;
}
-keep class com.ubermicrostudios.messagesenhanced.nativeapp.**$$serializer { *; }
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-keepclassmembers class com.ubermicrostudios.messagesenhanced.nativeapp.** {
    *** Companion;
}
-keepclasseswithmembers class com.ubermicrostudios.messagesenhanced.nativeapp.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class com.ubermicrostudios.messagesenhanced.nativeapp.** { *; }

# Hilt
-dontwarn dagger.hilt.android.internal.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.* <methods>;
    @dagger.hilt.* <fields>;
}
-keepclasseswithmembers class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
    @javax.inject.Inject <methods>;
}
-keep class com.ubermicrostudios.messagesenhanced.nativeapp.**_Factory { *; }
-keep class com.ubermicrostudios.messagesenhanced.nativeapp.**_MembersInjector { *; }
-keep class com.ubermicrostudios.messagesenhanced.nativeapp.**Hilt_* { *; }
-keep class com.ubermicrostudios.messagesenhanced.nativeapp.**_HiltModules* { *; }
-keep class hilt_aggregated_deps.** { *; }

# Coil
-dontwarn coil.**
-keep class coil.** { *; }

# Firebase Messaging. The service class is named from the manifest and by FCM.
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
-keep class com.google.firebase.messaging.** { *; }
-keep class com.google.firebase.installations.** { *; }
-keep class com.google.firebase.components.** { *; }
-keep class com.ubermicrostudios.messagesenhanced.nativeapp.push.MessagingService { *; }

# PackageInstaller result receiver.
-keep class com.ubermicrostudios.messagesenhanced.nativeapp.update.UpdateInstallReceiver { *; }

# Coroutines. ServiceLoader entry points and atomic updaters must keep their names.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.coroutines.SafeContinuation {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**
-dontwarn java.lang.instrument.ClassFileTransformer
-dontwarn java.lang.instrument.Instrumentation
-dontwarn sun.misc.SignalHandler
-dontwarn sun.misc.Signal
