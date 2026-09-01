package fr.moovie.tv.data.sync.providers

import fr.moovie.tv.data.sync.CredentialField
import fr.moovie.tv.data.sync.SyncException
import fr.moovie.tv.data.sync.SyncFailure
import fr.moovie.tv.data.sync.SyncFile
import fr.moovie.tv.data.sync.SyncProvider
import fr.moovie.tv.data.sync.SyncProviderDescriptor
import fr.moovie.tv.data.sync.SyncStore
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import fr.moovie.tv.shared.dispatcherEs
import fr.moovie.tv.shared.sha1 as sha1Octets
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlin.concurrent.Volatile
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

const val B2_KEY_ID = "b2_key_id"
const val B2_APP_KEY = "b2_app_key"

/**
 * Backblaze B2.
 *
 * Choisi pour une raison précise : **son API native ne signe pas les requêtes.**
 * Le même service en S3 imposerait la SigV4 — trois cents lignes de canonisation
 * et de HMAC qu'on ne veut ni écrire ni déboguer. Ici, un en-tête `Authorization`
 * et du JSON.
 *
 * **La clé doit être limitée à un bucket.** C'est ce que Backblaze propose par
 * défaut quand on crée une clé applicative, et ça nous rend deux services : la
 * réponse d'autorisation nous donne alors le bucket, donc l'utilisateur n'a que
 * deux valeurs à coller — et une clé volée ne peut rien atteindre d'autre. Une
 * clé de compte est refusée avec un message qui le dit.
 */
val B2_DESCRIPTOR = SyncProviderDescriptor(
    provider = SyncProvider.BACKBLAZE_B2,
    fields = listOf(
        CredentialField(B2_KEY_ID),
        CredentialField(B2_APP_KEY, secret = true),
    ),
    open = { credentials ->
        val keyId = credentials[B2_KEY_ID]?.trim().orEmpty()
        val appKey = credentials[B2_APP_KEY]?.trim().orEmpty()
        if (keyId.isBlank() || appKey.isBlank()) null else B2SyncStore(keyId, appKey)
    },
)

/** Ce que l'autorisation nous apprend, valable quelques heures. */
private data class B2Session(
    val token: String,
    val apiUrl: String,
    val downloadUrl: String,
    val bucketId: String,
    val bucketName: String,
)

@OptIn(ExperimentalEncodingApi::class)
internal class B2SyncStore(
    private val keyId: String,
    private val applicationKey: String,
    private val client: HttpClient = defaultClient(),
) : SyncStore {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Session mémorisée : le jeton vaut plusieurs heures, et réautoriser à
     * chaque appel tripleraient le nombre de requêtes d'une synchro.
     */
    @Volatile
    private var session: B2Session? = null

    override suspend fun list(): List<SyncFile> = withSession { s ->
        val body = post(
            url = "${s.apiUrl}/b2api/$API_VERSION/b2_list_file_names",
            token = s.token,
            payload = """{"bucketId":"${s.bucketId}","prefix":"$PREFIX","maxFileCount":1000}""",
        )
        json.decodeFromString<ListResponse>(body).files
            .map { SyncFile(name = it.fileName, modifiedAt = it.uploadTimestamp) }
    }

    override suspend fun read(name: String): String? = withSession { s ->
        val reponse = client.get("${s.downloadUrl}/file/${s.bucketName}/${encode(name)}") {
            header("Authorization", s.token)
        }
        verdict(reponse)
        // 404 = le fichier a disparu entre le listage et la lecture. Ce n'est
        // pas une panne : un autre appareil a pu le remplacer.
        if (reponse.status.value == 404) null else reponse.bodyAsText()
    }

    override suspend fun write(name: String, content: String): Long = withSession { s ->
        val upload = json.decodeFromString<UploadUrl>(
            post(
                url = "${s.apiUrl}/b2api/$API_VERSION/b2_get_upload_url",
                token = s.token,
                payload = """{"bucketId":"${s.bucketId}"}""",
            ),
        )
        val bytes = content.encodeToByteArray()
        val reponse = client.post(upload.uploadUrl) {
            header("Authorization", upload.authorizationToken)
            header("X-Bz-File-Name", encode(name))
            header("X-Bz-Content-Sha1", sha1(bytes))
            contentType(ContentType.Application.Json)
            setBody(bytes)
        }
        verdict(reponse)
        val body = reponse.bodyAsText()
        // L'horodatage rendu est celui du serveur : c'est notre référence de
        // temps, la seule que tous les appareils partagent.
        json.decodeFromString<UploadedFile>(body).uploadTimestamp
    }

    /**
     * Autorise si besoin, exécute, et **réessaie une fois** si le jeton avait
     * expiré. Un jeton B2 vit quelques heures ; une TV allumée plus longtemps
     * que ça tomberait sinon sur un 401 à chaque synchro.
     */
    private suspend fun <T> withSession(block: suspend (B2Session) -> T): T =
        withContext(dispatcherEs) {
            val current = session ?: authorize().also { session = it }
            try {
                block(current)
            } catch (e: SyncException) {
                if (e.failure != SyncFailure.CREDENTIALS) throw e
                val fresh = authorize()
                session = fresh
                block(fresh)
            }
        }

    private suspend fun authorize(): B2Session {
        val basic = Base64.encode("$keyId:$applicationKey".encodeToByteArray())
        val reponse = client.get(
            "https://api.backblazeb2.com/b2api/$API_VERSION/b2_authorize_account",
        ) {
            header("Authorization", "Basic $basic")
        }
        verdict(reponse)
        val auth = json.decodeFromString<AuthResponse>(reponse.bodyAsText())
        val storage = auth.apiInfo.storageApi

        // Une clé de compte ne restreint aucun bucket : la liste est vide. On la
        // refuse ici plutôt que de laisser l'utilisateur découvrir un 403 à la
        // première synchro — et refuser oblige à créer une clé qui ne peut
        // toucher que ce bucket, ce qui vaut mieux pour lui.
        val bucket = storage.allowed.buckets.firstOrNull()
            ?: throw SyncException(
                SyncFailure.CREDENTIALS,
                "La clé doit être limitée à un bucket : en recréer une en la restreignant.",
            )

        // Les capacités se lisent à l'autorisation : autant le dire maintenant
        // qu'au premier refus, quand l'utilisateur ne fait plus le lien.
        val missing = REQUIRED_CAPABILITIES - storage.allowed.capabilities.toSet()
        if (missing.isNotEmpty()) {
            throw SyncException(
                SyncFailure.CREDENTIALS,
                "Il manque à la clé : ${missing.joinToString(", ")}.",
            )
        }

        return B2Session(
            token = auth.authorizationToken,
            apiUrl = storage.apiUrl,
            downloadUrl = storage.downloadUrl,
            bucketId = bucket.id,
            bucketName = bucket.name,
        )
    }

    private suspend fun post(url: String, token: String, payload: String): String {
        val reponse = client.post(url) {
            header("Authorization", token)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        verdict(reponse)
        return reponse.bodyAsText()
    }

    /**
     * Traduit HTTP en [SyncFailure]. C'est le seul endroit qui a le droit de
     * connaître des codes de statut : au-delà, le domaine ne voit que des
     * causes.
     */
    private fun verdict(reponse: HttpResponse) {
        val code = reponse.status.value
        when {
            reponse.status.isSuccess() || code == 404 -> Unit
            code == 401 || code == 403 ->
                throw SyncException(SyncFailure.CREDENTIALS, "B2 a refusé les identifiants")
            else -> throw SyncException(SyncFailure.STORE, "B2 a répondu $code")
        }
    }

    /**
     * `encodeURLParameter` puis `+` remplacé par `%20` : c'est exactement ce que
     * faisait `URLEncoder.encode(name, "UTF-8")` suivi du même remplacement. B2
     * attend un nom de fichier percent-encodé, où un `+` littéral resterait un
     * `+` et non une espace.
     */
    private fun encode(name: String): String =
        name.encodeURLParameter(spaceToPlus = true).replace("+", "%20")

    /** Hexadécimal minuscule, la forme que B2 attend dans `X-Bz-Content-Sha1`. */
    private fun sha1(bytes: ByteArray): String =
        sha1Octets(bytes).joinToString("") { octet ->
            val v = octet.toInt() and 0xFF
            HEX[v shr 4].toString() + HEX[v and 0x0F]
        }

    private companion object {
        /**
         * Version de l'API. La v4 (avril 2025) a **déplacé** `apiUrl`,
         * `downloadUrl` et `allowed` sous `apiInfo.storageApi`, et regroupé les
         * buckets autorisés dans un tableau au lieu d'un couple de champs à la
         * racine. Lire la réponse à plat, comme en v2, ne trouvait rien.
         */
        const val API_VERSION = "v4"

        /** Sans ces trois-là, la synchro ne peut ni lister, ni lire, ni publier. */
        val REQUIRED_CAPABILITIES = setOf("listFiles", "readFiles", "writeFiles")

        /** Préfixe commun : le bucket peut servir à autre chose que Moo-vie. */
        const val PREFIX = "moovie-sync-"
        const val HEX = "0123456789abcdef"

        fun defaultClient() = HttpClient {
            // B2 n'est bloqué par aucun FAI : DNS système, pas de DoH. Voir la
            // même décision pour TMDB et TheIntroDB.
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 30_000
            }
        }
    }
}

@Serializable
private data class AuthResponse(
    val authorizationToken: String,
    val apiInfo: ApiInfo,
)

@Serializable
private data class ApiInfo(val storageApi: StorageApi)

@Serializable
private data class StorageApi(
    val apiUrl: String,
    val downloadUrl: String,
    val allowed: Allowed,
)

@Serializable
private data class Allowed(
    /** Les buckets que la clé peut toucher. Vide = clé de compte, refusée. */
    val buckets: List<AllowedBucket> = emptyList(),
    val capabilities: List<String> = emptyList(),
)

@Serializable
private data class AllowedBucket(val id: String, val name: String)

@Serializable
private data class ListResponse(val files: List<B2File> = emptyList())

@Serializable
private data class B2File(val fileName: String, val uploadTimestamp: Long = 0)

@Serializable
private data class UploadUrl(val uploadUrl: String, val authorizationToken: String)

@Serializable
private data class UploadedFile(@SerialName("uploadTimestamp") val uploadTimestamp: Long = 0)
