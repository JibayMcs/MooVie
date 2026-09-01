package fr.moovie.tv.data.net

import io.ktor.client.HttpClient

/**
 * Client HTTP des API JSON ordinaires : OpenSubtitles, TheIntroDB, l'updater.
 *
 * Distinct de `clientTmdb`, qui porte un cache disque et une politique de
 * fraîcheur propres à un catalogue qu'on relit sans cesse. Ici on veut
 * l'inverse : aucune mise en cache, chaque appel touche le service.
 *
 * `expectSuccess` reste **faux**. Ces API logent leurs causes d'échec dans le
 * code de statut *et* dans le corps — OpenSubtitles rend un 406 aussi bien pour
 * un identifiant invalide que pour un quota épuisé, et seul le corps les
 * distingue. Lever à la place d'exposer la réponse aurait rendu ce diagnostic
 * impossible.
 *
 * **DNS système, pas de DoH** : comme TMDB, ces domaines ne sont pas bloqués
 * par les FAI. Le DoH est réservé à l'extraction de sources.
 */
expect val clientRest: HttpClient
