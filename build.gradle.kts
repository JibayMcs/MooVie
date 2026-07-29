// Plugins déclarés ici (apply false) puis appliqués dans les modules.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.compose") version "1.7.1" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
}
