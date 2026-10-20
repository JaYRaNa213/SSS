# ============================================================
# SSS App - ProGuard / R8 Rules
# ============================================================

# ── Kotlin ────────────────────────────────────────────────────
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Lazy {
    *;
}

# ── Kotlin Coroutines ─────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ── Hilt / Dagger ─────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class dagger.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keep @dagger.Module class * { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}
-dontwarn dagger.**
-dontwarn hilt_aggregated_deps.**

# ── Room ──────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers @androidx.room.Entity class * {
    <fields>;
}
# Keep all SSS Room entities and DAOs explicitly
-keep class com.sss.app.data.local.** { *; }

# ── App Classes (Services, Application, Activities) ───────────
-keep class com.sss.app.SssApplication { *; }
-keep class com.sss.app.MainActivity { *; }
-keep class com.sss.app.capture.OverlayBubbleService { *; }
-keep class com.sss.app.capture.ScreenshotObserver { *; }

# ── Jetpack Compose ───────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
# Keep Composable functions from being removed
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ── Coil 3 ───────────────────────────────────────────────────
-keep class coil3.** { *; }
-dontwarn coil3.**
-keep interface coil3.** { *; }

# ── ML Kit Text Recognition ───────────────────────────────────
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ── AndroidX Navigation ───────────────────────────────────────
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# ── AndroidX Lifecycle / ViewModel ───────────────────────────
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ── AndroidX SavedState ───────────────────────────────────────
-keep class androidx.savedstate.** { *; }
-dontwarn androidx.savedstate.**

# ── FileProvider / ContentProvider ───────────────────────────
-keep class androidx.core.content.FileProvider { *; }

# ── Serialization (for any data classes passed between composables) ──
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ── General Android ───────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses,EnclosingMethod

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep enum values (used in Kotlin sealed classes, etc.)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Suppress common warnings ──────────────────────────────────
-dontwarn java.lang.invoke.**
-dontwarn sun.misc.Unsafe
-dontwarn org.jetbrains.**
-dontwarn com.sun.**
