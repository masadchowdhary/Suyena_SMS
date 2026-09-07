# =========================================
# ProGuard Rules for Suyena SMS
# =========================================

# --- General Android Rules ---
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes Exceptions

# Keep the application class
-keep public class * extends android.app.Application

# Keep all activities
-keep public class * extends android.app.Activity
-keep public class * extends androidx.activity.ComponentActivity

# --- Kotlin ---
-dontwarn kotlin.**
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# --- Jetpack Compose ---
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# --- Android Telephony (SMS) ---
-keep class android.telephony.** { *; }
-keep class android.telephony.SmsManager { *; }

# --- Material3 ---
-keep class androidx.compose.material3.** { *; }
-dontwarn androidx.compose.material3.**

# --- AndroidX Core ---
-keep class androidx.core.** { *; }
-keep class androidx.lifecycle.** { *; }

# --- Keep R classes ---
-keepclassmembers class **.R$* {
    public static <fields>;
}

# --- Keep Parcelable ---
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# --- Keep Serializable ---
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# --- Prevent stripping of PendingIntent usage ---
-keep class android.app.PendingIntent { *; }

# --- Remove logging in release ---
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
