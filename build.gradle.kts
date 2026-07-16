plugins {
    id("com.android.application") version "8.6.1" apply false
    id("com.android.library") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // KSP вместо kapt: Room генерирует код быстрее, без стаба Java
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    // Firebase
    id("com.google.gms.google-services") version "4.4.4" apply false
    id("com.google.firebase.crashlytics") version "3.0.7" apply false
}
