import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "1.9.23"
    id("org.jetbrains.compose") version "1.6.10"
}

group = "com.example.prayerapp"
version = "1.0"

repositories {
    google()
    mavenCentral()
}

dependencies {
    // التبعية الأساسية لـ Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    val iconsVersion = "1.6.10"

    // "Core" تحتوي على الأيقونات الشائعة (مثل MoreVert, Refresh)
    implementation("org.jetbrains.compose.material:material-icons-core-desktop:$iconsVersion")

    // "Extended" تحتوي على أيقونات إضافية (مثل Link)
    // هذا السطر هو الذي سيحل المشكلة
    implementation("org.jetbrains.compose.material:material-icons-extended-desktop:$iconsVersion")


    // مكتبة JSON (للـ JSONObject و JSONArray)
    implementation("org.json:json:20240303")

    // لدعم JavaFX لتشغيل الصوت
    implementation("org.openjfx:javafx-media:21")
}

compose.desktop {
    application {
        mainClass = "com.example.prayerapp.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "PrayerApp"
            packageVersion = "1.0.0"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

