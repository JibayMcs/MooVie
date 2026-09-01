package fr.moovie.tv.data.sources

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import fr.moovie.tv.core.sources.usecase.HosterTrust
import fr.moovie.tv.data.store.preferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Ce que cet appareil a appris des hébergeurs, à force de les essayer.
 *
 * ### Le défaut mesuré
 *
 * Sur douze titres, `netu` est proposé **18 fois** et n'est **jamais** jouable.
 * Idem, plus modestement, pour filmoon, serix, savefiles, vidara, vidsonic,
 * waaw. Ils ne bloquent rien — la cascade passe au suivant — mais chacun coûte
 * un aller-retour réseau avant d'échouer, et ils passent **devant** des liens
 * qui, eux, jouent : n'étant jamais mesurés, ils siègent au pivot des 720.
 *
 * ### Pourquoi apprendre plutôt que lister
 *
 * Une liste d'hébergeurs morts écrite à la main demande un entretien
 * permanent, vaut pour tout le monde alors qu'un hébergeur peut être bloqué
 * chez un fournisseur d'accès et pas chez un autre, et ne pardonne pas : celui
 * qui revient reste écarté jusqu'à ce que quelqu'un s'en aperçoive.
 *
 * Cette mémoire-ci se remplit seule, décrit **cette** connexion, et se corrige
 * dans les deux sens — une seule réussite ramène un hébergeur dans le rang.
 *
 * ### Global, pas par profil
 *
 * Un hébergeur joignable ou non est une propriété de l'appareil et de sa
 * connexion, pas de la personne qui regarde. Ce magasin ne va donc **pas** dans
 * `PROFILE_SCOPED_STORES` : le mesurer deux fois pour deux profils ferait payer
 * deux fois le même apprentissage.
 */
class HosterTrustRepository {

    private val store = preferencesStore(STORE)

    /** Verdict par hébergeur, prêt pour le classement des liens. */
    val trust: Flow<Map<String, HosterTrust>> = store.data.map { prefs ->
        val entries = prefs.asMap()
        val hosters = entries.keys
            .mapNotNull { key ->
                when {
                    key.name.startsWith(OK) -> key.name.removePrefix(OK)
                    key.name.startsWith(KO) -> key.name.removePrefix(KO)
                    else -> null
                }
            }
            .toSet()
        hosters.associateWith { hoster ->
            val ok = entries[intPreferencesKey(OK + hoster)] as? Int ?: 0
            val ko = entries[intPreferencesKey(KO + hoster)] as? Int ?: 0
            verdict(ok, ko)
        }
    }

    /** Cet hébergeur vient de servir un flux jouable. */
    suspend fun recordSuccess(hoster: String) {
        if (hoster.isBlank()) return
        store.edit { prefs ->
            val key = intPreferencesKey(OK + hoster)
            prefs[key] = ((prefs[key] ?: 0) + 1).coerceAtMost(PLAFOND)
            // **Les échecs sont effacés, pas décrémentés.** Un hébergeur qui
            // rejoue après une panne doit repartir propre : sinon ses vingt
            // échecs de la semaine passée le maintiendraient au fond pendant
            // vingt réussites, et la mémoire mettrait plus de temps à pardonner
            // qu'à condamner.
            prefs.remove(intPreferencesKey(KO + hoster))
        }
    }

    /** Cet hébergeur n'a rien rendu de jouable. */
    suspend fun recordFailure(hoster: String) {
        if (hoster.isBlank()) return
        store.edit { prefs ->
            val key = intPreferencesKey(KO + hoster)
            prefs[key] = ((prefs[key] ?: 0) + 1).coerceAtMost(PLAFOND)
        }
    }

    private companion object {
        const val STORE = "moovie_hosters"
        const val OK = "ok:"
        const val KO = "ko:"

        /**
         * Plafond des compteurs.
         *
         * Sans lui, un hébergeur fidèle depuis des mois accumulerait des
         * milliers de réussites et resterait « bon » très longtemps après être
         * tombé — la mémoire deviendrait un dogme. Plafonner, c'est garder la
         * capacité de changer d'avis.
         */
        const val PLAFOND = 50
    }
}

/**
 * Le verdict, à partir des deux compteurs.
 *
 * **Une seule réussite suffit à sortir du purgatoire** : ce qui a joué ici peut
 * rejouer. À l'inverse il faut [SEUIL_ECHECS] échecs *sans aucune réussite*
 * pour condamner — trois essais sur un hébergeur momentanément en panne ne
 * doivent pas le reléguer pour autant.
 *
 * Extraite du dépôt pour être éprouvée sans magasin.
 */
fun verdict(reussites: Int, echecs: Int): HosterTrust = when {
    reussites > 0 -> HosterTrust.GOOD
    echecs >= SEUIL_ECHECS -> HosterTrust.BAD
    else -> HosterTrust.UNKNOWN
}

/**
 * Échecs consécutifs avant de reléguer un hébergeur jamais vu jouer.
 *
 * Quatre : assez pour traverser une panne passagère ou une soirée de maintenance
 * sans condamner, assez peu pour qu'un hébergeur mort cesse vite de coûter un
 * aller-retour à chaque lecture.
 */
const val SEUIL_ECHECS = 4
