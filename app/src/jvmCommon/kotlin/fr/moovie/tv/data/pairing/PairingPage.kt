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
    /**
     * Libellés des touches. Ils ne s'affichent pas — la télécommande n'a que
     * des icônes — mais ce sont les **seuls** noms qu'un lecteur d'écran
     * annonce, et la seule chose qu'un utilisateur entende. Une icône sans nom
     * accessible est un bouton muet.
     */
    val keyUp: String = "",
    val keyDown: String = "",
    val keyLeft: String = "",
    val keyRight: String = "",
    val keyOk: String = "",
    val keyBack: String = "",
    val keyPlayPause: String = "",
    val keyRewind: String = "",
    val keyForward: String = "",
    val keyKeyboard: String = "",
    val remoteNoHaptics: String = "",
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
fun remotePage(texts: PairingTexts, base: String, appLink: String = ""): String = document(
    texts.remoteTitle,
    """
<h1 class="brand">${esc(texts.remoteTitle)}</h1>
<div class="rule brandrule"></div>

<div class="np" id="np" hidden>
  <div class="npmeta">
    <img class="npart" id="npart" alt="" hidden>
    <div class="nptxt">
      <div class="nptitle" id="nptitle"></div>
      <div class="npsub" id="npsub"></div>
    </div>
  </div>
  <div class="npbar" id="npbar"><s></s><i id="npfill"></i><b id="npdot"></b></div>
  <div class="nptime"><span id="nppos">0:00</span><span id="npleft"></span></div>
</div>

<div class="remote">
  <div class="dpad">
    <button class="sec up"    data-k="UP"    aria-label="${esc(texts.keyUp)}">$CHEVRON_UP</button>
    <button class="sec right" data-k="RIGHT" aria-label="${esc(texts.keyRight)}">$CHEVRON_RIGHT</button>
    <button class="sec down"  data-k="DOWN"  aria-label="${esc(texts.keyDown)}">$CHEVRON_DOWN</button>
    <button class="sec left"  data-k="LEFT"  aria-label="${esc(texts.keyLeft)}">$CHEVRON_LEFT</button>
    <button class="ok"        data-k="OK"    aria-label="${esc(texts.keyOk)}"><i></i></button>
  </div>

  <div class="row">
    <button class="rk" data-k="REWIND"     aria-label="${esc(texts.keyRewind)}">$ICON_REWIND</button>
    <button class="rk hero" data-k="PLAY_PAUSE" aria-label="${esc(texts.keyPlayPause)}"><span class="ip">$ICON_PLAY</span><span class="ia">$ICON_PAUSE</span></button>
    <button class="rk" data-k="FORWARD"    aria-label="${esc(texts.keyForward)}">$ICON_FORWARD</button>
  </div>

  <div class="row">
    <button class="rk" data-k="BACK" aria-label="${esc(texts.keyBack)}">$ICON_BACK</button>
    <button class="rk" id="kb" aria-label="${esc(texts.keyKeyboard)}">$ICON_KEYBOARD</button>
  </div>

  <div class="kbwrap" id="kbwrap" hidden>
    <p class="kblabel" id="kblabel" hidden></p>
    <input id="t" autocapitalize="off" autocorrect="off" spellcheck="false"
           autocomplete="off" enterkeyhint="send" placeholder="${esc(texts.remoteType)}"
           aria-label="${esc(texts.remoteType)}">
  </div>
</div>

<p class="intro nohap" id="nohap" hidden>${esc(texts.remoteNoHaptics)}</p>
<p class="intro foot"><a href="$base">${esc(texts.remoteToSettings)}</a></p>

<script>
var A=${'"'}$base${'"'};

/* Bascule vers l'application quand elle est installée.
   `intent://` plutôt qu'un simple `moovie://` : le premier est compris par
   Chrome, qui ouvre l'application si elle est là et **retombe sur l'URL de
   repli** sinon. Un schéma privé, lui, mène à une page d'erreur chez qui n'a
   pas Moo-vie — exactement ce qu'on ne veut pas.
   Une seule tentative par session : le repli ramène ici, et sans garde on
   boucler ait. */
var LINK=${'"'}$appLink${'"'};
if (LINK && !sessionStorage.getItem('moovie-tried')) {
  sessionStorage.setItem('moovie-tried','1');
  location.href = LINK;
}

/* Retour haptique.
   Sur écran tactile, `pointerdown` **n'accorde pas** l'activation utilisateur :
   seuls `pointerup` et `touchend` le font. Un premier appui vibrait donc dans
   le vide. On amorce l'activation dès le premier relâchement, et l'API est
   testée avant chaque appel — iOS ne l'implémente pas, et là où elle manque il
   ne reste que le néon, la télécommande fonctionnant de la même façon. */
var haptics = ('vibrate' in navigator);
function buzz(p){ if (haptics) { try { navigator.vibrate(p); } catch(e){} } }
window.addEventListener('pointerup', function primer(){
  buzz(1);
  window.removeEventListener('pointerup', primer);
});

function lit(el){ el.classList.add('lit'); setTimeout(function(){ el.classList.remove('lit'); }, 180); }
function send(n){ fetch(A+'/key',{method:'POST',body:'k='+n}).catch(function(){}); }

/* Un appui au doigt produit aussi un `click` derrière lui. On ne peut pas s'en
   passer : c'est par lui que passent les technologies d'assistance et le
   clavier. On ignore donc celui qui suit immédiatement un geste au pointeur. */
var lastPointer=0;
function echo(){ return Date.now()-lastPointer < 700; }

function press(el,n){ lit(el); buzz(n==='OK'?24:(n==='BACK'?[8,26,8]:12)); send(n); }

/* --- Croix directionnelle : un joystick, pas quatre boutons ---------------
   Le geste est suivi sur le disque entier, avec capture du pointeur : on
   descend, on garde le doigt posé, on tourne vers la gauche, et la direction
   suit — sans relâcher, et sans passer par le centre.

   Trois règles font la sensation :
   - une **zone morte** au centre, du rayon du bouton OK ;
   - une **hystérésis** de 8° sur les diagonales, sans quoi la direction
     oscillerait entre deux flèches au moindre tremblement du pouce ;
   - OK ne se déclenche **que** si le geste a commencé sur lui, jamais en
     glissant dessus depuis une flèche. */
var pad=document.querySelector('.dpad');
var AXIS={RIGHT:0,DOWN:90,LEFT:180,UP:270};
/* `active` plutôt que l'état de la capture : traverser la zone morte remet la
   direction à zéro, et une garde qui s'appuyait sur `hasPointerCapture` faisait
   alors mourir le geste — on ressortait du centre sans que rien ne reparte. */
var active=false, cur=null, cx=0, cy=0, dead=0, timer=null, tick=null;

function diff(a,b){ var d=Math.abs(a-b)%360; return d>180?360-d:d; }

function dirAt(x,y){
  var dx=x-cx, dy=y-cy;
  if (Math.sqrt(dx*dx+dy*dy) < dead) return null;
  var deg=(Math.atan2(dy,dx)*180/Math.PI+360)%360;
  if (cur && diff(deg,AXIS[cur]) < 53) return cur;
  var best=null, bd=999;
  for (var k in AXIS){ var d=diff(deg,AXIS[k]); if (d<bd){ bd=d; best=k; } }
  return best;
}

function light(dir){
  pad.querySelectorAll('.sec').forEach(function(s){
    s.classList.toggle('on', dir!==null && s.getAttribute('data-k')===dir);
  });
}

/* Répétition au maintien : descendre vingt épisodes ne doit pas demander vingt
   appuis. Le compte repart à chaque changement de direction. */
function hold(n){
  stop();
  timer=setTimeout(function(){ tick=setInterval(function(){ buzz(8); send(n); },120); },380);
}
function stop(){ clearTimeout(timer); clearInterval(tick); timer=null; tick=null; }

function go(dir){
  cur=dir; light(dir);
  if (!dir){ stop(); return; }
  buzz(12); send(dir); hold(dir);
}

function release(){ active=false; stop(); cur=null; light(null); }

pad.addEventListener('pointerdown', function(e){
  lastPointer=Date.now();
  if (e.target.closest('.ok')) return;
  var r=pad.getBoundingClientRect();
  cx=r.left+r.width/2; cy=r.top+r.height/2;
  dead=r.width*0.19;
  try { pad.setPointerCapture(e.pointerId); } catch(err){}
  active=true; cur=null;
  var d=dirAt(e.clientX,e.clientY);
  if (d) go(d);
});
pad.addEventListener('pointermove', function(e){
  if (!active) return;
  var d=dirAt(e.clientX,e.clientY);
  if (d===null){ stop(); cur=null; light(null); return; }
  if (d!==cur) go(d);
});
['pointerup','pointercancel','lostpointercapture'].forEach(function(ev){
  pad.addEventListener(ev, release);
});

/* Chemin d'assistance et de clavier : les secteurs ne reçoivent plus de
   pointeur (le disque s'en charge), mais restent de vrais boutons — un lecteur
   d'écran et la touche Entrée passent par `click`. */
pad.querySelectorAll('.sec').forEach(function(el){
  el.addEventListener('click', function(){ if (!echo()) press(el, el.getAttribute('data-k')); });
});

var ok=pad.querySelector('.ok');
ok.addEventListener('pointerdown', function(e){ e.stopPropagation(); lastPointer=Date.now(); press(ok,'OK'); });
ok.addEventListener('click', function(){ if (!echo()) press(ok,'OK'); });

/* Les touches rondes du bas : appui simple, sans joystick. */
document.querySelectorAll('.rk[data-k]').forEach(function(el){
  var n=el.getAttribute('data-k');
  el.addEventListener('pointerdown', function(){ lastPointer=Date.now(); press(el,n); });
  el.addEventListener('click', function(){ if (!echo()) press(el,n); });
});

var kb=document.getElementById('kb'), wrap=document.getElementById('kbwrap'), t=document.getElementById('t');
var kblabel=document.getElementById('kblabel');

/* Ouverture manuelle : le repli, quand le téléviseur n'annonce aucun champ —
   un écran qu'on n'a pas instrumenté, ou une TV d'une version antérieure. */
kb.addEventListener('click',function(){
  lit(kb); buzz(12);
  wrap.hidden=!wrap.hidden;
  if(!wrap.hidden) t.focus();
});

/* Envoi au fil de la frappe, amorti : sans l'amortissement chaque lettre
   serait une requête, avec un délai trop long la TV traîne derrière le doigt.
   Le champ n'est plus vidé après l'envoi — le serveur **remplace** désormais le
   contenu du champ annoncé, si bien que ce qu'on voit ici est ce qu'il y a
   là-bas. Le vider ferait effacer le champ au relevé suivant. */
var typeTimer=null;
t.addEventListener('input',function(){
  clearTimeout(typeTimer);
  typeTimer=setTimeout(function(){
    fetch(A+'/text',{method:'POST',body:'t='+encodeURIComponent(t.value)}).catch(function(){});
  },250);
});
t.addEventListener('keydown',function(e){
  if(e.key!=='Enter') return;
  e.preventDefault();
  buzz(24);
  clearTimeout(typeTimer);
  /* Le texte part, **puis** OK valide : l'ordre compte, valider avant l'envoi
     lancerait la recherche sur ce que le champ contenait encore. */
  fetch(A+'/text',{method:'POST',body:'t='+encodeURIComponent(t.value)})
    .then(function(){ send('OK'); }).catch(function(){});
});

/* --- Ce que le téléviseur raconte de lui ---------------------------------
   Un seul relevé apporte la lecture en cours *et* le champ qui attend une
   saisie. Deux requêtes auraient doublé le réveil radio et permis d'afficher un
   clavier pour un champ déjà refermé.

   La page vaut pour les téléphones sans l'application — un iPhone, notamment,
   n'a pas d'autre télécommande. Elle offre donc les mêmes fonctions que l'écran
   natif, aux limites du navigateur près (pas de vibreur sur iOS, et le clavier
   ne s'ouvre pas sans un geste de l'utilisateur). */
var np=document.getElementById('np');
var npart=document.getElementById('npart'), nptitle=document.getElementById('nptitle');
var npsub=document.getElementById('npsub'), npfill=document.getElementById('npfill');
var npdot=document.getElementById('npdot'), nppos=document.getElementById('nppos');
var npleft=document.getElementById('npleft'), bar=document.getElementById('npbar');
var hero=document.querySelector('.rk.hero');

var shown=0, dur=0, playing=false, trustAfter=0, dragging=false, silences=0;
var fieldLabel=null, autoOpened=false;

function pad2(n){ return n<10 ? '0'+n : ''+n; }
function fmt(ms){
  var s=Math.max(0,Math.floor(ms/1000));
  var h=Math.floor(s/3600), m=Math.floor((s%3600)/60), q=s%60;
  return h>0 ? h+':'+pad2(m)+':'+pad2(q) : m+':'+pad2(q);
}

function paint(){
  var f = dur>0 ? Math.max(0,Math.min(1,shown/dur)) : 0;
  npfill.style.width=(f*100)+'%';
  npdot.style.left=(f*100)+'%';
  nppos.textContent=fmt(shown);
  npleft.textContent = dur>0 ? '-'+fmt(dur-shown) : '';
}

/* Le clavier suit le focus du téléviseur. C'est ce qui manquait : la saisie
   existait, mais il fallait deviner qu'un champ l'attendait. On ne referme que
   ce qu'on a ouvert soi-même — refermer une saisie ouverte à la main serait
   arracher le clavier des doigts. */
function setTyping(f){
  if(!f){
    if(autoOpened){ autoOpened=false; fieldLabel=null; wrap.hidden=true; kblabel.hidden=true; }
    return;
  }
  if(f.label===fieldLabel) return;
  fieldLabel=f.label;
  t.value=f.value||'';
  kblabel.textContent=f.label||'';
  kblabel.hidden=!f.label;
  wrap.hidden=false;
  autoOpened=true;
  /* iOS n'ouvre pas son clavier sans geste de l'utilisateur : le champ apparaît
     et attend un appui. C'est déjà tout ce qui manquait — savoir qu'il est là. */
  try{ t.focus(); }catch(e){}
}

function apply(s){
  var n = s ? s.now : null;
  if(n){
    np.hidden=false;
    nptitle.textContent=n.title||'';
    npsub.textContent=n.subtitle||'';
    if(n.artwork){ if(npart.getAttribute('src')!==n.artwork) npart.src=n.artwork; npart.hidden=false; }
    else { npart.hidden=true; }
    dur=n.durationMs||0;
    playing=!!n.playing;
    hero.classList.toggle('playing',playing);
    /* Un saut met un aller-retour à se voir : le relevé qui suit rend encore
       l'ancienne position, et la barre revenait en arrière sous le doigt. */
    if(!dragging && Date.now()>=trustAfter) shown=n.positionMs||0;
    paint();
  } else {
    np.hidden=true; playing=false; dur=0;
    hero.classList.remove('playing');
  }
  setTyping(s && s.typing ? s.typing : null);
}

/* Un silence isolé ne prouve rien — un paquet perdu suffit — et effacer dessus
   faisait clignoter le panneau. Il en faut trois d'affilée. */
function poll(){
  fetch(A+'/state').then(function(r){
    if(!r.ok) throw 0;
    return r.json();
  }).then(function(s){
    silences=0; apply(s);
  }, function(){
    silences++;
    if(silences>=3){ np.hidden=true; playing=false; hero.classList.remove('playing'); setTyping(null); }
  }).then(function(){
    setTimeout(poll, playing?1000:2000);
  });
}
poll();

/* Entre deux relevés, la barre avance seule : sans cela elle sauterait d'une
   seconde entière à chaque réponse, ce qui se voit comme un à-coup. */
setInterval(function(){ if(playing && !dragging && dur>0){ shown+=200; paint(); } },200);

/* Bascule affichée avant confirmation : attendre le relevé ferait un bouton qui
   met une seconde à réagir, donc un bouton sur lequel on appuie deux fois. */
hero.addEventListener('click',function(){
  playing=!playing; hero.classList.toggle('playing',playing);
});

/* La barre se prend au doigt. Un appui simple produit aussi pointerdown puis
   pointerup : il se traite donc comme un glissement de longueur nulle, sans
   détecteur séparé. */
function msAt(e){
  var r=bar.getBoundingClientRect();
  var f=Math.max(0,Math.min(1,(e.clientX-r.left)/r.width));
  return Math.round(f*dur);
}
bar.addEventListener('pointerdown',function(e){
  if(dur<=0) return;
  dragging=true; bar.classList.add('drag');
  try{ bar.setPointerCapture(e.pointerId); }catch(err){}
  shown=msAt(e); paint();
});
bar.addEventListener('pointermove',function(e){
  if(!dragging) return;
  shown=msAt(e); paint();
});
['pointerup','pointercancel','lostpointercapture'].forEach(function(ev){
  bar.addEventListener(ev,function(){
    if(!dragging) return;
    dragging=false; bar.classList.remove('drag');
    trustAfter=Date.now()+1500;
    buzz(12);
    fetch(A+'/seek',{method:'POST',body:'p='+shown}).catch(function(){});
  });
});

/* Dit ce qui manque plutôt que de laisser douter du téléphone. */
if (!haptics) document.getElementById('nohap').hidden=false;
</script>
    """.trim(),
)

/* Icônes tracées à la main plutôt qu'importées : la page ne charge aucune
   ressource externe (voir [pairingPage]), et une police d'icônes ou un sprite
   romprait cette autonomie pour huit symboles. */
private const val CHEVRON_UP =
    """<svg viewBox="0 0 24 24"><path d="M5 15l7-7 7 7"/></svg>"""
private const val CHEVRON_DOWN =
    """<svg viewBox="0 0 24 24"><path d="M5 9l7 7 7-7"/></svg>"""
private const val CHEVRON_LEFT =
    """<svg viewBox="0 0 24 24"><path d="M15 5l-7 7 7 7"/></svg>"""
private const val CHEVRON_RIGHT =
    """<svg viewBox="0 0 24 24"><path d="M9 5l7 7-7 7"/></svg>"""
private const val ICON_REWIND =
    """<svg viewBox="0 0 24 24"><path d="M11 6l-6 6 6 6M19 6l-6 6 6 6"/></svg>"""
private const val ICON_FORWARD =
    """<svg viewBox="0 0 24 24"><path d="M13 6l6 6-6 6M5 6l6 6-6 6"/></svg>"""
private const val ICON_PLAY =
    """<svg viewBox="0 0 24 24"><path d="M8 5l10 7-10 7z" fill="currentColor" stroke="none"/></svg>"""
private const val ICON_PAUSE =
    """<svg viewBox="0 0 24 24"><path d="M8 5h3v14H8zM13 5h3v14h-3z" fill="currentColor" stroke="none"/></svg>"""
private const val ICON_BACK =
    """<svg viewBox="0 0 24 24"><path d="M10 8L5 12l5 4M5 12h9a5 5 0 010 10h-2"/></svg>"""
private const val ICON_KEYBOARD =
    """<svg viewBox="0 0 24 24"><rect x="2.5" y="6" width="19" height="12" rx="2"/><path d="M7 10h.01M11 10h.01M15 10h.01M8 14h8"/></svg>"""

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
   Une télécommande, pas un formulaire. Le reste du site garde les angles droits
   de `MoovieShape` ; ici tout est rond, parce que l'objet imité l'est et que le
   pouce vise un disque sans regarder. C'est la seule page qui s'écarte de
   l'identité, et elle le fait pour ressembler à autre chose qu'à une page.

   Les touches n'ont plus de texte. Une étiquette sous chaque icône rendrait la
   croix plus haute que l'écran, et on ne lit pas les libellés d'une
   télécommande : on reconnaît des formes et des positions. Les noms restent en
   `aria-label`, seule chose qu'un lecteur d'écran ait à annoncer. */
.remote{max-width:340px;margin:8px auto 0;
        display:flex;flex-direction:column;align-items:center;gap:22px}

/* Le disque directionnel : quatre secteurs découpés dans un même cercle, et non
   quatre boutons posés côte à côte. Le pouce tombe alors *forcément* sur une
   direction, y compris entre deux flèches — sur une grille, cet entre-deux ne
   déclenche rien et se ressent comme une touche qui a raté. */
/* `overflow:hidden` n'est pas décoratif : les secteurs sont découpés en
   triangles qui vont jusqu'aux **coins du carré**, pas au bord du cercle. Sans
   lui, la lueur d'un appui débordait du disque en un halo carré. */
.dpad{position:relative;overflow:hidden;width:min(78vw,290px);aspect-ratio:1;border-radius:50%;
      background:
        radial-gradient(circle at 50% 32%,rgba(255,255,255,.07),transparent 60%),
        #16161c;
      box-shadow:inset 0 1px 0 rgba(255,255,255,.07),
                 inset 0 -14px 26px rgba(0,0,0,.55),
                 0 18px 34px rgba(0,0,0,.5);
      /* `none` et non `manipulation` : le doigt tourne sur le disque sans
         relâcher, et le navigateur ne doit ni faire défiler la page ni
         interpréter le geste comme un balayage. */
      touch-action:none;-webkit-tap-highlight-color:transparent}
/* `pointer-events:none` : c'est le disque qui suit le geste, pas les secteurs.
   Ils restent de vrais boutons — focalisables, activables au clavier et par un
   lecteur d'écran, qui passent par `click` et non par le pointeur. */
.sec{position:absolute;inset:0;margin:0;padding:0;width:100%;height:100%;
     background:transparent;border:0;cursor:pointer;color:#cfcfd6;
     display:flex;align-items:center;justify-content:center;
     pointer-events:none;-webkit-tap-highlight-color:transparent;
     -webkit-appearance:none;transition:background .12s,color .12s}
.sec:focus-visible{pointer-events:auto}
/* Secteurs en triangles depuis le centre : les diagonales appartiennent à la
   direction la plus proche, comme sur une vraie croix. */
.sec.up   {clip-path:polygon(50% 50%,0 0,100% 0);        align-items:flex-start;padding-top:6%}
.sec.down {clip-path:polygon(50% 50%,100% 100%,0 100%);  align-items:flex-end;padding-bottom:6%}
.sec.left {clip-path:polygon(50% 50%,0 100%,0 0);        justify-content:flex-start;padding-left:6%}
.sec.right{clip-path:polygon(50% 50%,100% 0,100% 100%);  justify-content:flex-end;padding-right:6%}
.sec svg{width:30px;height:30px}

/* Le bouton central, posé au-dessus des secteurs. Son anneau porte le dégradé
   d'identité : c'est le seul endroit de la télécommande où il apparaît, donc
   celui que l'œil trouve en premier. */
.ok{position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);
    display:flex;align-items:center;justify-content:center;
    width:38%;height:38%;margin:0;padding:0;border-radius:50%;border:0;cursor:pointer;
    background:
      linear-gradient(#1d1d24,#141419) padding-box,
      var(--gradient) border-box;
    border:2px solid transparent;
    box-shadow:0 6px 18px rgba(0,0,0,.55),inset 0 1px 0 rgba(255,255,255,.08);
    touch-action:manipulation;-webkit-tap-highlight-color:transparent;
    -webkit-appearance:none;transition:box-shadow .12s,transform .08s}

/* Les touches rondes du bas : mêmes règles, taille de doigt (56 px, au-dessus
   des 48 dp recommandés). */
.row{display:flex;justify-content:center;gap:18px}
.rk{width:56px;height:56px;margin:0;padding:0;border-radius:50%;border:0;
    display:flex;align-items:center;justify-content:center;color:#cfcfd6;
    background:radial-gradient(circle at 50% 30%,#22222a,#16161c);
    box-shadow:0 8px 16px rgba(0,0,0,.5),inset 0 1px 0 rgba(255,255,255,.07);
    cursor:pointer;touch-action:manipulation;-webkit-tap-highlight-color:transparent;
    -webkit-appearance:none;transition:box-shadow .12s,transform .08s,color .12s}
.rk.hero{width:68px;height:68px;color:#f2f2f5}
.rk svg{width:24px;height:24px}
.rk.hero svg{width:28px;height:28px}

/* Les icônes sont des tracés, pas des glyphes : une seule règle les accorde. */
.sec svg,.rk svg{fill:none;stroke:currentColor;stroke-width:2;
                 stroke-linecap:round;stroke-linejoin:round}

/* Le néon. Chaque touche a sa teinte, prise dans le dégradé d'identité, pour
   qu'un appui se reconnaisse du coin de l'œil sans lire l'icône. Il s'allume à
   la pression et sur `.lit`, que le script maintient 180 ms — un appui bref ne
   dure pas assez pour être vu autrement. */
.sec.up   {--glow:#ff9a2e}
.sec.right{--glow:#e8214e}
.sec.down {--glow:#7b3fb0}
.sec.left {--glow:#e8214e}
.rk       {--glow:#7b3fb0}
.rk.hero  {--glow:#e8214e}
/* La lueur se pose **sous la flèche**, pas au centre du disque : centrée, elle
   éclairait le point de fuite des quatre secteurs et le découpage triangulaire
   se voyait comme un coin. Décalée vers le bord, elle ressemble à une touche
   rétroéclairée. */
.sec:active,.sec.lit,.sec.on{background:radial-gradient(circle at var(--gx) var(--gy),
            color-mix(in srgb,var(--glow) 30%,transparent),transparent 42%);
            color:#fff}
.sec.up{--gx:50%;--gy:16%}
.sec.down{--gx:50%;--gy:84%}
.sec.left{--gx:16%;--gy:50%}
.sec.right{--gx:84%;--gy:50%}
.rk:active,.rk.lit{color:#fff;transform:translateY(1px);
            box-shadow:0 0 0 1px color-mix(in srgb,var(--glow) 60%,transparent),
                       0 0 22px color-mix(in srgb,var(--glow) 70%,transparent),
                       inset 0 0 14px color-mix(in srgb,var(--glow) 30%,transparent)}
.ok:active,.ok.lit{transform:translate(-50%,-50%) scale(.96);
            box-shadow:0 0 26px rgba(232,33,78,.55),0 0 46px rgba(123,63,176,.4),
                       inset 0 0 18px rgba(255,255,255,.1)}

/* Repli pour les navigateurs sans `color-mix` : le néon devient une lueur
   blanche. Moins joli, mais un appui reste visible — c'est ce qui compte. */
@supports not (color:color-mix(in srgb,red 50%,transparent)){
  .sec:active,.sec.lit,.sec.on{background:radial-gradient(circle at var(--gx) var(--gy),
                                  rgba(255,255,255,.16),transparent 42%)}
  .rk:active,.rk.lit{box-shadow:0 0 22px rgba(255,255,255,.35),
                                inset 0 0 14px rgba(255,255,255,.12)}
}

/* En-tête réduit à une marque : la page imite un objet, pas un document. Un
   titre reste nécessaire — c'est le seul repère annoncé à l'ouverture, et le
   seul <h1> de la page. */
.brand{font-size:.9rem;font-weight:700;letter-spacing:.16em;text-transform:uppercase;
       text-align:center;color:var(--muted);margin:0 0 10px}
.brandrule{width:56px;margin:0 auto 24px;height:2px}

/* Pastille centrale : un anneau vide se lit comme un trou dans le disque, pas
   comme une touche. Elle s'allume avec le reste. */
.ok i{display:block;width:18%;height:18%;min-width:9px;min-height:9px;
      margin:auto;border-radius:50%;background:#5a5a66;transition:background .12s}
.ok:active i,.ok.lit i{background:#fff}

.nohap{text-align:center;font-size:.82rem;margin:18px 0 0}
.kbwrap{width:100%;margin-top:2px}
.kbwrap input{border-radius:28px;text-align:center;border-color:#3a3a44}
.kblabel{margin:0 0 6px;text-align:center;font-size:13px;color:#9a9a9a}
/* Mini-lecteur : ce qui passe sur le téléviseur. Mêmes règles que l'écran
   natif — jaquette, titre, barre prenable au doigt, et temps restant plutôt que
   durée, parce que c'est la question qu'on se pose devant un film. */
.np{width:100%;margin:0 0 22px}
.npmeta{display:flex;gap:14px;align-items:center}
.npart{width:60px;height:90px;object-fit:cover;border-radius:10px;flex:none;background:#1d1d24}
.nptxt{min-width:0}
.nptitle{font-size:17px;font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.npsub{font-size:13px;color:#9a9a9a;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
/* 32 px de haut pour un trait de 4 : viser une ligne de quatre pixels avec un
   pouce est impossible, et l'épaissir en ferait une barre de lecteur de salon. */
.npbar{position:relative;height:32px;margin-top:10px;touch-action:none;cursor:pointer}
.npbar s{position:absolute;top:14px;left:0;right:0;height:4px;background:#33333b;border-radius:2px}
.npbar i{position:absolute;top:14px;left:0;width:0;height:4px;background:var(--gradient);border-radius:2px}
.npbar b{position:absolute;top:8px;left:0;width:16px;height:16px;margin-left:-8px;
         border-radius:50%;background:#fff;display:none}
.npbar.drag b{display:block}
.nptime{display:flex;justify-content:space-between;font-size:12px;color:#9a9a9a;margin-top:2px}
/* Le bouton central porte les deux icônes et n'en montre qu'une : échanger du
   HTML à chaque relevé referait le calque et ferait clignoter le néon. */
.rk.hero .ip,.rk.hero .ia{align-items:center;justify-content:center}
.rk.hero .ip{display:flex}
.rk.hero .ia{display:none}
.rk.hero.playing .ip{display:none}
.rk.hero.playing .ia{display:flex}
.foot{margin-top:30px;text-align:center}
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
