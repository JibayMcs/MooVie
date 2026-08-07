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
val appVersion = "1.16.0-rc.3"

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
                // Encodage QR seulement (Java pur, sans dépendance Android) :
                // l'appairage du téléphone en affiche un. Le codage QR est du
                // Reed-Solomon et du masquage — pas quelque chose qu'on écrit
                // à la main pour économiser un jar.
                implementation("com.google.zxing:core:3.5.3")
                // Images multiplateforme (Coil 3), fetcher réseau OkHttp
                implementation("io.coil-kt.coil3:coil-compose:3.0.4")
                implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
                // Réglages persistants (artefact KMP ; le chemin du fichier est
                // fourni par expect/actual moovieDataStoreFile)
                implementation("androidx.datastore:datastore-preferences-core:1.1.1")
                // ViewModels multiplateformes (androidx.lifecycle réel côté Android,
                // port JetBrains côté desktop)
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.8.4")
                // LocalViewModelStoreOwner, que ProfileHost redéfinit pour donner
                // à chaque profil son propre magasin de ViewModels — sans quoi
                // `viewModel()` rend l'instance construite sous le profil
                // précédent. Le port JetBrains, pour rester en jvmCommon.
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
            }
        }

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
        versionCode = 36
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
        // distribution : avec le runtime Temurin, libVLC meurt en SIGSEGV dans
        // son démuxeur adaptive au démarrage de la lecture. Non défini → runtime
        // du JDK courant, comportement local inchangé.
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
            // JNA (vlcj) a besoin de sun.misc.Unsafe dans l'image jpackage.
            modules("jdk.unsupported")

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
 * Répertoires de plugins libVLC qu'on n'embarque pas.
 *
 * `gui`, `lua`, `control`, `services_discovery`, `notify`, `visualization` :
 * l'interface de VLC et ses à-côtés, dont on n'utilise rien — `gui` à lui seul
 * pèse 4 Mo et tire toute la chaîne Qt. `vdpau` et `vaapi` : décodage matériel,
 * couplé au pilote GPU de l'hôte, exactement ce qu'il ne faut pas figer. Le
 * reste (`mux`, `stream_out`, `access_output`, `keystore`, `meta_engine`,
 * `video_splitter`, `stream_extractor`) ne sert qu'à encoder, diffuser ou
 * gérer des archives.
 */
val vlcDroppedDirs = setOf(
    "gui", "lua", "control", "services_discovery", "notify", "visualization",
    "vdpau", "vaapi", "mux", "stream_out", "access_output", "keystore",
    "meta_engine", "video_splitter", "stream_extractor",
)

/**
 * Plugins écartés un par un, pour ce qu'ils traînent derrière eux.
 *
 * `smb` amène toute la pile Samba (5,6 Mo de libndr), `libbluray`/`dvdread`/
 * `dvdnav`/`vcd` les disques optiques, `x264`/`x265`/`twolame` des encodeurs,
 * `svgdec` librsvg, `xml` ICU. `gl`/`gles2` tirent libplacebo (7 Mo) pour des
 * sorties vidéo qu'on n'utilise pas : la vidéo passe par les callbacks vlcj,
 * donc seul `vmem` compte.
 */
val vlcDroppedPlugins = setOf(
    "smb", "dsm", "nfs", "sftp", "upnp", "libbluray", "dvdread", "dvdnav",
    "vcd", "v4l2", "dtv", "screen", "avio", "x264", "x265", "svgdec",
    "twolame", "xml", "packetizer_avparser", "gl", "gles2", "chromaprint",
    "jack", "oss",
    // Sorties vidéo liées au serveur graphique et au pilote GPU (libEGL,
    // libxcb-xv), qu'on n'embarque jamais : elles ne se chargeraient pas. La
    // vidéo passe de toute façon par les callbacks vlcj, donc par vmem.
    "egl_x11", "egl_wl", "xcb_xv",
    // Décodeur d'images SDL, inutile ici, et seul plugin à lier libpulse-simple.
    "sdl_image",
    // Capture d'écran et gestion de fenêtre X : sans objet pour nous, et
    // dépendantes d'extensions xcb qu'on n'embarque pas.
    "xcb_screen", "xcb_window",
)

/**
 * Copie libVLC et ses plugins dans l'AppDir.
 *
 * Sans ça, l'application exigeait un `apt install vlc` sur la machine cible, et
 * tournait avec la libVLC de l'hôte — dont la version varie d'une distribution
 * à l'autre. Deux crashes déjà rencontrés venaient de cette zone ; figer la
 * version supprime la classe entière de problèmes.
 */
fun bundleVlc(appDir: File) {
    val libDirs = listOf(
        File("/usr/lib/x86_64-linux-gnu"),
        File("/usr/lib64"),
        File("/usr/lib"),
    )
    val pluginsSrc = libDirs.map { File(it, "vlc/plugins") }.firstOrNull { it.isDirectory }
    requireNotNull(pluginsSrc) {
        "plugins libVLC introuvables — installer vlc-plugin-base sur la machine de build"
    }
    val bundled = File(appDir, "usr/lib/bundled").apply { mkdirs() }
    listOf("libvlc.so.5", "libvlccore.so.9").forEach { name ->
        val lib = libDirs.map { File(it, name) }.firstOrNull { it.exists() }
        requireNotNull(lib) { "$name introuvable — installer libvlc5 sur la machine de build" }
        // canonicalFile : ce sont des liens symboliques vers libvlc.so.5.6.0,
        // et un lien vers /usr/lib dans l'image ne mènerait nulle part.
        lib.canonicalFile.copyTo(File(bundled, name), overwrite = true)
        // JNA résout « vlc » en « libvlc.so », sans suffixe de version. Sans ce
        // lien, il ne trouvait rien dans notre répertoire et retombait sur la
        // libvlc du système, chargée alors avec nos plugins et notre
        // libvlccore : le mélange de versions revenait par la fenêtre.
        val unversioned = File(bundled, name.substringBefore(".so") + ".so")
        unversioned.delete()
        Files.createSymbolicLink(unversioned.toPath(), File(name).toPath())
    }

    val pluginsDst = File(appDir, "usr/lib/vlc/plugins")
    var kept = 0
    pluginsSrc.listFiles()?.filter { it.isDirectory && it.name !in vlcDroppedDirs }?.forEach { dir ->
        dir.listFiles()?.filter { it.isFile && it.name.endsWith(".so") }?.forEach { plugin ->
            val short = plugin.name.removePrefix("lib").removeSuffix("_plugin.so")
            if (short in vlcDroppedPlugins) return@forEach
            plugin.copyTo(File(pluginsDst, "${dir.name}/${plugin.name}"), overwrite = true)
            kept++
        }
    }
    // plugins.dat référence les chemins absolus du système de build : laissé en
    // place, libVLC le lit et cherche les plugins là où ils ne sont plus. Son
    // absence force un scan au démarrage, qui est le comportement correct.
    File(pluginsDst, "plugins.dat").delete()
    logger.lifecycle("Plugins libVLC embarqués : $kept")
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
        // VLC_PLUGIN_PATH pointe les plugins embarqués : sans lui, libVLC va
        // chercher ceux du système, dont la version peut ne pas correspondre à
        // la libvlc qu'on livre. MOOVIE_VLC_HOME dit à vlcj où trouver notre
        // libvlc plutôt que celle de l'hôte.
        File(appDir, "AppRun").apply {
            writeText(
                """
                #!/bin/sh
                HERE="${'$'}(dirname "${'$'}(readlink -f "${'$'}0")")"
                export LD_LIBRARY_PATH="${'$'}HERE/usr/lib/bundled${'$'}{LD_LIBRARY_PATH:+:${'$'}LD_LIBRARY_PATH}"
                export VLC_PLUGIN_PATH="${'$'}HERE/usr/lib/vlc/plugins"
                export MOOVIE_VLC_HOME="${'$'}HERE/usr/lib/bundled"
                exec "${'$'}HERE/usr/bin/Moo-vie" "${'$'}@"
                """.trimIndent() + "\n",
            )
            setExecutable(true)
        }

        bundleVlc(appDir)

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
        val seen = mutableSetOf<String>()
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
