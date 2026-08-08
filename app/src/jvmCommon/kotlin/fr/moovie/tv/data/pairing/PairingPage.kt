package fr.moovie.tv.data.pairing

import fr.moovie.tv.data.remote.remoteAvailable

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
    val submit: String,
    val done: String,
    val doneDetail: String,
    val remoteTitle: String = "",
    val remoteIntro: String = "",
    val remoteType: String = "",
    val remoteSend: String = "",
    val remoteToSettings: String = "",
    val remoteToRemote: String = "",
    val remoteBack: String = "",
)

/**
 * Page de saisie servie au téléphone, en un seul fichier.
 *
 * Aucune ressource externe — ni police, ni feuille de style, ni script. La page
 * est servie par un téléviseur sur le réseau local : tout ce qui pointerait
 * ailleurs serait une dépendance de plus pour afficher un formulaire de sept
 * champs, et sur un Wi-Fi sans accès à Internet elle arriverait sans style.
 * C'est aussi pourquoi il n'y a pas de Tailwind ici : sa variante CDN romprait
 * cette autonomie, et sa variante compilée demanderait une chaîne Node dans un
 * projet Kotlin pour une page.
 */
fun pairingPage(fields: List<PairingField>, texts: PairingTexts, action: String): String {
    // Groupé, et titré par le service. Un champ « Mot de passe » seul au milieu
    // d'un formulaire ne dit pas de quel compte il est le mot de passe, et
    // « Identifiant de clé » ne se rattache à rien sans le nom du service.
    // `groupBy` conserve l'ordre d'apparition, qui est celui des réglages.
    val inputs = fields.groupBy { it.group }.entries.joinToString("\n") { (group, groupFields) ->
        val rows = groupFields.joinToString("\n") { field ->
            // Pré-rempli avec ce qui est en place sur le téléviseur. `esc`
            // échappe le guillemet : une phrase de passe en contenant un
            // fermerait l'attribut et amputerait la valeur.
            """<div class="field"><label for="${esc(field.id)}">${esc(field.label)}</label>""" +
                """<input id="${esc(field.id)}" name="${esc(field.id)}" """ +
                """value="${esc(field.value)}" """ +
                """autocapitalize="off" autocorrect="off" spellcheck="false" """ +
                """autocomplete="off" enterkeyhint="next"></div>"""
        }
        """<section><h2>${esc(group)}</h2>
$rows
</section>"""
    }
    // Proposé seulement si la plateforme sait injecter des touches : sur desktop
    // `remoteAvailable()` est faux, et un lien vers une page qui répond 404
    // serait pire que pas de lien.
    val remoteLink = if (!remoteAvailable() || texts.remoteToRemote.isEmpty()) "" else
        """<p class="intro" style="margin-top:28px">""" +
            """<a href="$action/remote">${esc(texts.remoteToRemote)}</a></p>"""
    return document(
        texts.title,
        """
<h1>${esc(texts.title)}</h1>
<div class="rule"></div>
<p class="intro">${esc(texts.intro)}</p>
<form method="post" action="${esc(action)}">
$inputs
<button type="submit">${esc(texts.submit)}</button>
</form>
$remoteLink
        """.trim(),
    )
}

/**
 * Télécommande virtuelle, à la manière de celle de Google Home.
 *
 * **C'est ici que le script devient nécessaire**, et c'est le seul endroit. Un
 * envoi de formulaire par appui rechargerait la page à chaque flèche : injouable
 * pour naviguer. `fetch` envoie et ne redessine rien. Le script reste en ligne,
 * donc la page garde sa propriété essentielle — elle marche sans Internet, seul
 * le réseau local est nécessaire.
 *
 * Les réponses sont ignorées volontairement (`catch` vide) : un appui perdu se
 * corrige en réappuyant, alors qu'une alerte à chaque micro-coupure rendrait la
 * télécommande insupportable.
 *
 * `touch-action:manipulation` supprime le délai de 300 ms que les navigateurs
 * mobiles gardent pour guetter un double-appui. Sans lui, chaque flèche répond
 * avec un tiers de seconde de retard et la navigation paraît cassée.
 */
fun remotePage(texts: PairingTexts, base: String): String = document(
    texts.remoteTitle,
    """
<h1>${esc(texts.remoteTitle)}</h1>
<div class="rule"></div>
<p class="intro">${esc(texts.remoteIntro)}</p>

<div class="pad">
  <button class="k up" onclick="k('UP')" aria-label="Haut">&#9650;</button>
  <button class="k left" onclick="k('LEFT')" aria-label="Gauche">&#9664;</button>
  <button class="k ok" onclick="k('OK')">OK</button>
  <button class="k right" onclick="k('RIGHT')" aria-label="Droite">&#9654;</button>
  <button class="k down" onclick="k('DOWN')" aria-label="Bas">&#9660;</button>
</div>

<div class="row">
  <button class="k wide" onclick="k('REWIND')" aria-label="Reculer">&#171; 15s</button>
  <button class="k wide" onclick="k('PLAY_PAUSE')" aria-label="Lecture ou pause">&#9199;</button>
  <button class="k wide" onclick="k('FORWARD')" aria-label="Avancer">15s &#187;</button>
</div>
<button class="k back" onclick="k('BACK')">&#8592; ${esc(texts.remoteBack)}</button>

<div class="field" style="margin-top:28px">
  <label for="t">${esc(texts.remoteType)}</label>
  <input id="t" autocapitalize="off" autocorrect="off" spellcheck="false"
         autocomplete="off" enterkeyhint="send">
</div>
<button onclick="t()">${esc(texts.remoteSend)}</button>
<p class="intro" style="margin-top:24px">
  <a href="$base">${esc(texts.remoteToSettings)}</a>
</p>

<script>
var A=${'"'}$base${'"'};
function k(n){fetch(A+'/key',{method:'POST',body:'k='+n}).catch(function(){});}
function t(){var e=document.getElementById('t');
  fetch(A+'/text',{method:'POST',body:'t='+encodeURIComponent(e.value)})
    .then(function(){e.value='';}).catch(function(){});}
document.getElementById('t').addEventListener('keydown',function(e){
  if(e.key==='Enter'){e.preventDefault();t();}});
</script>
    """.trim(),
)

/** Confirmation après envoi. */
fun pairingDonePage(texts: PairingTexts): String = document(
    texts.done,
    """
<h1 class="ok">${esc(texts.done)}</h1>
<div class="rule"></div>
<p class="intro">${esc(texts.doneDetail)}</p>
    """.trim(),
)

/**
 * Enveloppe commune : le style de l'application, transposé au navigateur.
 *
 * Les valeurs ne sont pas approchées à l'œil, elles sont **reprises du thème**
 * (`MoovieIdentity.kt`) — d'où les commentaires qui nomment leur équivalent
 * Kotlin. Les trois règles qui font l'identité de Moo-vie :
 *
 * - **Angles droits partout.** `MoovieShape` vaut `RectangleShape`, et c'est un
 *   choix assumé contre l'interface « molle et générique » ; un formulaire à
 *   coins arrondis n'aurait aucun air de famille avec l'app.
 * - **Rien au repos, dégradé à l'action.** `moovieSurface` ne remplit pas un
 *   contrôle inactif : il pose un verre blanc translucide et un liseré dégradé
 *   quand il devient actif. Un champ au repos n'a donc qu'un trait, et prend son
 *   dégradé à la mise au point.
 * - **Orange → magenta → violet**, dans le sens de lecture, comme `MoovieGradient`.
 *
 * Le bouton d'envoi, lui, porte en permanence l'état « actif » : au doigt il n'y
 * a pas de focus à recevoir, et la seule action de la page doit se voir.
 *
 * Deux détails qui décident de l'utilisabilité au doigt, pas de l'esthétique :
 * `viewport` (sans lui le téléphone dézoome et il faut pincer pour lire), et des
 * entrées à **16 px au moins**, en dessous de quoi iOS zoome de force à chaque
 * mise au point et décale la page.
 */
private fun document(title: String, body: String): String = """
<!doctype html>
<html lang="fr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<!-- Reliquats, mais utiles ici : beaucoup de lecteurs de QR ouvrent le lien dans
     leur propre WebView plutôt que dans le navigateur, et une WebView qui n'a
     pas `setUseWideViewPort` compose la page à ~980 px puis la dézoome — la page
     est alors minuscule et il faut pincer pour saisir. Ces deux en-têtes sont
     honorés par une partie d'entre elles là où `viewport` seul est ignoré. -->
<meta name="HandheldFriendly" content="true">
<meta name="MobileOptimized" content="width">
<meta name="theme-color" content="#101014">
<title>${esc(title)}</title>
<style>
:root{
  color-scheme:dark;
  --bg:#101014;          /* fond général, dans la lignée du thème sombre */
  --fg:#f2f2f5;
  --muted:#9a9a9a;       /* texte secondaire des modales */
  --line:#2a2a30;
  --accent:#e8214e;      /* MOOVIE_ACCENT = MOOVIE_MAGENTA */
  --ok:#7ddc7d;          /* confirmation, même vert que l'état enregistré côté TV */
  --glass:rgba(255,255,255,.08);   /* GLASS_FOCUSED */
  --glass-on:rgba(255,255,255,.16);/* GLASS_PRESSED */
  /* MoovieGradient : MOOVIE_ORANGE -> MOOVIE_MAGENTA -> MOOVIE_VIOLET */
  --gradient:linear-gradient(90deg,#ff9a2e,#e8214e,#7b3fb0);
}
*{box-sizing:border-box}
html{-webkit-text-size-adjust:100%}
body{margin:0;background:var(--bg);color:var(--fg);line-height:1.5;
     font-family:system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
main{width:100%;max-width:560px;margin:0 auto;
     padding:32px 20px calc(48px + env(safe-area-inset-bottom))}
h1{margin:0 0 12px;font-size:1.55rem;font-weight:700;letter-spacing:-.01em}
h1.ok{color:var(--ok)}
/* Le liseré identité, ici en filet de titre plutôt qu'en soulignement de focus. */
.rule{height:3px;background:var(--gradient);margin:0 0 20px}
.intro{margin:0 0 28px;color:var(--muted)}
/* Titre de section en magenta, comme l'écran de réglages où il est rendu avec
   `MaterialTheme.colorScheme.primary` — soit MOOVIE_MAGENTA. */
section{margin:0 0 30px}
section+section{padding-top:26px;border-top:1px solid var(--line)}
h2{margin:0 0 16px;font-size:.82rem;font-weight:700;letter-spacing:.09em;
   text-transform:uppercase;color:var(--accent)}
.field{position:relative;margin:0 0 20px}
label{display:block;margin:0 0 8px;font-weight:600}
/* Cadre rectangulaire visible, comme les champs de l'écran de réglages : un
   champ n'est pas un bouton, il doit montrer où l'on tape avant qu'on y tape. */
input{display:block;width:100%;padding:14px 12px;font:inherit;font-size:16px;
      color:var(--fg);background:rgba(255,255,255,.03);
      border:1px solid var(--line);border-radius:0;
      -webkit-appearance:none;appearance:none}
input:focus{outline:0;background:var(--glass);border-color:#4a4a55}
/* Liseré dégradé à la mise au point, sur le conteneur : `border-image` sur
   l'entrée peindrait les quatre côtés, et une entrée n'accepte pas de
   pseudo-élément. C'est l'état actif de moovieSurface, transposé. */
.field::after{content:"";position:absolute;left:0;right:0;bottom:0;height:2px;
              background:var(--gradient);transform:scaleX(0);
              transition:transform .15s ease-out}
.field:focus-within::after{transform:scaleX(1)}
button{display:block;width:100%;margin-top:36px;padding:16px;
       font:inherit;font-size:1.05rem;font-weight:700;color:var(--fg);
       background:var(--glass);cursor:pointer;
       border:0;border-bottom:3px solid transparent;border-image:var(--gradient) 1;
       border-radius:0;-webkit-appearance:none}
button:active{background:var(--glass-on)}
a{color:var(--accent)}
/* --- Télécommande ---------------------------------------------------------
   Grille 3x3 : la croix directionnelle se vise au pouce sans regarder, ce qui
   n'est vrai que si les cibles sont grandes et toujours au même endroit.
   `touch-action` retire le délai de double-appui, sans quoi chaque flèche
   répondrait avec un tiers de seconde de retard. */
.pad{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin:8px 0 16px}
.pad .up{grid-column:2}
.pad .left{grid-column:1;grid-row:2}
.pad .ok{grid-column:2;grid-row:2}
.pad .right{grid-column:3;grid-row:2}
.pad .down{grid-column:2;grid-row:3}
.row{display:grid;grid-template-columns:repeat(3,1fr);gap:10px}
.k{margin:0;padding:22px 8px;font-size:1.1rem;font-weight:700;color:var(--fg);
   background:var(--glass);border:0;border-radius:0;cursor:pointer;
   touch-action:manipulation;-webkit-tap-highlight-color:transparent;
   -webkit-appearance:none;width:100%}
.k:active{background:var(--glass-on)}
.k.ok{border-bottom:3px solid transparent;border-image:var(--gradient) 1}
.k.back{margin-top:10px;padding:16px;font-size:1rem}
</style>
</head>
<body><main>
$body
</main></body>
</html>
""".trim()

/** Échappe le texte inséré dans le HTML : un libellé traduit reste du texte. */
private fun esc(raw: String): String = raw
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
