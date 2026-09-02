import java.nio.file.Files
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
//
// Un suffixe semver — « -rc.1 » — désigne une préversion : le tag correspondant
// sort en pré-release GitHub et reste invisible pour les updaters intégrés.
val appVersion = "1.23.1-rc.1"

/**
 * La même version, telle que **jpackage** l'accepte : purement numérique.
 *
 * Le MSI et le DMG refusent tout suffixe — un `-rc.1` fait échouer l'emballage,
 * et la release ne sortirait alors que l'APK, en laissant les utilisateurs
 * desktop sur la version précédente avec une bannière qui ne pointe sur rien.
 * L'APK, l'AppImage et la version affichée dans l'app gardent, eux, le suffixe :
 * eux seuls savent dire ce qu'ils sont.
 */
val jpackageVersion = appVersion.substringBefore('-')

// Signature release : keystore.properties en local (gitignoré), variables
// d'environnement en CI (KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(propKey: String, envKey: String): String? =
    keystoreProps.getProperty(propKey) ?: System.getenv(envKey)

// Clé de consumer OpenSubtitles. Même dispositif que le keystore : fichier
// gitignoré en local, secret GitHub en CI, injectée à la compilation.
//
// Elle identifie *l'application*, pas l'utilisateur : OpenSubtitles impose une
// clé unique par application et bannit l'accès de ceux qui demandent la leur à
// leurs utilisateurs. Elle ne peut donc pas être saisie dans les réglages comme
// celle de TMDB, et n'a rien à faire dans un dépôt public — d'où l'injection.
//
// Absente (build depuis les sources sans clé), les sous-titres se désactivent
// proprement plutôt que de faire échouer la compilation.
val openSubtitlesProps = Properties().apply {
    val f = rootProject.file("opensubtitles.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val openSubtitlesApiKey: String =
    openSubtitlesProps.getProperty("apiKey")
        ?: System.getenv("OPENSUBTITLES_API_KEY")
        ?: ""

/**
 * Équivalent iOS de `BuildConfig`.
 *
 * La version affichée et la clé OpenSubtitles atteignent l'APK par un champ
 * `BuildConfig` et le desktop par une propriété système passée à la JVM.
 * Kotlin/Native n'a ni l'un ni l'autre : pas de `System.getProperty`, et rien
 * n'injecte de constante dans un binaire natif après coup. On génère donc une
 * source, ce qui garde l'unique `appVersion` de ce fichier comme seule origine
 * du numéro de version sur les quatre plateformes.
 *
 * Passer par `Info.plist` aurait été possible pour la version, mais pas pour la
 * clé : le plist est en clair dans le bundle, là où une constante compilée est
 * au moins noyée dans le binaire — même protection, faible, que côté Android.
 */
val genererBuildConfigIos by tasks.registering {
    val sortie = layout.buildDirectory.dir("generated/moovie/ios")
    // Sans ces deux `inputs`, Gradle considérerait la tâche à jour après un
    // changement de version ou de clé, et le binaire iOS embarquerait
    // silencieusement les anciennes valeurs.
    inputs.property("version", appVersion)
    inputs.property("cleOpenSubtitles", openSubtitlesApiKey)
    outputs.dir(sortie)
    doLast {
        // Échappement : la clé vient d'un secret CI, rien ne garantit qu'elle
        // ne porte pas de guillemet ou d'antislash.
        fun litteral(v: String) = v.replace("\\", "\\\\").replace("\"", "\\\"")
        val f = sortie.get().file("MoovieBuildConfig.kt").asFile
        f.parentFile.mkdirs()
        f.writeText(
            """
            // Généré par la tâche Gradle `genererBuildConfigIos`. Ne pas éditer.
            package fr.moovie.tv.shared

            internal const val VERSION_GENEREE: String = "${litteral(appVersion)}"
            internal const val CLE_OPENSUBTITLES_GENEREE: String = "${litteral(openSubtitlesApiKey)}"
            """.trimIndent() + "\n",
        )
    }
}

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

    // Cibles iOS.
    //
    // Kotlin/Native ne sait compiler une cible Apple que **depuis macOS** : la
    // toolchain est celle de Xcode. Sur Linux et Windows, Gradle configure ces
    // cibles sans broncher mais leurs tâches de compilation sont inexécutables.
    // C'est sans conséquence pour les autres plateformes — `assembleRelease` et
    // `packageDistributionForCurrentOS` ne dépendent d'aucune d'elles — et cela
    // veut dire que la vérification de compilation iOS appartient au runner
    // macOS de la CI, pas au poste de développement.
    //
    // iosX64 est le simulateur sur Mac Intel, iosSimulatorArm64 celui des Mac
    // Apple Silicon, iosArm64 l'appareil réel — seule cette dernière entre dans
    // le .ipa.
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { cible ->
        cible.binaries.framework {
            baseName = "MoovieShared"
            // Statique, et non dynamique : Compose Multiplatform embarque son
            // propre moteur de rendu Skia, et un .ipa distribué hors App Store
            // n'a aucun mécanisme de partage de frameworks entre applications.
            // Le lien statique évite en prime l'étape d'embarquement de
            // framework dans le bundle, que la signature AltStore n'aime pas.
            isStatic = true
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
                // Explicite depuis que la couche sources est en commun : elle
                // référence `CoroutineDispatcher`. Les cibles JVM la tiraient
                // jusque-là transitivement, à la même version — rien ne change
                // pour elles.
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                // Réglages persistants. L'artefact était déjà multiplateforme,
                // il était simplement déclaré côté JVM faute d'utilisateur
                // commun ; le chemin du fichier vient de l'expect/actual
                // `moovieDataStoreChemin`.
                implementation("androidx.datastore:datastore-preferences-core:1.1.1")
                // `createWithPath` prend un `okio.Path` là où l'API JVM prenait
                // un `File`. DataStore tire déjà okio, on le déclare pour ne pas
                // dépendre d'une transitivité.
                implementation("com.squareup.okio:okio:3.9.1")
                // ViewModels multiplateformes. Le port JetBrains vise aussi iOS :
                // ces artefacts étaient déclarés côté JVM faute d'utilisateur
                // commun, pas par contrainte. Les remonter ici est ce qui permet
                // aux ViewModels de l'application de devenir partagés.
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.8.4")
                // LocalViewModelStoreOwner, que ProfileHost redéfinit pour donner
                // à chaque profil son propre magasin de ViewModels — sans quoi
                // `viewModel()` rend l'instance construite sous le profil
                // précédent.
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
                // Ktor : client HTTP commun de la couche TMDB. Le *moteur* reste
                // par plateforme — OkHttp côté JVM, ce qui permet de réutiliser
                // le client existant et son cache disque sans rien changer au
                // comportement d'Android et du desktop.
                implementation("io.ktor:ktor-client-core:3.0.3")
                implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
                // ksoup : portage Kotlin/Multiplatform de jsoup, même API de
                // sélection CSS. Remplace `org.jsoup`, qui est du Java pur.
                implementation("com.fleeksoft.ksoup:ksoup:0.2.0")
                // Dates : analyse ISO, calendrier local, arithmétique. Le
                // *formatage localisé*, lui, reste en expect/actual — aucune
                // bibliothèque multiplateforme ne connaît les motifs de date
                // propres à chaque langue, seuls les OS les portent.
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
                // Coil 3 est multiplateforme ; seul son moteur réseau ne l'est
                // pas, et celui-ci reste déclaré par cible.
                implementation("io.coil-kt.coil3:coil-compose:3.0.4")
            }
        }

        // Libs JVM pures partagées entre Android et desktop (Retrofit, OkHttp,
        // jsoup…) : pas de variante KMP mais les deux cibles sont JVM →
        // sourceset intermédiaire commun.
        val jvmCommon by creating {
            dependsOn(commonMain)
            dependencies {
                implementation("com.squareup.okhttp3:okhttp:4.12.0")
                implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
                // DNS-over-HTTPS : contourne le blocage DNS des FAI sur les domaines sources
                implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")
                // Moteur Ktor adossé à OkHttp : `preconfigured` lui passe le
                // client existant, cache et interceptors compris.
                implementation("io.ktor:ktor-client-okhttp:3.0.3")
                implementation("org.jsoup:jsoup:1.18.1")
                // Encodage QR seulement (Java pur, sans dépendance Android) :
                // l'appairage du téléphone en affiche un. Le codage QR est du
                // Reed-Solomon et du masquage — pas quelque chose qu'on écrit
                // à la main pour économiser un jar.
                implementation("com.google.zxing:core:3.5.3")
                // Fetcher réseau OkHttp de Coil : lui seul est propre à la JVM.
                implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
            }
        }

        // Pendant iOS de `jvmCommon` : ce que les deux cibles JVM tirent de
        // Retrofit/OkHttp/jsoup, Kotlin/Native doit le tirer d'ailleurs. Le
        // source set est déclaré à la main parce que le Default Hierarchy
        // Template est désactivé sur ce projet — les `dependsOn` explicites de
        // `jvmCommon` le désactivent pour tout le module, `iosMain` ne serait
        // donc pas créé tout seul.
        val iosMain by creating {
            dependsOn(commonMain)
            // Dépendance de tâche portée par le srcDir : Gradle génère le
            // fichier avant de compiler, sans qu'on ait à câbler un `dependsOn`
            // sur chacune des trois tâches de compilation iOS.
            kotlin.srcDir(genererBuildConfigIos)
            dependencies {
                // Ktor remplace OkHttp. Le moteur Darwin s'appuie sur
                // NSURLSession, seule pile HTTP disponible sans JVM.
                implementation("io.ktor:ktor-client-core:3.0.3")
                implementation("io.ktor:ktor-client-darwin:3.0.3")
                // Pendant iOS du fetcher OkHttp : Coil passe par Ktor, donc par
                // NSURLSession.
                implementation("io.coil-kt.coil3:coil-network-ktor3:3.0.4")
                // Chiffrement. Uniquement côté iOS : la JVM garde `javax.crypto`
                // tel quel, il n'y a aucune raison de remplacer une pile qui
                // marche chez les utilisateurs Android et desktop. Le provider
                // Apple s'adosse à CommonCrypto et CryptoKit.
                implementation("dev.whyoleg.cryptography:cryptography-core:0.4.0")
                implementation("dev.whyoleg.cryptography:cryptography-provider-apple:0.4.0")
            }
        }
        val iosX64Main by getting { dependsOn(iosMain) }
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                // runTest : la politique de résolution est suspend, elle se teste
                // sans dispatcher réel ni attente.
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            }
        }

        // Miroir de jvmCommon côté tests : le domaine des sources et ses adapters
        // se testent une seule fois pour les deux cibles.
        val jvmCommonTest by creating {
            dependsOn(commonTest)
        }

        val androidMain by getting {
            dependsOn(jvmCommon)
            dependencies {
                implementation(project.dependencies.platform("androidx.compose:compose-bom:2024.10.01"))

                implementation("androidx.core:core-ktx:1.13.1")
                implementation("androidx.core:core-splashscreen:1.0.1")
                // Décodage du WebP animé du splash (ImageDecoder, API 28+).
                implementation("io.coil-kt.coil3:coil-gif:3.0.4")
                implementation("androidx.activity:activity-compose:1.9.3")
                implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
                implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
                implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

                // Compose for TV (en cours de retrait au profit des composants partagés)
                implementation("androidx.tv:tv-material:1.0.0")

                // Lecture vidéo native (Android uniquement — le desktop est sur libmpv)
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
                // Lecture vidéo desktop via libmpv (embarquée dans les paquets),
                // pilotée par un binding JNA maison — voir desktop/mpv/Libmpv.kt.
                implementation("net.java.dev.jna:jna-jpms:5.14.0")
            }
        }

        val desktopTest by getting { dependsOn(jvmCommonTest) }
        val androidUnitTest by getting { dependsOn(jvmCommonTest) }
    }
}

android {
    namespace = "fr.moovie.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.moovie.tv"
        minSdk = 23
        targetSdk = 34
        versionCode = 98
        versionName = appVersion
        buildConfigField("String", "OPENSUBTITLES_API_KEY", "\"$openSubtitlesApiKey\"")
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
        // `java.time` demande l'API 26, et minSdk vaut 23 : sans désucrage, tout
        // usage lève un NoClassDefFoundError sur Android 6 et 7. `MediaDate` en
        // vit depuis toujours sans jamais planter, parce qu'il n'était appelé
        // que par ses tests — l'afficher enfin à l'écran rend le risque réel.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

tasks.withType<Test>().configureEach {
    // Les sondes de diagnostic (empreinte TLS…) écrivent sur stdout : sans ça
    // Gradle l'avale et le test « passe » sans rien montrer.
    testLogging { showStandardStreams = true }
    // -Dmoovie.probe=1 est passé à la JVM Gradle, pas à celle des tests : on le
    // relaie explicitement, sinon la sonde se croit non demandée.
    System.getProperty("moovie.probe")?.let { systemProperty("moovie.probe", it) }
    System.getProperty("moovie.probe.download")
        ?.let { systemProperty("moovie.probe.download", it) }
    // Mêmes relais pour les sondes qui prennent un flux en paramètre.
    listOf(
        "moovie.stream", "moovie.referer", "moovie.ua", "moovie.mpv.path",
        // Maintient le relais en vie le temps qu'un vrai appareil du réseau
        // vienne y chercher le flux — voir LanRelayProbeTest.
        "moovie.probe.hold",
        // Adresse du récepteur Cast à éprouver — voir CastEndToEndProbeTest.
        "moovie.cast.host", "moovie.cast.trace",
        // Autorise la sonde de volume à *écrire* sur l'appareil, et pas
        // seulement à le lire — voir CastVolumeProbeTest.
        "moovie.cast.volume",
        // Fait envoyer une piste de sous-titres par la sonde de bout en bout.
        "moovie.vtt",
        // Dossier où les sondes déposent leur relevé JSON, lu par le tableau de
        // bord (tools/dashboard). Absent = rien n'est écrit.
        "moovie.report",
    ).forEach { key ->
        System.getProperty(key)?.let { systemProperty(key, it) }
    }
    // Les sondes OpenSubtitles interrogent la vraie API : sans la clé elles
    // s'abstiennent au lieu d'échouer.
    systemProperty("moovie.opensubtitles.key", openSubtitlesApiKey)
}

compose.resources {
    // Res accessible depuis jvmCommon (strings FR/EN/ES partagées TV + desktop)
    packageOfResClass = "fr.moovie.tv.resources"
}

// Classe principale du desktop. Extraite en constante parce que l'AppImage en
// dérive aussi son StartupWMClass : AWT nomme la WM_CLASS X11 d'après elle, en
// remplaçant les points par des tirets.
val desktopMainClass = "fr.moovie.tv.desktop.MainKt"

compose.desktop {
    application {
        mainClass = desktopMainClass
        // Version lue au runtime par l'updater desktop (bannière de mise à jour).
        jvmArgs += "-Dmoovie.version=$appVersion"
        // Pendant de BuildConfig côté Android : le desktop lit la clé en
        // propriété système, comme il lit déjà la version.
        jvmArgs += "-Dmoovie.opensubtitles.key=$openSubtitlesApiKey"

        // JDK dont jpackage tire le runtime embarqué, quand il doit différer de
        // celui qui exécute Gradle. Sous Linux la CI y met l'OpenJDK de la
        // distribution : avec le runtime Temurin, le décodage natif mourait en
        // SIGSEGV au démarrage de la lecture (constaté sur libVLC ; la cause
        // est le runtime, pas le lecteur, donc la précaution reste). Non défini
        // → runtime du JDK courant, comportement local inchangé.
        System.getenv("MOOVIE_JPACKAGE_JDK")?.let { javaHome = it }

        nativeDistributions {
            // Linux passe par l'AppImage (tâche packageAppImage ci-dessous) :
            // un .deb ne couvrait qu'Ubuntu, déclarait des dépendances système
            // héritées de la machine de build, et interdisait la mise à jour
            // in-app faute de pouvoir s'installer sans root.
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "Moo-vie"
            packageVersion = jpackageVersion
            description = "Moo-vie — streaming, extraction de sources on-device"
            // Sans vendor, jpackage écrit « Unknown » dans le champ Maintainer.
            vendor = "JibayMcs"
            // JNA — par qui passe tout le dialogue avec libmpv — a besoin de
            // sun.misc.Unsafe dans l'image jpackage.
            modules("jdk.unsupported")

            // Ressources déposées à côté de l'application, et dont jpackage
            // donne le chemin en propriété système : c'est là que la CI pose
            // libmpv pour Windows et macOS, qui n'ont pas de `LD_LIBRARY_PATH`
            // à leur offrir. Compose lit les sous-répertoires par plateforme
            // (`windows-x64`, `macos-arm64`…). Vide en local : le binding
            // retombe alors sur la bibliothèque du système.
            appResourcesRootDir.set(project.file("resources"))

            // Icônes des paquets natifs. Sans elles, jpackage retombe sur
            // l'icône Kotlin/Java par défaut. Les fichiers sont versionnés
            // (générés depuis docs/assets/sources/moo_vie_launcher_icon_master_1024x1024.png)
            // car la CI n'a pas d'outil de conversion ico/icns.
            linux { iconFile.set(project.file("icons/moovie.png")) }
            windows {
                iconFile.set(project.file("icons/moovie.ico"))
                // Sans raccourci ni groupe de menu, le MSI installait bien l'app
                // (sous Program Files / LocalAppData) mais ne créait aucune
                // entrée : ni menu Démarrer, ni bureau, ni icône visible — d'où
                // l'impression que « l'installation ne marche pas ».
                // menuGroup crée l'entrée menu Démarrer (menu = true en découle) ;
                // shortcut ajoute le raccourci bureau.
                menuGroup = "Moo-vie"
                shortcut = true
                // UUID figé : les MSI suivants remplacent l'installation en place
                // au lieu de s'ajouter côte à côte. Doit rester constant d'une
                // version à l'autre (comme upgradeUuid l'exige côté WiX).
                upgradeUuid = "3bb04069-630b-44cc-ae44-8579cc165af4"
                // Installation par utilisateur (%LOCALAPPDATA%) : pas d'élévation
                // UAC, et surtout l'updater desktop peut réécrire les fichiers
                // sans droits admin — cohérent avec la mise à jour in-app.
                perUserInstall = true
            }
            macOS { iconFile.set(project.file("icons/moovie.icns")) }
        }
    }
}


// ── Paquet Linux : AppImage ──────────────────────────────────────────────────
//
// jpackage ne sait produire que .deb/.rpm, qui déclarent des dépendances
// système héritées de la machine de build et exigent root pour s'installer. Un
// AppImage embarque ses bibliothèques, tourne sur n'importe quelle
// distribution et, étant un simple fichier chez l'utilisateur, permet à
// l'app de se mettre à jour elle-même.
//
// Attention : la glibc n'est compatible que vers l'avant. L'image doit être
// construite sur la plus ancienne base visée, sinon elle ne démarrera pas sur
// les distributions plus anciennes.

private val appImageToolUrl =
    "https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage"

/**
 * Copie libmpv dans l'AppDir.
 *
 * **Une bibliothèque, et c'est tout.** Le lecteur précédent réclamait ici une
 * centaine de fichiers : libVLC, libvlccore, et un arbre de plugins qu'il
 * fallait trier un par un pour ne pas emporter Qt, Samba ou ICU. Ce tri était
 * la source du défaut le plus coûteux du projet — le plugin `xml` retiré pour
 * gagner 29 Mo, et plus une seule bande-annonce ne se lisait en 1.18.0. mpv n'a
 * pas de répertoire de plugins : ce qu'il sait faire est dans le fichier.
 *
 * Ses dépendances (FFmpeg, libass, freetype…) ne sont pas listées non plus :
 * la fermeture ELF calculée plus bas les ramasse toutes seules, comme pour le
 * reste du paquet.
 *
 * **Deux sources possibles, et la première l'emporte.** `app/mpv-linux/`, si
 * elle existe, contient une libmpv portable et sa fermeture, déposées par
 * `tools/fetch-mpv-linux.sh` (voir ce script pour le pourquoi : la libmpv de la
 * base de build décidait de la qualité de l'image, et 22.04 en sert une qui ne
 * sait pas convertir le HDR). À défaut, on retombe sur la bibliothèque système
 * — ce qui garde un `./gradlew packageAppImage` utilisable sans réseau.
 */
fun bundleMpv(appDir: File) {
    val portable = File(projectDir, "mpv-linux")
    if (File(portable, "libmpv.so.2").isFile) {
        bundleMpvPortable(appDir, portable)
        return
    }
    logger.lifecycle(
        "app/mpv-linux/ absent — repli sur la libmpv du système. " +
            "Lancer tools/fetch-mpv-linux.sh pour embarquer la version portable.",
    )
    val libDirs = listOf(
        File("/usr/lib/x86_64-linux-gnu"),
        File("/usr/lib64"),
        File("/usr/lib"),
    )
    // Deux noms possibles : mpv ≥ 0.35 est en `so.2`, les LTS servent `so.1`.
    // On prend ce que la machine de build a, et on l'expose sous le nom que le
    // binding cherche en premier.
    val noms = listOf("libmpv.so.2", "libmpv.so.1")
    val lib = noms.firstNotNullOfOrNull { nom ->
        libDirs.map { File(it, nom) }.firstOrNull { it.exists() }
    }
    requireNotNull(lib) {
        "libmpv introuvable — installer libmpv2 (ou libmpv1) sur la machine de build"
    }
    val bundled = File(appDir, "usr/lib/bundled").apply { mkdirs() }
    // canonicalFile : ce sont des liens vers libmpv.so.2.x.y, et un lien vers
    // /usr/lib dans l'image ne mènerait nulle part.
    val cible = File(bundled, lib.name)
    lib.canonicalFile.copyTo(cible, overwrite = true)
    // JNA résout « mpv » en « libmpv.so », sans suffixe de version : sans ce
    // lien il ne trouverait rien dans notre répertoire et retomberait sur la
    // bibliothèque de l'hôte — celle dont on ne sait rien.
    val sansVersion = File(bundled, "libmpv.so")
    sansVersion.delete()
    Files.createSymbolicLink(sansVersion.toPath(), File(lib.name).toPath())
    logger.lifecycle("libmpv embarquée : ${lib.name} (${cible.length() / 1024 / 1024} Mo)")
}

/**
 * Copie la libmpv portable **et toute sa fermeture** dans l'AppDir.
 *
 * Le répertoire a déjà été trié par `tools/fetch-mpv-linux.sh` : ce qui s'y
 * trouve s'y trouve parce qu'on l'a mesuré nécessaire, et ce qui n'y est pas
 * appartient à l'hôte. On recopie donc sans refiltrer — la fermeture ELF plus
 * bas verra ces fichiers déjà présents et n'ira pas chercher les homonymes du
 * système, qui seraient d'une autre génération.
 */
fun bundleMpvPortable(appDir: File, source: File) {
    val bundled = File(appDir, "usr/lib/bundled").apply { mkdirs() }
    val libs = source.listFiles()?.filter { it.isFile && it.name.contains(".so") }.orEmpty()
    libs.forEach { it.copyTo(File(bundled, it.name), overwrite = true) }
    // JNA résout « mpv » en « libmpv.so », sans suffixe de version : sans ce
    // lien il ne trouverait rien dans notre répertoire et retomberait sur la
    // bibliothèque de l'hôte — celle dont on ne sait rien.
    val sansVersion = File(bundled, "libmpv.so")
    sansVersion.delete()
    Files.createSymbolicLink(sansVersion.toPath(), File("libmpv.so.2").toPath())
    val version = File(source, "VERSION").takeIf { it.isFile }?.readText()?.trim() ?: "?"
    val poids = libs.sumOf { it.length() } / 1024 / 1024
    logger.lifecycle("libmpv portable embarquée : mpv $version, ${libs.size} bibliothèques ($poids Mo)")
}

val packageAppImage by tasks.registering {
    group = "compose desktop"
    description = "Emballe l'app-image jpackage dans un .AppImage Linux."
    dependsOn("createDistributable")

    val appDirRoot = layout.buildDirectory.dir("appimage").get().asFile
    val distributable = layout.buildDirectory.dir("compose/binaries/main/app/Moo-vie")
    val output = layout.buildDirectory
        .file("compose/binaries/main/appimage/Moo-vie-$appVersion-x86_64.AppImage").get().asFile
    // Sans entrée déclarée, Gradle considérait la tâche à jour dès que le
    // fichier de sortie existait et la sautait : on pouvait alors emballer —
    // ou pire, publier — une image construite avant les dernières
    // modifications du code.
    inputs.dir(distributable)
    inputs.property("version", appVersion)
    outputs.file(output)

    doLast {
        val appImageDir = distributable.get().asFile
        require(appImageDir.isDirectory) { "app-image introuvable : $appImageDir" }

        // Outil téléchargé une fois puis conservé (la CI le remet en cache).
        val tool = File(appDirRoot.parentFile, "appimagetool")
        if (!tool.exists()) {
            tool.parentFile.mkdirs()
            uri(appImageToolUrl).toURL().openStream().use { input ->
                tool.outputStream().use { input.copyTo(it) }
            }
            tool.setExecutable(true)
        }

        // AppDir : l'app sous usr/, plus les trois fichiers exigés à la racine
        // (AppRun, .desktop, icône).
        val appDir = File(appDirRoot, "AppDir")
        appDir.deleteRecursively()
        File(appDir, "usr").mkdirs()
        copy {
            from(appImageDir)
            into(File(appDir, "usr"))
        }
        copy {
            from(project.file("icons/moovie.png"))
            into(appDir)
            rename { "moovie.png" }
        }
        File(appDir, "moovie.desktop").writeText(
            """
            [Desktop Entry]
            Type=Application
            Name=Moo-vie
            Comment=Streaming, extraction de sources on-device
            Exec=Moo-vie
            Icon=moovie
            Categories=AudioVideo;Video;Player;
            Terminal=false
            StartupWMClass=${desktopMainClass.replace('.', '-')}
            """.trimIndent() + "\n",
        )
        // $APPDIR n'est pas fiable selon le mode de montage : on résout le
        // chemin réel de AppRun, qui vit à la racine de l'AppDir.
        //
        // `LD_LIBRARY_PATH` suffit désormais : JNA y trouve la libmpv qu'on
        // livre, et mpv n'a pas de répertoire de plugins à lui désigner. Les
        // deux variables que cette ligne remplace (`VLC_PLUGIN_PATH`,
        // `MOOVIE_VLC_HOME`) n'existaient que pour empêcher libVLC d'aller
        // mélanger nos plugins avec la bibliothèque de l'hôte.
        File(appDir, "AppRun").apply {
            writeText(
                """
                #!/bin/sh
                HERE="${'$'}(dirname "${'$'}(readlink -f "${'$'}0")")"
                export LD_LIBRARY_PATH="${'$'}HERE/usr/lib/bundled${'$'}{LD_LIBRARY_PATH:+:${'$'}LD_LIBRARY_PATH}"
                exec "${'$'}HERE/usr/bin/Moo-vie" "${'$'}@"
                """.trimIndent() + "\n",
            )
            setExecutable(true)
        }

        bundleMpv(appDir)

        // Bibliothèques embarquées. On copie la fermeture ELF de ce dont le
        // bundle a besoin, moins deux familles qu'on ne bundle jamais :
        //  - graphique et son (libGL, libX*, libasound) : couplés au pilote GPU
        //    et au serveur graphique de l'hôte, les embarquer casse plus qu'il
        //    n'aide ; toute session de bureau les fournit ;
        //  - glibc, libstdc++ et libgcc_s : deux copies de libstdc++ dans un
        //    même process, c'est exactement ce qui faisait crasher le paquet
        //    Temurin.
        // Ce sont les autres (jpeg, gif, png, harfbuzz, fontconfig, freetype,
        // lcms2) qui ont fait échouer le .deb selon les distributions : les
        // embarquer supprime cette classe de problème.
        val excluded = Regex(
            "^(ld-linux.*|libc|libm|libdl|libpthread|librt|libresolv|libgcc_s|libstdc\\+\\+" +
                "|libGL.*|libGLX.*|libGLdispatch|libEGL.*|libX11.*|libXext|libXi|libXrender" +
                "|libXtst|libXau|libXdmcp|libxcb.*|libasound|libpulse.*|libdrm.*)\\..*",
        )
        val bundled = File(appDir, "usr/lib/bundled").apply { mkdirs() }
        // Ce que `bundleMpv` a déjà déposé est réputé bon et n'est pas
        // reconsidéré. Sans cette amorce, `ldd` d'une libmpv portable résoudrait
        // ses dépendances contre le *système* de la machine de build, et une
        // freetype d'Ubuntu 22.04 viendrait écraser celle qui accompagne la
        // libmpv qu'on a choisie — deux générations mélangées dans un process.
        val seen = bundled.list()?.toMutableSet() ?: mutableSetOf()
        fun closure(file: File) {
            val out = providers.exec {
                commandLine("ldd", file.absolutePath)
                isIgnoreExitValue = true
            }.standardOutput.asText.get()
            out.lineSequence().forEach { line ->
                val path = Regex("=> (/[^ ]+)").find(line)?.groupValues?.get(1) ?: return@forEach
                val lib = File(path)
                if (!lib.isFile || !seen.add(lib.name)) return@forEach
                if (excluded.containsMatchIn(lib.name)) return@forEach
                lib.copyTo(File(bundled, lib.name), overwrite = true)
                closure(lib)
            }
        }
        File(appDir, "usr/lib").walkTopDown()
            .filter { it.isFile && (it.name.contains(".so") || it.name == "Moo-vie") }
            .forEach { closure(it) }
        closure(File(appDir, "usr/bin/Moo-vie"))
        logger.lifecycle("Bibliothèques embarquées : ${bundled.list()?.size ?: 0}")

        output.parentFile.mkdirs()
        providers.exec {
            commandLine(tool.absolutePath, appDir.absolutePath, output.absolutePath)
            environment("ARCH", "x86_64")
            environment("VERSION", appVersion)
            // appimagetool est lui-même une AppImage : sans FUSE (cas des
            // conteneurs de CI) son runtime ne peut pas se monter et sort en
            // 127. Ce drapeau le fait s'extraire puis s'exécuter, ce qui
            // fonctionne aussi bien sur un poste de travail.
            environment("APPIMAGE_EXTRACT_AND_RUN", "1")
        }.result.get().assertNormalExitValue()
        logger.lifecycle("AppImage écrite : ${output.absolutePath}")
    }
}

// Désucrage des bibliothèques du JDK : configuration Android, elle ne peut pas
// vivre dans un source set KMP. Voir `isCoreLibraryDesugaringEnabled`.
dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
}
