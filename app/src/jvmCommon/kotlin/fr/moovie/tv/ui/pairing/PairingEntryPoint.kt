package fr.moovie.tv.ui.pairing

import androidx.compose.runtime.Composable
import fr.moovie.tv.ui.adaptive.LocalUiFlavor
import fr.moovie.tv.ui.adaptive.UiFlavor

/**
 * Vrai là où proposer l'appairage a un sens.
 *
 * Réservé à la TV : c'est le seul appareil où saisir une clé se fait à la
 * télécommande, lettre par lettre sur un clavier en grille. Un téléphone et un
 * ordinateur ont déjà un clavier — leur proposer de s'appairer à eux-mêmes
 * n'aurait aucun sens.
 *
 * Une garde d'**exécution**, pas de compilation : TV et téléphone sont le même
 * APK, et seul `LocalUiFlavor` les distingue, résolu au démarrage. Deux écrans
 * la posent maintenant (réglages et première installation), d'où cette fonction
 * plutôt qu'une condition recopiée — c'est le genre de règle qui diverge dès
 * qu'elle existe en double.
 */
@Composable
fun pairingOffered(): Boolean = LocalUiFlavor.current == UiFlavor.TV || PAIRING_FORCED

/**
 * Crochet de dev : `MOOVIE_PAIRING=1` montre l'appairage hors TV.
 *
 * Il n'existe que pour le tester. L'émulateur Android est derrière le NAT de
 * QEMU : l'adresse qu'il met dans le QR n'appartient qu'à son réseau virtuel, et
 * aucun téléphone du Wi-Fi ne la joindra jamais. Lancer le desktop avec cette
 * variable met l'application sur la **vraie** adresse du poste, ce qui rend le
 * QR scannable pour de bon et permet d'éprouver la page au doigt, sur un
 * téléphone réel, sans dépendre d'un téléviseur.
 *
 * Même esprit que `MOOVIE_TEST_STREAM` : hors du chemin normal, et sans effet
 * tant que la variable n'est pas posée.
 */
private val PAIRING_FORCED = System.getenv("MOOVIE_PAIRING") == "1"
