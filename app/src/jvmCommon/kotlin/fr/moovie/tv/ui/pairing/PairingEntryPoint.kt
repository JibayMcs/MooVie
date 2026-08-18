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
 * Vrai là où la section « Télécommande » a quelque chose à dire.
 *
 * Plus large que [pairingOffered], parce que la télécommande a **deux bouts** et
 * qu'un réglage existe à chacun : le téléviseur se laisse piloter (QR, révocation
 * des jetons), les autres pilotent (quel téléviseur, et comment l'oublier).
 *
 * ## Le pointeur en faisait partie, et c'était une erreur
 *
 * Il en était exclu au motif qu'« un ordinateur n'est ni l'un ni l'autre ». La
 * première moitié tient : personne ne pilote un poste de travail depuis son
 * canapé, et [pairingOffered] l'en garde toujours. La seconde était fausse — on
 * regarde une fiche sur son ordinateur exactement comme sur son téléphone, et
 * vouloir l'envoyer sur la télé du salon n'a rien de moins naturel là qu'ici.
 *
 * L'exclusion se voyait mal parce que le desktop **acceptait** déjà les
 * diffusions sans savoir les jouer : le serveur répondait « accepté » et il ne
 * se passait rien. Réserver le rôle de cible au téléviseur et donner celui
 * d'émetteur à tout le reste rend les deux moitiés cohérentes.
 */
@Composable
fun remoteOffered(): Boolean = true

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
