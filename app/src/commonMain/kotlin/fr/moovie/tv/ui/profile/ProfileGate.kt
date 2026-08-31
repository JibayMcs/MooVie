package fr.moovie.tv.ui.profile

import fr.moovie.tv.shared.maintenantMs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.moovie.tv.ui.remote.remoteTypable
import fr.moovie.tv.data.profile.Profile
import fr.moovie.tv.data.profile.ProfileRepository
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.common_cancel
import fr.moovie.tv.resources.profile_add
import fr.moovie.tv.resources.profile_choose
import fr.moovie.tv.resources.profile_create
import fr.moovie.tv.resources.profile_default
import fr.moovie.tv.resources.profile_delete
import fr.moovie.tv.resources.profile_delete_confirm
import fr.moovie.tv.resources.profile_done
import fr.moovie.tv.resources.profile_manage
import fr.moovie.tv.resources.profile_name_hint
import fr.moovie.tv.resources.profile_rename
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.theme.MOOVIE_MAGENTA
import fr.moovie.tv.ui.theme.MOOVIE_ORANGE
import fr.moovie.tv.ui.theme.MOOVIE_VIOLET
import fr.moovie.tv.ui.theme.MoovieShape
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private val DIM = Color(0xFF9A9A9A)

/**
 * Palette des profils. Six couleurs franches, lisibles à trois mètres : sur une
 * TV, c'est la pastille qu'on reconnaît, pas le nom qu'on lit.
 */
private val PROFILE_COLORS = listOf(
    MOOVIE_MAGENTA,
    MOOVIE_ORANGE,
    MOOVIE_VIOLET,
    Color(0xFF2EA6FF),
    Color(0xFF3FB950),
    Color(0xFF00C2B2),
)

fun profileColor(index: Int): Color = PROFILE_COLORS[index.mod(PROFILE_COLORS.size)]

/**
 * Porte d'entrée : « Qui regarde ? ».
 *
 * Posée **avant** la pile de navigation, comme l'écran d'installation, et pour
 * la même raison : ce qu'on choisit ici décide quel fichier tous les dépôts vont
 * ouvrir. Laisser l'accueil se composer d'abord reviendrait à afficher les
 * reprises de quelqu'un d'autre le temps d'une image.
 *
 * Elle ne s'affiche jamais tant qu'il n'existe qu'un profil : il n'y a rien à
 * choisir, et demander quand même transformerait chaque lancement en formalité.
 * Elle réapparaît dès qu'un second existe, y compris quand on demande
 * explicitement à changer de profil.
 */
@Composable
fun ProfileGate(onPicked: (String) -> Unit) {
    val repo = remember { ProfileRepository() }
    val scope = rememberCoroutineScope()
    val profiles by repo.profiles.collectAsState(initial = emptyList())

    var managing by remember { mutableStateOf(false) }
    // Non nul = un formulaire de nom est ouvert. La création et le renommage
    // partagent le même champ : c'est la même question posée deux fois.
    var editing by remember { mutableStateOf<ProfileEdit?>(null) }
    var confirmDelete by remember { mutableStateOf<Profile?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))
            .padding(vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(Res.string.profile_choose),
            style = MaterialTheme.typography.headlineMedium,
        )

        when {
            confirmDelete != null -> {
                val target = confirmDelete!!
                Text(
                    stringResource(Res.string.profile_delete_confirm, target.displayName()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = DIM,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 420.dp).padding(horizontal = 24.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MoovieButton(onClick = { confirmDelete = null }) {
                        Text(stringResource(Res.string.common_cancel))
                    }
                    MoovieButton(onClick = {
                        scope.launch {
                            repo.delete(target.id)
                            confirmDelete = null
                        }
                    }) {
                        Text(stringResource(Res.string.profile_delete))
                    }
                }
            }

            editing != null -> NameForm(
                initial = editing!!.initialName,
                onCancel = { editing = null },
                onConfirm = { name ->
                    val edit = editing!!
                    scope.launch {
                        if (edit.profileId == null) {
                            repo.create(name, maintenantMs())
                        } else {
                            repo.rename(edit.profileId, name)
                        }
                        editing = null
                    }
                },
            )

            else -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    // Marges dans le contentPadding : l'agrandissement au focus
                    // déborde dedans au lieu d'être rogné par le conteneur.
                    contentPadding = PaddingValues(horizontal = 24.dp),
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        ProfileTile(
                            profile = profile,
                            managing = managing,
                            onPick = { scope.launch { repo.setActive(profile.id); onPicked(profile.id) } },
                            onRename = { editing = ProfileEdit(profile.id, profile.name) },
                            onDelete = { confirmDelete = profile },
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MoovieButton(onClick = { editing = ProfileEdit(null, "") }) {
                        Text(stringResource(Res.string.profile_add))
                    }
                    MoovieButton(onClick = { managing = !managing }, selected = managing) {
                        Text(
                            stringResource(
                                if (managing) Res.string.profile_done else Res.string.profile_manage,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** Ce qu'un formulaire de nom est en train de faire : créer (null) ou renommer. */
private data class ProfileEdit(val profileId: String?, val initialName: String)

@Composable
private fun ProfileTile(
    profile: Profile,
    managing: Boolean,
    onPick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MoovieButton(
            onClick = if (managing) onRename else onPick,
            // Appui long = renommer, sans passer par le mode gestion. Sur une
            // télécommande c'est le même geste que le menu contextuel du reste
            // de l'app, donc rien de nouveau à apprendre.
            onLongClick = onRename,
            contentPadding = PaddingValues(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(profileColor(profile.colorIndex), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    profile.initial(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFF0A0A0A),
                )
            }
        }
        Text(
            profile.displayName(),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 120.dp),
        )
        // Le profil d'origine ne se supprime pas : ses fichiers sont ceux de
        // l'installation, l'effacer viderait l'historique de tout le monde.
        if (managing && !profile.isDefault) {
            MoovieButton(
                onClick = onDelete,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(stringResource(Res.string.profile_delete), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (managing && profile.isDefault) {
            Text(
                stringResource(Res.string.profile_rename),
                style = MaterialTheme.typography.bodySmall,
                color = DIM,
            )
        }
    }
}

@Composable
private fun NameForm(
    initial: String,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    val focusManager = LocalFocusManager.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.widthIn(max = 420.dp).padding(horizontal = 24.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF555555), MoovieShape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (draft.isEmpty()) Text(stringResource(Res.string.profile_name_hint), color = Color(0xFF888888))
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .remoteTypable(
                        label = stringResource(Res.string.profile_name_hint),
                        value = draft,
                        onValueChange = { draft = it },
                    )
                    // Le champ avale les flèches : sans ça le D-pad ne peut plus
                    // en sortir, une télécommande n'ayant pas de touche Tab.
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val direction = when (event.key) {
                            Key.DirectionDown -> FocusDirection.Down
                            Key.DirectionUp -> FocusDirection.Up
                            else -> return@onPreviewKeyEvent false
                        }
                        focusManager.moveFocus(direction)
                        true
                    },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MoovieButton(onClick = onCancel) { Text(stringResource(Res.string.common_cancel)) }
            MoovieButton(
                onClick = { onConfirm(draft) },
                enabled = draft.isNotBlank(),
                selected = draft.isNotBlank(),
            ) {
                Text(stringResource(Res.string.profile_create))
            }
        }
    }
}

/** Nom affiché : le profil d'origine n'en stocke pas, il porte le libellé traduit. */
@Composable
private fun Profile.displayName(): String =
    name.ifBlank { stringResource(Res.string.profile_default) }

@Composable
private fun Profile.initial(): String =
    displayName().trim().take(1).uppercase().ifBlank { "?" }
