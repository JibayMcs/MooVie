package fr.moovie.tv.data.discovery

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.moovie.tv.data.store.STORE_MOOD
import fr.moovie.tv.data.store.preferencesStore
import fr.moovie.tv.data.store.profileStoreName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Le questionnaire : trois questions, pas six.
 *
 * ### Pourquoi si court
 *
 * Le modèle du genre (la page « Suggestion » de Movix) en pose six, dont une à
 * dix-huit choix. À la télécommande c'est un formulaire, et on abandonne avant
 * la fin. Trois questions tiennent en trois appuis, et c'est déjà assez pour
 * orienter une page.
 *
 * ### Ce que le questionnaire n'est pas
 *
 * Il ne sert **pas** à découvrir vos goûts : ils sont déjà dans l'historique,
 * et les redemander serait insultant pour la donnée qu'on possède. Il sert à
 * connaître ce qu'aucune donnée ne peut deviner — l'humeur de ce soir, avec qui
 * vous êtes, et le temps dont vous disposez.
 *
 * Il ne prend donc la main entièrement que sur un profil neuf, où il résout le
 * démarrage à froid. Partout ailleurs il **réoriente** des groupes bâtis sur
 * l'historique.
 */
enum class MoodQuestion { HUMEUR, AVEC, TEMPS }

/**
 * Une réponse possible.
 *
 * [genres] sont des identifiants TMDB, joints par un **OU** au moment de la
 * requête. [detail] est la clé de chaîne qui dit à l'écran ce que la réponse
 * fait réellement (« thriller, policier ») : un questionnaire dont on voit les
 * effets est un questionnaire dont on comprend le résultat, et qu'on peut
 * corriger quand il déçoit.
 */
data class MoodOption(
    val id: String,
    val question: MoodQuestion,
    val genres: List<Int> = emptyList(),
    val maxRuntime: Int? = null,
    val minRuntime: Int? = null,
    val wantsTv: Boolean = false,
)

/**
 * Genres TMDB, nommés pour que les recettes se lisent.
 *
 * Les identifiants de genre sont stables chez TMDB et communs aux films ; les
 * séries en partagent la plupart, à l'exception notable d'Action (28), qui y
 * devient « Action & Adventure » (10759). Les recettes ci-dessous restent donc
 * sur des genres valables des deux côtés, sauf quand la réponse demande
 * explicitement une série.
 */
private object G {
    const val ACTION = 28
    const val AVENTURE = 12
    const val ANIMATION = 16
    const val COMEDIE = 35
    const val POLICIER = 80
    const val DRAME = 18
    const val FAMILLE = 10751
    const val FANTASTIQUE = 14
    const val HORREUR = 27
    const val MYSTERE = 9648
    const val ROMANCE = 10749
    const val SF = 878
    const val THRILLER = 53
}

/** Le questionnaire, dans l'ordre où il est posé. */
val MOOD_OPTIONS: List<MoodOption> = listOf(
    // 1 — l'humeur. C'est la seule question que tout le monde attend.
    MoodOption("detendue", MoodQuestion.HUMEUR, listOf(G.COMEDIE, G.FAMILLE, G.AVENTURE)),
    MoodOption("tension", MoodQuestion.HUMEUR, listOf(G.THRILLER, G.POLICIER, G.MYSTERE)),
    MoodOption("rire", MoodQuestion.HUMEUR, listOf(G.COMEDIE)),
    MoodOption("peur", MoodQuestion.HUMEUR, listOf(G.HORREUR, G.MYSTERE)),

    // 2 — avec qui. La meilleure question du lot : elle change plus de choses
    // que le genre, et aucun catalogue ne la pose.
    MoodOption("seul", MoodQuestion.AVEC, listOf(G.DRAME, G.MYSTERE, G.SF)),
    MoodOption("deux", MoodQuestion.AVEC, listOf(G.ROMANCE, G.DRAME)),
    MoodOption("amis", MoodQuestion.AVEC, listOf(G.COMEDIE, G.ACTION, G.HORREUR)),
    MoodOption("famille", MoodQuestion.AVEC, listOf(G.FAMILLE, G.ANIMATION, G.FANTASTIQUE)),

    // 3 — le temps. Un film de 2 h 40 un mardi soir, c'est un film qu'on ne
    // finira pas ; la reprise le sait déjà, autant le demander avant.
    MoodOption("court", MoodQuestion.TEMPS, maxRuntime = 100),
    MoodOption("soiree", MoodQuestion.TEMPS, minRuntime = 120),
    MoodOption("peuimporte", MoodQuestion.TEMPS),
    MoodOption("serie", MoodQuestion.TEMPS, wantsTv = true),
)

fun moodOptionsFor(question: MoodQuestion): List<MoodOption> =
    MOOD_OPTIONS.filter { it.question == question }

fun moodOption(id: String): MoodOption? = MOOD_OPTIONS.firstOrNull { it.id == id }

/**
 * Les réponses retenues, telles que la page les applique.
 *
 * Les genres des deux premières questions sont **réunis, pas croisés** : « sous
 * tension » et « à deux » veut dire thriller ou drame, pas thriller ET drame.
 * L'intersection est le piège dans lequel tombe la page de Movix — trois genres
 * exigés ensemble ne rendent presque rien, et il faut enchaîner des replis
 * jusqu'à servir autre chose que ce qui était demandé.
 */
data class MoodAnswers(val ids: List<String> = emptyList()) {

    val options: List<MoodOption> get() = ids.mapNotNull(::moodOption)

    val isComplete: Boolean
        get() = MoodQuestion.entries.all { q -> options.any { it.question == q } }

    val genres: List<Int> get() = options.flatMap { it.genres }.distinct()
    val maxRuntime: Int? get() = options.firstNotNullOfOrNull { it.maxRuntime }
    val minRuntime: Int? get() = options.firstNotNullOfOrNull { it.minRuntime }
    val wantsTv: Boolean get() = options.any { it.wantsTv }

    fun answerFor(question: MoodQuestion): MoodOption? =
        options.firstOrNull { it.question == question }

    /** Remplace la réponse d'une question sans toucher aux autres. */
    fun with(option: MoodOption): MoodAnswers = MoodAnswers(
        (ids.mapNotNull(::moodOption).filterNot { it.question == option.question } + option)
            .sortedBy { it.question.ordinal }
            .map { it.id },
    )

    companion object {
        val EMPTY = MoodAnswers()
    }
}

/**
 * Les réponses, conservées.
 *
 * Par profil : deux personnes devant le même téléviseur n'ont pas la même
 * humeur, et c'est tout l'intérêt de la question. Le magasin est donc déclaré
 * dans `PROFILE_SCOPED_STORES`, sans quoi il survivrait à la suppression du
 * profil.
 */
class MoodRepository {

    private val store = preferencesStore(profileStoreName(STORE_MOOD))
    private val key = stringPreferencesKey("answers")

    val answers: Flow<MoodAnswers> = store.data.map { prefs ->
        MoodAnswers(prefs[key]?.split(",")?.filter { it.isNotBlank() }.orEmpty())
    }

    suspend fun save(answers: MoodAnswers) {
        store.edit { it[key] = answers.ids.joinToString(",") }
    }

    suspend fun clear() {
        store.edit { it.remove(key) }
    }
}
