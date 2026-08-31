package fr.moovie.tv.data.tmdb

import io.ktor.client.HttpClient

/**
 * Client HTTP de TMDB, **unique pour tout le processus**.
 *
 * L'unicité n'est pas une optimisation : côté JVM, le cache disque d'OkHttp
 * verrouille son répertoire, et deux instances concurrentes sur le même dossier
 * le corrompent. Or `TmdbRepository` est construit à la demande un peu partout
 * — accueil, recherche, fiche.
 *
 * Il n'utilise **pas** le DNS-over-HTTPS d'`AppDns` : TMDB n'est pas bloqué par
 * les FAI, seuls les domaines des sources le sont.
 */
expect val clientTmdb: HttpClient
