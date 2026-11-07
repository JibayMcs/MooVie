# MooVie sur Amazon Fire TV Stick (Fire OS)

MooVie utilise le même APK Android que sur Android TV et téléphone. Le mode TV est sélectionné automatiquement lorsque l'appareil expose `amazon.hardware.fire_tv` (ou le mode TV/Leanback Android). Aucune dépendance aux Google Play Services n'est requise pour lancer l'interface.

## Appareils pris en charge

- **Fire OS 6, 7 et 8** : versions basées sur Android 7.1 (API 25), 9 (API 28), 10/11 (API 29/30), respectivement. `minSdk = 23` dans `app/build.gradle.kts` permet l'installation sur ces versions. Les autres versions de Fire OS ne sont pas garanties sans essai.
- **Fire OS 5** : Android 5.1 (API 22), **non pris en charge** car inférieur au `minSdk` actuel. Ne pas abaisser le minimum sans vérifier toutes les dépendances et API.
- **Vega OS** : **pas compatible avec l'APK Android**. Certains Fire TV Stick récents utilisent Vega OS ; ils demanderaient un portage distinct.

Identifier son système dans **Paramètres → Ma Fire TV → À propos** (les intitulés peuvent varier).

## Installer l'APK

1. Télécharger un APK MooVie construit depuis une version incluant la prise en charge Fire TV, ou construire `./gradlew :app:assembleDebug`.
2. Dans les paramètres du Fire TV Stick, activer les **Options pour les développeurs** (si absentes : sélectionner plusieurs fois le nom de l'appareil dans *À propos*), puis **Débogage ADB**.
3. Relever l'adresse IP du Fire TV sur le réseau local.
4. Sur le PC, avec Android Platform Tools installé :

```bash
adb connect 192.168.1.50:5555        # remplacer par l'IP du Fire TV
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n fr.moovie.tv/.MainActivity
```

Pour une release signée, remplacer le chemin de l'APK par le fichier téléchargé depuis les releases GitHub. On peut également transférer l'APK via un outil de sideload approprié.

## Télécommande

- **D-pad** : déplacer le focus ; **OK** : sélectionner, ou lecture/pause lorsque les contrôles sont masqués.
- **Retour** : fermer les panneaux ou revenir à l'écran précédent.
- **Lecture/Pause** : commander directement le lecteur.
- **Retour rapide / Avance rapide** : reculer / avancer de 15 secondes, même quand les contrôles sont masqués (si la télécommande émet les codes Android média standard).
- **Accueil** et **Volume** : restent principalement des fonctions gérées par Fire OS ou le téléviseur.

La saisie des clés d'API peut passer par le QR code de la télécommande web de MooVie, si le téléphone et le Fire TV sont sur le même réseau local.

## Vérifications conseillées sur un vrai Fire TV Stick

Le support a été ajouté au code, mais **n'a pas été validé sur appareil physique**. Tester en priorité :

1. Installation et présence de l'icône MooVie dans le lanceur d'applications.
2. Ouverture en mode **TV** plutôt que l'interface mobile, y compris après relancement.
3. Navigation D-pad / OK / Retour dans les profils, la recherche, les fiches et les réglages.
4. Lecture HLS / MP4, décodage matériel, audio, sous-titres, pause / reprise et avance / retour.
5. Sortie puis retour à l'application sans lecture fantôme ; veille et mémoire sur un Stick peu puissant.
6. QR code, réseau local, sauvegardes, permissions de stockage et installation des mises à jour (selon version de Fire OS).

Commandes de diagnostic :

```bash
adb shell getprop ro.build.version.sdk
adb shell pm list features | grep -E 'amazon.hardware.fire_tv|leanback|touchscreen'
adb logcat -c
adb shell am start -n fr.moovie.tv/.MainActivity
adb logcat -d -s AndroidRuntime:E ActivityTaskManager:I
```

Si la lecture échoue sur une source particulière, comparer avec une URL vidéo de test autorisée et noter modèle exact, Fire OS, codec, résolution et logs avant de modifier le lecteur.

Documentation Amazon : [identifier les Fire TV](https://developer.amazon.com/docs/device-specs/identify-fire-tv-devices.html) · [versions Fire OS](https://developer.amazon.com/docs/fire-tv/fire-os-overview.html).
