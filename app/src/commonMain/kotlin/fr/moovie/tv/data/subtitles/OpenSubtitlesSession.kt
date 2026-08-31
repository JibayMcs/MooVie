package fr.moovie.tv.data.subtitles

import fr.moovie.tv.shared.maintenantMs
import fr.moovie.tv.core.subtitles.model.SubtitleQuota
import fr.moovie.tv.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first

/** Ce que l'interface a besoin de savoir de la session, sans rien de secret. */
data class OsAccountState(
    val username: String = "",
    val connected: Boolean = false,
    val remembered: Boolean = false,
    val quota: SubtitleQuota = SubtitleQuota.Unknown,
)

/** Pourquoi une connexion a échoué, en termes affichables. */
enum class OsLoginError { BAD_CREDENTIALS, RATE_LIMITED, NETWORK }

/**
 * Session utilisateur OpenSubtitles : connexion, expiration, reconnexion.
 *
 * Le jeton vaut **24 h** et l'API n'offre aucun endpoint de rafraîchissement —
 * le renouveler veut dire renvoyer identifiant et mot de passe. C'est toute la
 * raison d'être de « se souvenir de moi » : sans les identifiants conservés,
 * l'utilisateur devrait se reconnecter chaque jour, au clavier virtuel d'une
 * télécommande.
 *
 * Se connecter n'est pas obligatoire. Sans compte, le téléchargement fonctionne
 * mais reste très plafonné, et le quota devient invisible : `/infos/user` répond
 * 401. C'est un confort, pas un péage.
 */
class OpenSubtitlesSession(
    private val api: OpenSubtitlesApi,
    private val settings: SettingsRepository = SettingsRepository(),
) {

    /**
     * Restaure la session au démarrage. À appeler avant le premier usage : sans
     * ça, un jeton parfaitement valide dort dans le magasin pendant que l'API
     * est interrogée en anonyme.
     */
    suspend fun restore() {
        val token = settings.osToken.first()
        if (token.isNotBlank() && !expired(settings.osTokenAt.first())) {
            api.token = token
        }
    }

    suspend fun state(): OsAccountState {
        val username = settings.osUsername.first()
        val token = settings.osToken.first()
        return OsAccountState(
            username = username,
            connected = token.isNotBlank() && !expired(settings.osTokenAt.first()),
            remembered = settings.osRemember.first(),
        )
    }

    /**
     * Connexion explicite. Conserve le mot de passe si — et seulement si —
     * [remember] le demande.
     */
    suspend fun login(
        username: String,
        password: String,
        remember: Boolean,
        nowMs: Long,
    ): Result<OsAccountState> {
        val response = api.login(username, password).getOrElse { failure ->
            return Result.failure(LoginFailure(failure.toLoginError()))
        }
        api.token = response.token
        settings.setOsCredentials(username, password, remember)
        settings.setOsSession(response.token, nowMs)

        return Result.success(
            OsAccountState(
                username = username,
                connected = true,
                remembered = remember,
                quota = SubtitleQuota(
                    remaining = response.user.remainingDownloads,
                    allowed = response.user.allowedDownloads,
                ),
            ),
        )
    }

    /**
     * Rouvre une session expirée sans rien demander, quand les identifiants ont
     * été conservés. Rend faux si l'utilisateur devra ressaisir.
     */
    suspend fun renewIfNeeded(nowMs: Long): Boolean {
        if (!expired(settings.osTokenAt.first()) && settings.osToken.first().isNotBlank()) {
            return true
        }
        if (!settings.osRemember.first()) return false
        val username = settings.osUsername.first()
        val password = settings.osPassword.first()
        if (username.isBlank() || password.isBlank()) return false

        return login(username, password, remember = true, nowMs = nowMs).isSuccess
    }

    /** Ferme la session côté serveur et oublie tout ce qui la rouvrirait. */
    suspend fun logout() {
        api.logout()
        api.token = null
        settings.forgetOsAccount()
    }

    private fun expired(issuedAtMs: Long): Boolean =
        issuedAtMs <= 0 || maintenantMs() - issuedAtMs >= TOKEN_LIFETIME_MS

    class LoginFailure(val reason: OsLoginError) : Exception(reason.name)

    private companion object {
        /**
         * 24 h annoncées ; on renouvelle une heure plus tôt pour ne pas
         * découvrir l'expiration au milieu d'un téléchargement.
         */
        const val TOKEN_LIFETIME_MS = 23L * 60 * 60 * 1000
    }
}

private fun Throwable.toLoginError(): OsLoginError = when (asOsFailure()) {
    is OsFailure.Unauthorized -> OsLoginError.BAD_CREDENTIALS
    is OsFailure.RateLimited -> OsLoginError.RATE_LIMITED
    else -> OsLoginError.NETWORK
}
