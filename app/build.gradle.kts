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
val appVersion = "1.2.0"

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
        versionCode = 11
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

        // JDK dont jpackage tire le runtime embarqué, quand il doit différer de
        // celui qui exécute Gradle. Sous Linux la CI y met l'OpenJDK de la
        // distribution : avec le runtime Temurin, libVLC meurt en SIGSEGV dans
        // son démuxeur adaptive au démarrage de la lecture. Non défini → runtime
        // du JDK courant, comportement local inchangé.
        System.getenv("MOOVIE_JPACKAGE_JDK")?.let { javaHome = it }

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "Moo-vie"
            packageVersion = appVersion
            description = "Moo-vie — streaming, extraction de sources on-device"
            // Sans vendor, jpackage écrit « Unknown » dans le champ Maintainer.
            vendor = "JibayMcs"
            // JNA (vlcj) a besoin de sun.misc.Unsafe dans l'image jpackage.
            modules("jdk.unsupported")

            // Icônes des paquets natifs. Sans elles, jpackage retombe sur
            // l'icône Kotlin/Java par défaut. Les fichiers sont versionnés
            // (générés depuis docs/assets/sources/moo_vie_launcher_icon_master_1024x1024.png)
            // car la CI n'a pas d'outil de conversion ico/icns.
            linux {
                iconFile.set(project.file("icons/moovie.png"))
                debMaintainer = "theamateis@gmail.com"
            }
            windows { iconFile.set(project.file("icons/moovie.ico")) }
            macOS { iconFile.set(project.file("icons/moovie.icns")) }
        }
    }
}

// Dépendances Debian du paquet, et le plugin Compose n'expose aucun moyen de les
// surcharger. Celles que jpackage génère sont exactes mais collées à la machine
// de build : un build sur Ubuntu 22.04 exige `libpcre3`, parce que la glib de
// 22.04 s'appuie sur pcre1 alors que celle de 24.04 utilise pcre2. Le paquet ne
// s'installait donc plus sur 24.04 (`dpkg -i` en échec, état iU).
//
// On ne garde ici que les bibliothèques de *tête* et on laisse apt résoudre
// leurs enfants (libpcre, libbrotli, libharfbuzz, libxcb, libuuid…), dont les
// noms varient d'une version à l'autre. Les quatre codecs image sont listés
// explicitement : le runtime les lie dynamiquement et rien d'autre ne les tire —
// vérifié par la fermeture `ldd` du bundle, pas déduit. Les alternatives
// `xxx-t64 | xxx` couvrent le renommage dû à la transition time_t de 24.04.
val debPackageDependencies = listOf(
    "libc6",
    "libstdc++6",
    "libgcc-s1",
    "zlib1g",
    "libx11-6",
    "libxext6",
    "libxi6",
    "libxrender1",
    "libxtst6",
    "libgl1",
    "libfontconfig1",
    "libfreetype6",
    // Liée directement par le bundle. Sur 22.04 elle arrivait par ricochet, pas
    // sur 24.04 : sans elle, `libharfbuzz.so.0 => not found` à l'exécution.
    "libharfbuzz0b",
    // Codecs image de java.desktop (PNG/JPEG/GIF + gestion des couleurs).
    // Seul libpng a été renommé par la transition time_t : sur 24.04 c'est
    // libpng16-16t64 qui fournit libpng16.so.16, et libpng16-16 y existe encore
    // en paquet installable mais n'est pas celui qui est posé.
    "libpng16-16t64 | libpng16-16",
    // Pas d'alternative Debian ici, volontairement : `libjpeg62-turbo` fournit
    // l'ABI libjpeg.so.62, alors que le runtime est lié contre libjpeg.so.8.
    // L'alternative satisfaisait apt tout en laissant un symbole manquant à
    // l'exécution — mieux vaut un échec franc à l'installation. Le paquet cible
    // Ubuntu et ses dérivées ; Debian demanderait un runtime embarquant sa
    // propre libjpeg.
    "libjpeg-turbo8",
    "libgif7",
    "liblcms2-2",
    "libglib2.0-0t64 | libglib2.0-0",
    "libasound2t64 | libasound2",
    "xdg-utils",
).joinToString(", ")

/**
 * Réécrit `Depends:` dans le paquet produit par jpackage. Dépaquetage puis
 * reconstruction via dpkg-deb, en place : la CI récupère le `.deb` au même
 * chemin, sans rien changer à son script.
 */
val rewriteDebDependencies by tasks.registering {
    description = "Remplace les dépendances devinées par jpackage par un jeu figé."
    doLast {
        val debDir = layout.buildDirectory.dir("compose/binaries/main/deb").get().asFile
        val deb = debDir.listFiles()?.singleOrNull { it.name.endsWith(".deb") }
            ?: error("Aucun .deb unique trouvé dans $debDir")
        val work = File(temporaryDir, "repack").apply { deleteRecursively(); mkdirs() }

        providers.exec {
            commandLine("dpkg-deb", "--raw-extract", deb.absolutePath, work.absolutePath)
        }.result.get().assertNormalExitValue()

        val control = File(work, "DEBIAN/control")
        val patched = control.readLines().map { line ->
            if (line.startsWith("Depends:")) "Depends: $debPackageDependencies" else line
        }
        require(patched.any { it.startsWith("Depends: $debPackageDependencies") }) {
            "Ligne Depends introuvable dans ${control.absolutePath}"
        }
        control.writeText(patched.joinToString("\n", postfix = "\n"))

        providers.exec {
            commandLine("dpkg-deb", "--build", work.absolutePath, deb.absolutePath)
        }.result.get().assertNormalExitValue()
        logger.lifecycle("Dépendances du paquet figées : $debPackageDependencies")
    }
}

// jpackage ne tourne que sous Linux pour le .deb ; ailleurs la tâche n'existe pas.
tasks.matching { it.name == "packageDeb" }.configureEach { finalizedBy(rewriteDebDependencies) }
