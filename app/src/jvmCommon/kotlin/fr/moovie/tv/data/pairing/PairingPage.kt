package fr.moovie.tv.data.pairing

/**
 * Les quelques libellés de la page, résolus par l'appelant.
 *
 * Ils viennent des ressources Compose comme tout le reste de l'app : cette
 * couche ne les connaît pas, elle les reçoit. C'est ce qui évite qu'un texte
 * affiché existe en double, le piège déjà payé avec `androidMain/res`.
 */
data class PairingTexts(
    val title: String,
    val intro: String,
    val filled: String,
    val submit: String,
    val done: String,
    val doneDetail: String,
)

/**
 * Page de saisie servie au téléphone, en un seul fichier.
 *
 * Aucune ressource externe — ni police, ni feuille de style, ni script. La page
 * est servie par un téléviseur sur le réseau local : tout ce qui pointerait
 * ailleurs serait une dépendance de plus pour afficher un formulaire de six
 * champs.
 */
fun pairingPage(fields: List<PairingField>, texts: PairingTexts, action: String): String {
    val inputs = fields.joinToString("\n") { field ->
        val note = if (field.filled) """<span class="set">${esc(texts.filled)}</span>""" else ""
        """
        <label for="${esc(field.id)}">${esc(field.label)}$note</label>
        <input id="${esc(field.id)}" name="${esc(field.id)}" value=""
               autocapitalize="off" autocorrect="off" spellcheck="false"
               autocomplete="off" enterkeyhint="next">
        """.trimIndent()
    }
    return document(
        texts.title,
        """
        <h1>${esc(texts.title)}</h1>
        <p class="intro">${esc(texts.intro)}</p>
        <form method="post" action="${esc(action)}">
        $inputs
        <button type="submit">${esc(texts.submit)}</button>
        </form>
        """.trimIndent(),
    )
}

/** Confirmation après envoi. */
fun pairingDonePage(texts: PairingTexts): String = document(
    texts.done,
    """
    <h1 class="ok">${esc(texts.done)}</h1>
    <p class="intro">${esc(texts.doneDetail)}</p>
    """.trimIndent(),
)

/**
 * Enveloppe commune.
 *
 * `viewport` est ce qui fait la différence entre un formulaire utilisable au
 * doigt et une page de bureau miniature : sans lui, le téléphone dézoome et les
 * champs deviennent illisibles. Et les entrées font 16 px au moins, sous quoi
 * iOS zoome de force à chaque mise au point et décale la page.
 */
private fun document(title: String, body: String): String = """
<!doctype html>
<html lang="fr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(title)}</title>
<style>
  :root { color-scheme: dark; }
  body { margin:0; padding:24px; background:#101014; color:#f2f2f5;
         font-family:system-ui,-apple-system,sans-serif; line-height:1.5; }
  main { max-width:520px; margin:0 auto; }
  h1 { font-size:1.4rem; margin:0 0 4px; }
  h1.ok { color:#7ddc7d; }
  .intro { color:#a0a0ab; margin:0 0 24px; }
  label { display:block; margin:18px 0 6px; font-weight:600; }
  .set { font-weight:400; color:#7ddc7d; margin-left:8px; font-size:.85rem; }
  input { width:100%; box-sizing:border-box; font-size:16px; padding:12px;
          border-radius:10px; border:1px solid #33333d; background:#1b1b22;
          color:#f2f2f5; }
  input:focus { outline:2px solid #e5335b; border-color:transparent; }
  button { width:100%; margin-top:28px; padding:14px; font-size:16px;
           font-weight:600; border:0; border-radius:10px;
           background:#e5335b; color:#fff; }
</style>
</head>
<body><main>
$body
</main></body>
</html>
""".trimIndent()

/** Échappe le texte inséré dans le HTML : un libellé traduit reste du texte. */
private fun esc(raw: String): String = raw
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
