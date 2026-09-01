package fr.moovie.tv.ui.onboarding

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.moovie.tv.data.profile.ProfileRepository
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.settings.StreamLanguage
import fr.moovie.tv.data.store.DEFAULT_PROFILE_ID
import fr.moovie.tv.data.tmdb.KeyCheck
import fr.moovie.tv.data.tmdb.TmdbRepository
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.common_back
import fr.moovie.tv.resources.onboarding_finish
import fr.moovie.tv.resources.onboarding_fresh
import fr.moovie.tv.resources.onboarding_fresh_help
import fr.moovie.tv.resources.onboarding_intro
import fr.moovie.tv.resources.onboarding_next
import fr.moovie.tv.resources.onboarding_no_key
import fr.moovie.tv.resources.onboarding_restore
import fr.moovie.tv.resources.onboarding_restore_help
import fr.moovie.tv.resources.onboarding_step_of
import fr.moovie.tv.resources.onboarding_phone_help
import fr.moovie.tv.resources.onboarding_title
import fr.moovie.tv.resources.pairing_action
import fr.moovie.tv.resources.pairing_key_checking
import fr.moovie.tv.resources.pairing_key_missing
import fr.moovie.tv.resources.pairing_key_rejected
import fr.moovie.tv.resources.pairing_key_unreachable
import fr.moovie.tv.ui.adaptive.isTouchUi
import fr.moovie.tv.ui.backup.BackupSection
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.navigation.Screen
import fr.moovie.tv.ui.theme.MOOVIE_ACCENT
import fr.moovie.tv.ui.theme.MoovieShape
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

internal val DIM = Color(0xFF9A9A9A)

/**
 * Les questions de l'installation, dans l'ordre où elles sont posées.
 *
 * L'ordre n'est pas arbitraire. La clé vient en premier parce qu'elle est la
 * seule sans laquelle l'application ne peut rien montrer — la poser plus tard
 * ferait répondre à trois questions avant d'apprendre que la seule qui compte
 * n'a pas la bonne réponse. Le profil vient en dernier parce que c'est la seule
 * qui parle de la personne plutôt que de l'installation : on finit sur elle.
 */
private enum class Etape { CLE, LANGUE, LECTURE, PROFIL }

/** Par où l'on est entré : les trois écrans que ce parcours peut afficher. */
private enum class Mode { BIENVENUE, RESTAURATION, QUESTIONS }

/**
 * Première installation : un parcours, et non un aiguillage vers les réglages.
 *
 * ## Ce qu'il remplace
 *
 * L'écran d'avant posait un seul choix — restaurer, appairer, ou « saisie
 * manuelle » — et cette dernière branche ouvrait les réglages en laissant la
 * personne s'y débrouiller. Elle atterrissait dans un écran de configuration
 * complet, organisé par catégories techniques, sans savoir lequel des trente
 * réglages était celui qu'on lui demandait. L'installation se terminait alors
 * en silence, sur la première clé saisie : la langue des flux, la lecture
 * automatique et le nom du profil gardaient leurs valeurs par défaut sans que
 * personne n'ait su qu'on pouvait en décider.
 *
 * ## Une question par écran
 *
 * Le parcours est fait pour être suivi à la télécommande autant qu'au doigt, et
 * c'est la télécommande qui décide de la forme : une page unique se parcourt
 * mal quand on n'a que quatre flèches et pas de défilement libre. Une question
 * par écran donne à chaque fois une seule cible à atteindre, et un compteur qui
 * dit combien il en reste.
 *
 * ## Aucune réponse par défaut
 *
 * Chaque étape doit être répondue pour être franchie, et rien n'est
 * présélectionné — pas même les valeurs que le magasin utiliserait de toute
 * façon. Un interrupteur déjà positionné aurait fait de « Suivant » une
 * approbation tacite : on validerait un choix qu'on n'a pas fait, en croyant
 * simplement avancer. La seule exception est la clé, qui se pré-remplit quand
 * un appairage ou une sauvegarde vient de la poser — la retaper serait une
 * punition, pas une confirmation.
 *
 * ## La sauvegarde court-circuite tout
 *
 * Restaurer, c'est rapporter des réponses déjà données : la clé, la langue, la
 * lecture, les profils, et le reste de ce que le parcours ne demande pas. Le
 * questionnaire est donc sauté en entier, et l'installation marquée finie. Le
 * seul cas qui revient au début est l'import qui n'apportait pas de clé — la
 * sauvegarde d'un appareil qui n'en avait pas non plus.
 *
 * @param onReady appelé une fois l'installation menée à terme, et seulement là.
 *   L'écran d'avant se refermait sur l'apparition d'une clé, ce qui aujourd'hui
 *   sauterait les trois questions suivantes.
 * @param pairingDialog boîte d'appairage d'un téléphone, ou null là où
 *   l'appairage n'est pas proposé — c'est-à-dire partout sauf sur un
 *   téléviseur. Un **emplacement** et non un appel direct : elle porte un
 *   serveur HTTP local, qui n'existe que sur les cibles JVM. Le choix « depuis
 *   mon téléphone » disparaît avec elle, au lieu de mener à une modale vide.
 */
@Composable
fun OnboardingScreen(
    onReady: () -> Unit,
    pairingDialog: (
        @Composable (onDismiss: () -> Unit, notice: String?, onSaved: () -> Unit) -> Unit
    )? = null,
) {
    val reglages = remember { SettingsRepository() }
    val profils = remember { ProfileRepository() }
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(Mode.BIENVENUE) }
    var etape by remember { mutableStateOf(Etape.CLE) }
    // Un import qui n'apportait pas de clé laisse l'installation à moitié faite :
    // on le dit, plutôt que de renvoyer sur le même choix sans explication.
    var importSansCle by remember { mutableStateOf(false) }
    var appairage by remember { mutableStateOf(false) }

    // --- Les réponses, et rien d'autre ---
    //
    // Elles vivent ici plutôt que dans chaque étape : revenir en arrière doit
    // retrouver ce qu'on avait répondu, et une étape démontée emporterait son
    // `remember`. C'est aussi ce qui permet de n'écrire dans le magasin qu'au
    // franchissement, une fois la réponse sûre.
    var cle by remember { mutableStateOf("") }
    var cleValidee by remember { mutableStateOf(false) }
    var verdict by remember { mutableStateOf<String?>(null) }
    var langue by remember { mutableStateOf<StreamLanguage?>(null) }
    var enchainer by remember { mutableStateOf<Boolean?>(null) }
    var passerGeneriques by remember { mutableStateOf<Boolean?>(null) }
    var nomProfil by remember { mutableStateOf("") }

    // Une clé peut déjà être là : appairage, sauvegarde, ou installation
    // interrompue à l'étape suivante. La montrer vaut mieux que de la redemander.
    LaunchedEffect(Unit) {
        val existante = reglages.tmdbApiKey.first()
        if (existante.isNotBlank() && cle.isBlank()) cle = existante
    }

    // Les verdicts sont résolus ici : `stringResource` est un composable, il ne
    // peut pas être appelé depuis la coroutine qui les choisit.
    val texteVerification = stringResource(Res.string.pairing_key_checking)
    val texteRefus = stringResource(Res.string.pairing_key_rejected)
    val texteInjoignable = stringResource(Res.string.pairing_key_unreachable)
    val texteAbsente = stringResource(Res.string.pairing_key_missing)

    /**
     * Fait valider la clé par TMDB avant de la garder.
     *
     * Sur « la clé n'est pas vide » on laisserait passer une faute de frappe,
     * et l'installation se terminerait sur un accueil sans catalogue, sans rien
     * pour comprendre. Trente-deux caractères hexadécimaux tapés à la
     * télécommande, c'est exactement là qu'une coquille se glisse.
     */
    fun validerCle(apres: () -> Unit) {
        scope.launch {
            verdict = texteVerification
            val valeur = cle.trim()
            if (valeur.isBlank()) {
                verdict = texteAbsente
                return@launch
            }
            verdict = when (TmdbRepository().validateKey(valeur)) {
                KeyCheck.VALID -> {
                    reglages.setTmdbApiKey(valeur)
                    cleValidee = true
                    apres()
                    null
                }
                KeyCheck.REJECTED -> texteRefus
                KeyCheck.UNREACHABLE -> texteInjoignable
            }
        }
    }

    /**
     * Entre dans le questionnaire, et **déclare l'installation inachevée**.
     *
     * Sans cette écriture, un parcours interrompu après la première question se
     * serait rouvert sur l'accueil. Le drapeau n'existe pas encore à ce
     * moment-là, si bien que la règle de migration prend le relais : elle lit
     * « une clé existe » et conclut « déjà installé ». C'est la bonne réponse
     * pour une mise à jour — c'est la mauvaise pour quelqu'un qui vient de
     * fermer l'application entre la clé et la langue des flux, et qui n'aurait
     * jamais revu les trois questions suivantes.
     *
     * Le poser à l'entrée plutôt qu'à la sortie couvre aussi la fermeture
     * brutale, qui ne laisse la main à personne.
     */
    fun ouvrirQuestionnaire(depuis: Etape) {
        scope.launch { reglages.setOnboardingDone(false) }
        importSansCle = false
        etape = depuis
        mode = Mode.QUESTIONS
    }

    /** Dernière étape franchie : on nomme le profil et on ferme le parcours. */
    fun terminer() {
        scope.launch {
            // Le profil d'origine est **renommé**, pas créé : il existe dans
            // toute installation, y compris celles d'avant la fonctionnalité, et
            // en créer un second laisserait un « Profil par défaut » vide à côté.
            profils.rename(DEFAULT_PROFILE_ID, nomProfil.trim())
            reglages.setOnboardingDone(true)
            onReady()
        }
    }

    /** L'étape courante a-t-elle sa réponse ? C'est ce qui arme « Suivant ». */
    val repondu = when (etape) {
        Etape.CLE -> cle.isNotBlank() && verdict != texteVerification
        Etape.LANGUE -> langue != null
        Etape.LECTURE -> enchainer != null && passerGeneriques != null
        Etape.PROFIL -> nomProfil.isNotBlank()
    }

    fun suivant() {
        when (etape) {
            // La seule étape dont le franchissement demande le réseau : elle
            // avance depuis le rappel de validation, pas depuis l'appui.
            Etape.CLE -> validerCle { etape = Etape.LANGUE }
            Etape.LANGUE -> {
                langue?.let { choix -> scope.launch { reglages.setStreamLanguage(choix) } }
                etape = Etape.LECTURE
            }
            Etape.LECTURE -> {
                val suite = enchainer
                val generiques = passerGeneriques
                if (suite != null && generiques != null) {
                    scope.launch {
                        reglages.setAutoPlayNext(suite)
                        reglages.setSkipIntroOutro(generiques)
                    }
                }
                etape = Etape.PROFIL
            }
            Etape.PROFIL -> terminer()
        }
    }

    fun precedent() {
        when (etape) {
            // Revenir depuis la première question, c'est ressortir du
            // questionnaire — sans quoi le bouton serait inerte, ce qui se lit
            // comme une panne plutôt que comme une limite.
            Etape.CLE -> mode = Mode.BIENVENUE
            Etape.LANGUE -> etape = Etape.CLE
            Etape.LECTURE -> etape = Etape.LANGUE
            Etape.PROFIL -> etape = Etape.LECTURE
        }
    }

    // **Le focus se pose d'office à la télécommande, et jamais au doigt.**
    //
    // Au D-pad, arriver sur un écran sans focus veut dire que la première flèche
    // ne fait rien : elle sert à entrer dans la page au lieu d'agir.
    //
    // Au doigt, la même ligne était un défaut. `MoovieButton` peint son état
    // « actif » sur le focus autant que sur `selected` — les deux passent par
    // `moovieSurface` — si bien que la première commande de chaque étape
    // s'affichait comme une réponse déjà donnée. Pire, un écran tactile ne
    // déplace pas le focus : répondre « Non » allumait le second bouton sans
    // éteindre le premier, et l'on voyait deux réponses cochées. Exactement ce
    // que ce parcours prétend éviter en ne présélectionnant rien.
    val auDoigt = isTouchUi
    val premiereCommande = remember { FocusRequester() }
    LaunchedEffect(mode, etape, auDoigt) {
        if (mode == Mode.QUESTIONS && !auDoigt) runCatching { premiereCommande.requestFocus() }
    }

    // **Les marges d'un salon ne sont pas celles d'une main.**
    //
    // 56 dp de côté, c'est le recul d'un téléviseur : la zone sûre d'une dalle
    // dont les bords sont rognés par le sur-balayage, sur 960 dp de large. Sur un
    // téléphone de 400 dp, les mêmes 56 dp mangent plus du quart de la largeur —
    // le questionnaire s'affichait en médaillon, encadré de noir, alors que
    // c'est le seul écran de l'application qui n'a rien d'autre à montrer que
    // lui-même. Le reste de l'app fait déjà cette distinction : 16 dp au doigt,
    // 48 dp ailleurs, sur la fiche comme sur l'accueil.
    val margeH = if (auDoigt) 20.dp else 56.dp
    val margeV = if (auDoigt) 24.dp else 48.dp
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = margeH, vertical = margeV),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        when (mode) {
            Mode.BIENVENUE -> Bienvenue(
                importSansCle = importSansCle,
                pairingOffert = pairingDialog != null,
                onCommencer = { ouvrirQuestionnaire(Etape.CLE) },
                onRestaurer = { mode = Mode.RESTAURATION },
                onAppairer = { appairage = true },
            )

            Mode.RESTAURATION -> BackupSection(
                importOnly = true,
                onLeave = {
                    // Une sauvegarde qui apporte une clé apporte tout le reste :
                    // il n'y a plus rien à demander, l'installation est faite.
                    scope.launch {
                        if (reglages.tmdbApiKey.first().isNotBlank()) {
                            reglages.setOnboardingDone(true)
                            onReady()
                        } else {
                            importSansCle = true
                            mode = Mode.BIENVENUE
                        }
                    }
                },
            )

            Mode.QUESTIONS -> {
                Progression(rang = etape.ordinal, total = Etape.entries.size)

                when (etape) {
                    Etape.CLE -> EtapeCle(
                        cle = cle,
                        onCle = {
                            cle = it
                            // La coche et le verdict portent sur la clé qui
                            // vient d'être vérifiée, pas sur celle qu'on tape.
                            cleValidee = false
                            verdict = null
                        },
                        validee = cleValidee,
                        verdict = verdict,
                        onAppairer = if (pairingDialog != null) {
                            { appairage = true }
                        } else {
                            null
                        },
                        focus = premiereCommande,
                    )

                    Etape.LANGUE -> EtapeLangue(
                        choix = langue,
                        onChoix = { langue = it },
                        focus = premiereCommande,
                    )

                    Etape.LECTURE -> EtapeLecture(
                        enchainer = enchainer,
                        onEnchainer = { enchainer = it },
                        passerGeneriques = passerGeneriques,
                        onPasserGeneriques = { passerGeneriques = it },
                        focus = premiereCommande,
                    )

                    Etape.PROFIL -> EtapeProfil(
                        nom = nomProfil,
                        onNom = { nomProfil = it },
                        focus = premiereCommande,
                    )
                }

                Navigation(
                    derniere = etape == Etape.PROFIL,
                    peutAvancer = repondu,
                    onPrecedent = ::precedent,
                    onSuivant = ::suivant,
                )
            }
        }
    }

    if (appairage && pairingDialog != null) {
        pairingDialog(
            { appairage = false },
            verdict,
            // Le téléphone a envoyé quelque chose : on relit la clé posée dans
            // le magasin, on la fait valider, et on entre dans le questionnaire
            // à l'étape suivante. Fermer la modale avant la réponse de TMDB
            // couperait le serveur au moment où il doit encore répondre au
            // téléphone, qui afficherait une erreur pour un envoi réussi.
            {
                scope.launch {
                    cle = reglages.tmdbApiKey.first()
                    validerCle {
                        appairage = false
                        // La clé est faite : le questionnaire reprend à la
                        // question suivante, et non au début.
                        ouvrirQuestionnaire(Etape.LANGUE)
                    }
                }
            },
        )
    }
}

/**
 * L'écran d'entrée : par où commencer.
 *
 * Il ne pose pas de question, il n'a donc pas de numéro — le compteur ne compte
 * que ce à quoi il faut répondre.
 */
@Composable
private fun Bienvenue(
    importSansCle: Boolean,
    pairingOffert: Boolean,
    onCommencer: () -> Unit,
    onRestaurer: () -> Unit,
    onAppairer: () -> Unit,
) {
    // Même réserve que dans les questions : au doigt, un focus posé d'office
    // allume la première carte comme si elle était déjà choisie.
    val auDoigt = isTouchUi
    val premier = remember { FocusRequester() }
    LaunchedEffect(auDoigt) { if (!auDoigt) runCatching { premier.requestFocus() } }

    Text(
        stringResource(Res.string.onboarding_title),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        stringResource(Res.string.onboarding_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = DIM,
        // Bornée : en pleine largeur d'un 1080p la ligne devient illisible.
        modifier = Modifier.widthIn(max = 760.dp),
    )

    if (importSansCle) {
        Text(
            stringResource(Res.string.onboarding_no_key),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFE0B057),
            modifier = Modifier.widthIn(max = 760.dp),
        )
    }

    Choix(
        label = stringResource(Res.string.onboarding_restore),
        help = stringResource(Res.string.onboarding_restore_help),
        onClick = onRestaurer,
        modifier = Modifier.focusRequester(premier),
    )
    // Avant la saisie manuelle, parce que c'est la même situation par un
    // meilleur chemin : sur une TV, coller la clé au clavier tactile bat
    // toujours 32 caractères hexadécimaux à la télécommande. Le questionnaire
    // reste dessous, et reprend de toute façon après l'appairage.
    if (pairingOffert) {
        Choix(
            label = stringResource(Res.string.pairing_action),
            help = stringResource(Res.string.onboarding_phone_help),
            onClick = onAppairer,
        )
    }
    Choix(
        label = stringResource(Res.string.onboarding_fresh),
        help = stringResource(Res.string.onboarding_fresh_help),
        onClick = onCommencer,
    )
}

/**
 * Où l'on en est : des pastilles et un compte.
 *
 * Les deux, et non l'un ou l'autre. Les pastilles se lisent d'un coup d'œil à
 * trois mètres, le compte reste lisible pour qui ne les distingue pas — et il
 * est le seul à dire ce qu'il reste à faire, ce qu'une pastille éteinte
 * n'exprime qu'à qui sait déjà les compter.
 */
@Composable
private fun Progression(rang: Int, total: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(total) { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == rang) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (index <= rang) MOOVIE_ACCENT else Color(0x33FFFFFF)),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(Res.string.onboarding_step_of, (rang + 1).toString(), total.toString()),
            style = MaterialTheme.typography.labelLarge,
            color = DIM,
        )
    }
}

/**
 * Retour et Suivant, en bas de chaque question.
 *
 * « Suivant » est désarmé tant que l'étape n'a pas sa réponse : c'est la forme
 * la plus honnête du caractère obligatoire, parce qu'elle se voit avant l'appui
 * plutôt que de le refuser après.
 */
@Composable
private fun Navigation(
    derniere: Boolean,
    peutAvancer: Boolean,
    onPrecedent: () -> Unit,
    onSuivant: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
    ) {
        MoovieButton(onClick = onPrecedent) { Text(stringResource(Res.string.common_back)) }
        Spacer(Modifier.weight(1f))
        MoovieButton(
            onClick = onSuivant,
            enabled = peutAvancer,
            selected = peutAvancer,
        ) {
            Text(
                stringResource(
                    if (derniere) Res.string.onboarding_finish else Res.string.onboarding_next,
                ),
            )
        }
    }
}

@Composable
private fun Choix(
    label: String,
    help: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Ces choix-là portent leur propre surface, contrairement au reste de l'app.
    // Un MoovieButton au repos n'est que son libellé : son habillage vient du
    // focus, ce qui va en face d'une télécommande mais ne donne rien au doigt.
    // Ailleurs le contexte suffit à dire qu'on peut toucher — une affiche, une
    // ligne de réglage. Ici il n'y a que deux paragraphes sur du noir, et c'est
    // le tout premier écran de l'app : il ne peut pas se permettre d'être
    // ambigu.
    MoovieButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 900.dp)
            .border(1.dp, Color(0x33FFFFFF), MoovieShape),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(help, style = MaterialTheme.typography.bodySmall, color = DIM)
        }
    }
}

/**
 * Écran racine de la pile, une fois su si l'installation a été menée à terme.
 *
 * Rend null tant que la réponse n'est pas lue : construire la pile sur l'accueil
 * puis la remplacer aurait laissé passer une image d'accueil vide, exactement ce
 * que l'écran d'installation existe pour éviter. La lecture est un accès
 * DataStore, de l'ordre de la dizaine de millisecondes.
 *
 * La question n'est plus « existe-t-il une clé » mais « a-t-on fini » : le
 * parcours pose des questions après la clé, et s'arrêter à elle les sauterait
 * toutes au premier redémarrage. Voir `SettingsRepository.onboardingDone`, qui
 * porte la règle de migration des installations d'avant.
 *
 * [override] court-circuite tout : c'est le crochet de dev qui ouvre directement
 * le lecteur sur un flux de test.
 */
@Composable
fun rememberStartScreen(override: Screen? = null): Screen? {
    if (override != null) return override
    val repo = remember { SettingsRepository() }
    val fini by produceState<Boolean?>(initialValue = null) {
        value = repo.onboardingDone.first()
    }
    return fini?.let { if (it) Screen.Home else Screen.Onboarding }
}
