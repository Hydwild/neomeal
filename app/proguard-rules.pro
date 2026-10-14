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

# --- Формат файла бэкапа ---
# BackupData и Envelope лежат в utils и под правила выше НЕ подпадали: R8
# переименовывал их поля (products → c, avatars → o, salt → c), и именно эти
# буквы Gson писал в файл. Формат бэкапа оказывался завязан на обфускацию —
# файл, созданный одной сборкой, мог не прочитаться другой, и пользователь
# терял данные при восстановлении. Имена полей обязаны быть стабильными.
-keep class com.nutritrack.app.utils.BackupManager$BackupData { *; }
-keep class com.nutritrack.app.utils.BackupManager$Envelope { *; }

# --- Gson TypeToken в полном режиме R8 ---
# Gson 2.10.1 не приносит своих R8-правил (они появились в 2.11). Без них
# полный режим R8 (по умолчанию в AGP 8) срезал generic-сигнатуру у анонимных
# `object : TypeToken<List<X>>() {}` и вдобавок слил наследников из
# MealTemplates и UserRecipes в один класс. Gson на таком TypeToken бросает
# исключение, load() молча возвращал пустой список: шаблоны приёмов не
# появлялись, свои рецепты пропадали после перезапуска, а следующее
# сохранение затирало прежние. Правила ниже — те же, что Gson 2.11 кладёт в
# META-INF/proguard/gson.pro. keep заодно запрещает слияние классов.
-keepattributes Signature
-keep,allowobfuscation class com.google.gson.reflect.TypeToken
-keep,allowobfuscation class * extends com.google.gson.reflect.TypeToken

# --- Шаблоны приёмов пищи (JSON в prefs и в бэкапе) ---
# Лежат в utils и под правила data.** не подпадали: R8 переименовывал поля
# (name → a, items → b, calories → d…), и эти буквы попадали в сохранённый JSON.
# Старые записи читает MealTemplates.parse по таблице прежних имён.
-keep class com.nutritrack.app.utils.MealTemplates$Template { *; }
-keep class com.nutritrack.app.utils.MealTemplates$Item { *; }

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
