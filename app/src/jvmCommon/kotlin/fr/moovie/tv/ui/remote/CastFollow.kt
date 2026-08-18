package fr.moovie.tv.ui.remote

import androidx.compose.runtime.Composable

/**
 * Ce qu'il faut faire, par plateforme, quand une diffusion vient d'être acceptée.
 *
 * ## Pourquoi une abstraction plutôt qu'un appel direct
 *
 * Même raison que [fr.moovie.tv.data.download.DownloadForeground], et même
 * forme : l'envoi vit désormais en `jvmCommon`, il ne peut donc pas nommer un
 * `Service` Android ni une permission système.
 *
 * **Android** doit suivre la box en arrière-plan — c'est ce qui met ses commandes
 * sur l'écran verrouillé et, surtout, ce qui recopie sa progression téléphone
 * rangé. Il lui faut pour cela un service de premier plan, donc l'autorisation de
 * notifier, demandée ici plutôt qu'au premier lancement : au moment du geste,
 * elle s'explique toute seule.
 *
 * **Le desktop n'a pas ce problème et n'a donc rien à faire.** Sa fenêtre est
 * ouverte ou l'application est fermée ; il n'existe pas d'entre-deux où une
 * diffusion continuerait sans que personne ne regarde. Inventer un service et
 * une notification pour un cas qui n'existe pas serait du mécanisme gratuit —
 * d'où une implémentation vide, exactement comme pour les téléchargements.
 *
 * Rend le geste à exécuter, pas un booléen : la demande d'autorisation a besoin
 * d'un lanceur mémorisé dans la composition, ce qu'une fonction ordinaire ne
 * saurait pas tenir.
 */
@Composable
expect fun rememberCastFollow(): () -> Unit
