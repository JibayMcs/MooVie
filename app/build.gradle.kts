import java.util.Properties
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Version unique Android + desktop (l'updater compare les tags GitHub à cette
// valeur ; côté desktop elle est injectée en propriété système moovie.version).
val appVersion = "1.1.0"

// Signature release : keystore.properties en local (gitignoré), variables
// d'environnement en CI (KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(propKey: String, envKey: String): String? =
    keystoreProps.getProperty(propKey) ?: System.getenv(envKey)

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.ui)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.components.resources)
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }

        // Libs JVM pures partagées entre Android et desktop (Retrofit, OkHttp,
        // jsoup…) : pas de variante KMP mais les deux cibles sont JVM →
        // sourceset intermédiaire commun.
        val jvmCommon by creating {
            dependsOn(commonMain)
            dependencies {
                implementation("com.squareup.retrofit2:retrofit:2.11.0")
                implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
                implementation("com.squareup.okhttp3:okhttp:4.12.0")
                implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
                // DNS-over-HTTPS : contourne le blocage DNS des FAI sur les domaines sources
                implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")
                implementation("org.jsoup:jsoup:1.18.1")
                // Images multiplateforme (Coil 3), fetcher réseau OkHttp
                implementation("io.coil-kt.coil3:coil-compose:3.0.4")
                implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
                // Réglages persistants (artefact KMP ; le chemin du fichier est
                // fourni par expect/actual moovieDataStoreFile)
                implementation("androidx.datastore:datastore-preferences-core:1.1.1")
                // ViewModels multiplateformes (androidx.lifecycle réel côté Android,
                // port JetBrains côté desktop)
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.8.4")
            }
        }

        val androidMain by getting {
            dependsOn(jvmCommon)
            dependencies {
                implementation(project.dependencies.platform("androidx.compose:compose-bom:2024.10.01"))

                implementation("androidx.core:core-ktx:1.13.1")
                implementation("androidx.core:core-splashscreen:1.0.1")
                implementation("androidx.activity:activity-compose:1.9.3")
                implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
                implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
                implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

                // Compose for TV (en cours de retrait au profit des composants partagés)
                implementation("androidx.tv:tv-material:1.0.0")

                // Lecture vidéo native (Android uniquement — VLCJ prévu côté desktop)
                implementation("androidx.media3:media3-exoplayer:1.4.1")
                implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
                implementation("androidx.media3:media3-exoplayer-dash:1.4.1")
                implementation("androidx.media3:media3-ui:1.4.1")
                implementation("androidx.media3:media3-datasource-okhttp:1.4.1")
                implementation("androidx.media3:media3-session:1.4.1")
            }
        }

        val desktopMain by getting {
            dependsOn(jvmCommon)
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
                // Lecture vidéo desktop via libVLC (VLC doit être installé)
                implementation("uk.co.caprica:vlcj:4.8.3")
            }
        }
    }
}

android {
    namespace = "fr.moovie.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.moovie.tv"
        minSdk = 23
        targetSdk = 34
        versionCode = 10
        versionName = appVersion
    }

    signingConfigs {
        create("release") {
            val storePath = signingValue("storeFile", "KEYSTORE_FILE")
            if (storePath != null) {
                storeFile = rootProject.file(storePath)
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

compose.resources {
    // Res accessible depuis jvmCommon (strings FR/EN/ES partagées TV + desktop)
    packageOfResClass = "fr.moovie.tv.resources"
}

compose.desktop {
    application {
        mainClass = "fr.moovie.tv.desktop.MainKt"
        // Version lue au runtime par l'updater desktop (bannière de mise à jour).
        jvmArgs += "-Dmoovie.version=$appVersion"

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "Moo-vie"
            packageVersion = appVersion
            description = "Moo-vie — streaming, extraction de sources on-device"
            // JNA (vlcj) a besoin de sun.misc.Unsafe dans l'image jpackage.
            modules("jdk.unsupported")

            // Icônes des paquets natifs. Sans elles, jpackage retombe sur
            // l'icône Kotlin/Java par défaut. Les fichiers sont versionnés
            // (générés depuis docs/assets/sources/moo_vie_launcher_icon_master_1024x1024.png)
            // car la CI n'a pas d'outil de conversion ico/icns.
            linux { iconFile.set(project.file("icons/moovie.png")) }
            windows { iconFile.set(project.file("icons/moovie.ico")) }
            macOS { iconFile.set(project.file("icons/moovie.icns")) }
        }
    }
}
