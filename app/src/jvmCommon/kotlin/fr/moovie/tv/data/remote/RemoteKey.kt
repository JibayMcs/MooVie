package fr.moovie.tv.data.remote

/**
 * Les touches qu'une télécommande virtuelle peut envoyer.
 *
 * Volontairement calquées sur ce que **l'application écoute déjà** : la
 * navigation passe par les `onPreviewKeyEvent` de neuf écrans partagés, et le
 * lecteur ajoute `MediaPlayPause` et `Spacebar`. Une touche qui ne correspond à
 * rien de géré donnerait un bouton mort à l'écran, ce qui est pire que pas de
 * bouton du tout.
 *
 * Il n'y a pas d'entrée « épisode suivant » pour cette raison : le lecteur
 * expose le bouton, mais aucune touche ne le déclenche aujourd'hui.
 *
 * `REWIND` et `FORWARD` sont volontairement les mêmes codes que `LEFT` et
 * `RIGHT`. C'est ce que fait une vraie télécommande : la flèche navigue dans les
 * listes et recule dans le lecteur, selon l'écran. Deux boutons pour un même
 * code, parce que ce que l'utilisateur cherche des yeux diffère selon qu'il
 * navigue ou qu'il regarde.
 */
enum class RemoteKey {
    UP,
    DOWN,
    LEFT,
    RIGHT,
    OK,
    BACK,
    PLAY_PAUSE,
    REWIND,
    FORWARD,
}

/**
 * Injecte une touche dans l'application, ou rend false si c'est impossible.
 *
 * **Dans notre propre application seulement.** Injecter dans le système
 * demanderait `INJECT_EVENTS`, une permission de signature qu'une application
 * tierce n'obtient pas — d'où une télécommande qui pilote Moo-vie, et pas le
 * téléviseur : elle ne peut ni l'allumer, ni changer de source, ni lancer l'app.
 *
 * L'implémentation Android passe par `Activity.dispatchKeyEvent`, c'est-à-dire
 * **le chemin exact qu'emprunte une vraie télécommande**. C'est ce qui fait que
 * rien dans les écrans n'a eu à changer : le lecteur, les rangées et les champs
 * de texte reçoivent l'événement comme d'habitude. Piloter `FocusManager` depuis
 * Compose aurait au contraire court-circuité tous ces gestionnaires.
 */
expect fun sendRemoteKey(key: RemoteKey): Boolean

/**
 * Tape un texte dans le champ focalisé, ou rend false.
 *
 * C'est le gain principal de la télécommande : chercher un titre à la
 * télécommande est aussi pénible que saisir une clé, le problème même que
 * l'appairage résout pour les réglages.
 */
expect fun sendRemoteText(text: String): Boolean

/** Vrai quand une application au premier plan peut recevoir des touches. */
expect fun remoteAvailable(): Boolean
