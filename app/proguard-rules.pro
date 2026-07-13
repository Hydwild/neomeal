# ============================================================
#  XiaUp / NutriTrack — правила для release-сборки (R8)
# ============================================================

# Сохраняем номера строк в стеке крашей (для разбора логов)
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses

# --- Room ---
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.paging.**

# --- Gson + Retrofit (модели OpenFoodFacts сериализуются по именам полей) ---
-keepattributes Signature
-keep class com.google.gson.** { *; }
-keep class com.nutritrack.app.data.api.** { *; }
-keepclassmembers class com.nutritrack.app.data.** {
    <fields>;
}
# data-классы и поля с @SerializedName не должны переименовываться
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# --- Retrofit ---
-keepattributes Exceptions
-keep interface com.nutritrack.app.data.api.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# --- ML Kit barcode ---
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# --- Health Connect ---
-keep class androidx.health.connect.** { *; }
-dontwarn androidx.health.connect.**

# --- Room entities / модели приложения (на всякий случай, по именам) ---
-keep class com.nutritrack.app.data.db.entity.** { *; }
-keep class com.nutritrack.app.data.** { *; }

# --- Kotlin корутины / метаданные ---
-keepclassmembers class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**

# --- Parcelable / Serializable (если используются) ---
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# --- ViewBinding ---
-keep class com.nutritrack.app.databinding.** { *; }

# --- Crashlytics: читаемые стектрейсы ---
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# --- SQLCipher ---
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

# --- AndroidX Security (EncryptedSharedPreferences) ---
-keep class androidx.security.crypto.** { *; }
-dontwarn com.google.crypto.tink.**
