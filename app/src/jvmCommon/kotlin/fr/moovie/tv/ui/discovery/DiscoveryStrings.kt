package fr.moovie.tv.ui.discovery

import fr.moovie.tv.data.discovery.DiscoveryKind
import fr.moovie.tv.data.discovery.MoodQuestion
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.discovery_group_humeur
import fr.moovie.tv.resources.discovery_group_pepites
import fr.moovie.tv.resources.discovery_group_recoupement
import fr.moovie.tv.resources.discovery_group_revoir
import fr.moovie.tv.resources.discovery_group_sagas
import fr.moovie.tv.resources.discovery_opt_amis
import fr.moovie.tv.resources.discovery_opt_court
import fr.moovie.tv.resources.discovery_opt_detendue
import fr.moovie.tv.resources.discovery_opt_deux
import fr.moovie.tv.resources.discovery_opt_famille
import fr.moovie.tv.resources.discovery_opt_peuimporte
import fr.moovie.tv.resources.discovery_opt_peur
import fr.moovie.tv.resources.discovery_opt_rire
import fr.moovie.tv.resources.discovery_opt_serie
import fr.moovie.tv.resources.discovery_opt_seul
import fr.moovie.tv.resources.discovery_opt_soiree
import fr.moovie.tv.resources.discovery_opt_tension
import fr.moovie.tv.resources.discovery_optd_amis
import fr.moovie.tv.resources.discovery_optd_court
import fr.moovie.tv.resources.discovery_optd_detendue
import fr.moovie.tv.resources.discovery_optd_deux
import fr.moovie.tv.resources.discovery_optd_famille
import fr.moovie.tv.resources.discovery_optd_peuimporte
import fr.moovie.tv.resources.discovery_optd_peur
import fr.moovie.tv.resources.discovery_optd_rire
import fr.moovie.tv.resources.discovery_optd_serie
import fr.moovie.tv.resources.discovery_optd_seul
import fr.moovie.tv.resources.discovery_optd_soiree
import fr.moovie.tv.resources.discovery_optd_tension
import fr.moovie.tv.resources.discovery_q_avec
import fr.moovie.tv.resources.discovery_q_avec_sub
import fr.moovie.tv.resources.discovery_q_humeur
import fr.moovie.tv.resources.discovery_q_humeur_sub
import fr.moovie.tv.resources.discovery_q_temps
import fr.moovie.tv.resources.discovery_q_temps_sub
import fr.moovie.tv.resources.discovery_why_humeur
import fr.moovie.tv.resources.discovery_why_pepites
import fr.moovie.tv.resources.discovery_why_recoupement
import fr.moovie.tv.resources.discovery_why_revoir
import fr.moovie.tv.resources.discovery_why_sagas
import org.jetbrains.compose.resources.StringResource

/**
 * Les libellés de la découverte, en un seul endroit.
 *
 * Une table plutôt qu'un `when` disséminé dans l'écran : ajouter une réponse au
 * questionnaire doit casser la compilation ici, à l'endroit exact où il faudra
 * écrire ses trois traductions, et pas se contenter d'afficher une clé nue.
 */
internal fun questionTitle(q: MoodQuestion): StringResource = when (q) {
    MoodQuestion.HUMEUR -> Res.string.discovery_q_humeur
    MoodQuestion.AVEC -> Res.string.discovery_q_avec
    MoodQuestion.TEMPS -> Res.string.discovery_q_temps
}

internal fun questionSub(q: MoodQuestion): StringResource = when (q) {
    MoodQuestion.HUMEUR -> Res.string.discovery_q_humeur_sub
    MoodQuestion.AVEC -> Res.string.discovery_q_avec_sub
    MoodQuestion.TEMPS -> Res.string.discovery_q_temps_sub
}

internal fun optionLabel(id: String): StringResource = when (id) {
    "detendue" -> Res.string.discovery_opt_detendue
    "tension" -> Res.string.discovery_opt_tension
    "rire" -> Res.string.discovery_opt_rire
    "peur" -> Res.string.discovery_opt_peur
    "seul" -> Res.string.discovery_opt_seul
    "deux" -> Res.string.discovery_opt_deux
    "amis" -> Res.string.discovery_opt_amis
    "famille" -> Res.string.discovery_opt_famille
    "court" -> Res.string.discovery_opt_court
    "soiree" -> Res.string.discovery_opt_soiree
    "serie" -> Res.string.discovery_opt_serie
    else -> Res.string.discovery_opt_peuimporte
}

internal fun optionDetail(id: String): StringResource = when (id) {
    "detendue" -> Res.string.discovery_optd_detendue
    "tension" -> Res.string.discovery_optd_tension
    "rire" -> Res.string.discovery_optd_rire
    "peur" -> Res.string.discovery_optd_peur
    "seul" -> Res.string.discovery_optd_seul
    "deux" -> Res.string.discovery_optd_deux
    "amis" -> Res.string.discovery_optd_amis
    "famille" -> Res.string.discovery_optd_famille
    "court" -> Res.string.discovery_optd_court
    "soiree" -> Res.string.discovery_optd_soiree
    "serie" -> Res.string.discovery_optd_serie
    else -> Res.string.discovery_optd_peuimporte
}

/** Le titre d'un groupe. Celui du recoupement porte les titres qui l'ont produit. */
internal fun groupTitle(kind: DiscoveryKind): StringResource = when (kind) {
    DiscoveryKind.RECOUPEMENT -> Res.string.discovery_group_recoupement
    DiscoveryKind.REVOIR -> Res.string.discovery_group_revoir
    DiscoveryKind.SAGAS -> Res.string.discovery_group_sagas
    DiscoveryKind.PEPITES -> Res.string.discovery_group_pepites
    DiscoveryKind.HUMEUR -> Res.string.discovery_group_humeur
}

/** La phrase qui explique le groupe, sous la carte désignée. */
internal fun groupWhy(kind: DiscoveryKind): StringResource = when (kind) {
    DiscoveryKind.RECOUPEMENT -> Res.string.discovery_why_recoupement
    DiscoveryKind.REVOIR -> Res.string.discovery_why_revoir
    DiscoveryKind.SAGAS -> Res.string.discovery_why_sagas
    DiscoveryKind.PEPITES -> Res.string.discovery_why_pepites
    DiscoveryKind.HUMEUR -> Res.string.discovery_why_humeur
}
