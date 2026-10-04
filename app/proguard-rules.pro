-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, RuntimeVisibleAnnotations, EnclosingMethod, SourceFile, LineNumberTable

# kotlinx.serialization: keep generated serializers of our @Serializable models
-keepclassmembers class com.trackr.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.trackr.app.**$$serializer { *; }
-keepclasseswithmembers class com.trackr.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit service interfaces (generic return types read reflectively)
-keep interface com.trackr.app.data.remote.tmdb.TmdbApi { *; }
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# Apollo generated models/adapters
-keep class com.trackr.app.anilist.** { *; }

# Ktor / supabase-kt reference optional JVM-only classes
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Room entities are referenced by generated code; Hilt workers by name
-keep class com.trackr.app.data.sync.SyncWorker { *; }

# Glance instantiates widget action callbacks by class name.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
