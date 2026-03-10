# MIRA ProGuard rules

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { CREATOR <fields>; }
-keep @kotlinx.serialization.Serializable class * { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ONNX Runtime
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# SQLCipher
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.** { *; }

# Sentry
-keep class io.sentry.** { *; }

# Our models (needed for JSON deserialization)
-keep class dev.mira.app.model.** { *; }
-keep class dev.mira.app.db.** { *; }

# Accessibility Service
-keep class dev.mira.app.service.MiraAccessibilityService { *; }

# WorkManager workers
-keep class dev.mira.app.worker.** { *; }
