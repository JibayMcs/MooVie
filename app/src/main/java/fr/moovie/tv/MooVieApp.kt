package fr.moovie.tv

import android.app.Application

/**
 * Point d'entrée applicatif. Servira plus tard à initialiser les singletons
 * (client HTTP partagé, base locale, registre d'extracteurs de sources).
 */
class MooVieApp : Application()
