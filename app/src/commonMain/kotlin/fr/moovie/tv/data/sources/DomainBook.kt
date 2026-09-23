package fr.moovie.tv.data.sources

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.getBody
import fr.moovie.tv.data.store.preferencesStore
import fr.moovie.tv.shared.maintenantMs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.concurrent.Volatile

/**
 * L'annuaire des domaines, tenu **hors de l'application**.
 *
 * ## Le problème qu'il résout
 *
 * Les catalogues déménagent. SwiftFlow trois fois en un an, french-stream le
 * mois dernier, frembed avant lui. À chaque fois la correction tenait en une
 * ligne — et coûtait une release, quatre plateformes à construire, et une mise
 * à jour à installer chez chaque utilisateur avant que la source reparte. Entre
 * les deux, elle rend zéro titre sans rien dire.
 *
 * L'annuaire coupe ce cycle : un fichier publié à part, relu au lancement.
 * Corriger un domaine devient une modification de ce fichier, et les appareils
 * sont réparés au démarrage suivant, sans rien installer.
 *
 * ## Ce qu'il peut, et ce qu'il ne peut pas
 *
 * **Il propose des données, jamais du comportement.** Il ne peut qu'offrir des
 * domaines candidats pour des sources **déjà compilées** : il n'en crée aucune,
 * ne change pas la cascade, n'apporte aucune URL de flux. Le pire qu'un fichier
 * corrompu puisse faire est de proposer un domaine qui échouera — et pour
 * SwiftFlow, dont la sonde exige du JSON, qui sera écarté tout seul.
 *
 * Il ne répare que les **domaines**. Un hébergeur qui change son format de
 * page, un backend qui tombe, un extracteur à corriger : rien de tout cela ne
 * passe par ici, et il ne faut pas lui demander de le faire.
 *
 * ## Trois couches, et aucune ne retire à la précédente
 *
 * 1. le dernier domaine qui a répondu, gardé par la source elle-même ;
 * 2. l'annuaire, c'est-à-dire ce qu'on sait aujourd'hui ;
 * 3. les domaines compilés, qui restent le **plancher** — une application hors
 *    ligne, ou qui ne joindrait pas GitHub, se comporte exactement comme avant.
 *
 * ## Pourquoi un asset de release
 *
 * Même chaîne de confiance que les mises à jour, déjà éprouvée, et rien à
 * héberger. Surtout : un asset n'apparaît pas dans l'arborescence du dépôt et
 * n'est pas indexé, là où un `domains.json` bien rangé à la racine servirait de
 * fiche récapitulative à qui voudrait signaler le projet. Le tag est une
 * **préversion** et porte un numéro nul : `releases/latest` l'ignore par
 * définition, et le canal de test ne peut pas le prendre pour une version — un
 * `0.0.0` n'est jamais postérieur à quoi que ce soit.
 */
object DomainBook {

    private const val URL =
        "https://github.com/JibayMcs/MooVie/releases/download/v0.0.0-annuaire/domains.json"

    /** Relu à ce rythme : un domaine ne change pas deux fois dans la journée. */
    private const val TTL_MS = 6L * 60 * 60 * 1000

    /**
     * Au-delà, on se passe de l'annuaire pour ce lancement-ci.
     *
     * La première résolution de sources d'une session l'attend : la faire
     * dépendre de la disponibilité de GitHub serait échanger une panne contre
     * une autre. Trois secondes une fois toutes les six heures, et la copie
     * persistée prend le relais.
     */
    private const val DELAI_MS = 3_000L

    private val json = Json { ignoreUnknownKeys = true }
    /**
     * Relu à chaque accès, et **jamais retenu dans un `by lazy`**.
     *
     * [preferencesStore] tient déjà une instance unique par fichier, protégée
     * par un verrou : le redemander ne coûte qu'une recherche dans une table.
     * Le figer, en revanche, garderait à vie le premier magasin rencontré —
     * celui d'un test, ou celui d'un profil qu'on vient de quitter — et
     * l'annuaire continuerait de se lire là où plus personne n'écrit.
     */
    private val store get() = preferencesStore("moovie_domains")

    private val CLE_JSON = stringPreferencesKey("book")
    private val CLE_AT = longPreferencesKey("fetched_at")

    @Volatile private var memoire: Map<String, List<String>> = emptyMap()
    @Volatile private var luAt = 0L

    /**
     * Les domaines connus pour cette source, **les plus frais d'abord**, suivis
     * des domaines compilés qui n'y figureraient pas.
     *
     * [compiles] n'est jamais retiré : l'annuaire complète le code, il ne le
     * contredit pas. Une entrée absente ou vide rend donc exactement la liste
     * d'avant.
     */
    suspend fun bases(http: HttpGateway, provider: String, compiles: List<String>): List<String> {
        rafraichis(http)
        return LinkedHashSet<String>().apply {
            addAll(memoire[provider].orEmpty())
            addAll(compiles)
        }.toList()
    }

    /**
     * Le domaine à employer pour une source qui n'en essaie qu'un.
     *
     * Les catalogues à domaine unique — fstream, cinestream, anime-sama — n'ont
     * pas de sonde qui distinguerait un bon domaine d'un mauvais : ils prennent
     * le premier de l'annuaire, ou [defaut]. C'est assumé, et c'est le même
     * niveau de confiance qu'une constante compilée — à ceci près qu'on peut la
     * corriger sans release.
     */
    suspend fun base(http: HttpGateway, provider: String, defaut: String): String =
        bases(http, provider, listOf(defaut)).first()

    private suspend fun rafraichis(http: HttpGateway) {
        val maintenant = maintenantMs()
        if (maintenant - luAt < TTL_MS && memoire.isNotEmpty()) return

        // La copie persistée d'abord : elle est immédiate, et c'est elle qui
        // sert quand GitHub ne répond pas — sans quoi un appareil hors ligne
        // perdrait à chaque démarrage ce qu'on lui a appris hier.
        if (memoire.isEmpty()) {
            val prefs = runCatching { store.data.first() }.getOrNull()
            prefs?.get(CLE_JSON)?.let { brut -> decode(brut)?.let { memoire = it } }
            luAt = prefs?.get(CLE_AT) ?: 0L
            if (maintenant - luAt < TTL_MS && memoire.isNotEmpty()) return
        }

        val corps = withTimeoutOrNull(DELAI_MS) {
            http.getBody(URL, mapOf("Accept" to "application/json", "User-Agent" to Ua.BROWSER))
        }
        val lu = corps?.let { decode(it) }
        if (lu == null) {
            // Échec : on garde ce qu'on a et on ne retente pas avant le TTL. Une
            // panne de GitHub ne doit pas coûter une requête par fiche ouverte.
            luAt = maintenant
            return
        }

        memoire = lu
        luAt = maintenant
        runCatching {
            store.edit {
                it[CLE_JSON] = corps
                it[CLE_AT] = maintenant
            }
        }
    }

    /**
     * `{"version":1,"providers":{"swiftflow":["https://…"]}}` → table.
     *
     * Tout ce qui n'est pas une origine `https://` est écarté silencieusement :
     * une entrée mal saisie ne doit pas pouvoir diriger l'application ailleurs
     * qu'en HTTPS, et un `http://` accepté ici ferait voyager une requête en
     * clair sans que personne ne s'en aperçoive.
     */
    internal fun decode(brut: String): Map<String, List<String>>? = runCatching {
        val racine = json.parseToJsonElement(brut).jsonObject
        val providers = racine["providers"]?.jsonObject ?: return null
        providers.entries.associate { (nom, valeur) ->
            nom to valeur.jsonArray.mapNotNull { element ->
                element.jsonPrimitive.content
                    .trim()
                    .trimEnd('/')
                    .takeIf { it.startsWith("https://") && it.length > "https://".length }
            }
        }.filterValues { it.isNotEmpty() }
    }.getOrNull()

    /** Pour les tests : repart d'un annuaire vierge. */
    internal fun oublie() {
        memoire = emptyMap()
        luAt = 0L
    }
}
