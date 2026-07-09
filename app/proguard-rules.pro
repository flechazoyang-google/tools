# Toolbox proguard rules
# Retrofit / Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class kotlin.coroutines.Continuation
-keep class com.example.toolbox.data.remote.model.** { *; }
# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.**
# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
