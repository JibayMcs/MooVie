package fr.moovie.tv.data.sources

import java.io.File

/**
 * Sortie **lisible par une machine** des sondes de santé.
 *
 * ## Pourquoi ça existe
 *
 * Les sondes impriment un tableau fait pour l'œil, et `tools/check-sources.sh`
 * le récupère en le grepant. Ça marche tant qu'un humain lit le résultat le jour
 * même ; ça ne permet pas de répondre à la seule question qui compte vraiment —
 * **quand est-ce tombé ?** — ni de la poser à autre chose qu'un terminal.
 *
 * Un relevé daté et structuré permet de comparer deux points dans le temps, donc
 * de distinguer « cet hébergeur n'a jamais marché » de « cet hébergeur est tombé
 * cette nuit ». C'est cette bascule, et elle seule, qui signale du travail à
 * faire.
 *
 * ## N'écrit que si on le demande
 *
 * Sans `-Dmoovie.report=<dossier>`, rien n'est écrit et les sondes se comportent
 * exactement comme avant. Le rapport est un supplément, jamais un prérequis :
 * une sonde qui échouerait à écrire son fichier ne doit pas faire échouer la
 * mesure qu'elle vient de prendre.
 */
object ProbeReport {

    private val dossier: File? =
        System.getProperty("moovie.report")?.takeIf { it.isNotBlank() }?.let(::File)

    /** Vrai si un rapport est attendu — évite de préparer des données pour rien. */
    val demande: Boolean get() = dossier != null

    /**
     * Écrit `<dossier>/<nom>.json`.
     *
     * Enveloppé : un disque plein ou un chemin invalide ne doit pas transformer
     * un relevé réussi en échec de test. Le relevé est déjà à l'écran de toute
     * façon.
     */
    fun ecris(nom: String, json: String) {
        val cible = dossier ?: return
        runCatching {
            cible.mkdirs()
            File(cible, "$nom.json").writeText(json)
            println("[rapport] écrit : ${File(cible, "$nom.json").absolutePath}")
        }.onFailure { println("[rapport] ⚠️ non écrit : ${it.message}") }
    }

    /** Échappe ce qui doit l'être dans une chaîne JSON. */
    fun texte(valeur: String): String = buildString {
        append('"')
        valeur.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }
}
