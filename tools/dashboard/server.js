#!/usr/bin/env node
/**
 * Tableau de bord des sources — surveille ce qui tombe, et le dit.
 *
 * ## Ce qu'il fait, et ce qu'il ne fait surtout pas
 *
 * Il ne sait **rien** des sources. Il ne parse aucun site, ne connaît aucun
 * hébergeur, n'a aucune règle d'extraction. Il lance les sondes du projet — du
 * vrai code de l'application — et lit le JSON qu'elles déposent.
 *
 * C'est la seule architecture qui ait un sens : réécrire les scrapers en Node
 * reviendrait à surveiller un second logiciel, qui divergerait du premier dès la
 * première rotation de domaine, et rassurerait pendant que l'application est en
 * panne.
 *
 * ## Pourquoi il tourne chez vous et pas en CI
 *
 * `tools/check-sources.sh` le dit déjà : l'IP d'un runner GitHub n'est pas votre
 * salon. Plusieurs hébergeurs bloquent les plages de datacenter — vidzy répond
 * 403 à tout ce qui ne lui plaît pas, wiflix rend « Bot shield active » — et un
 * relevé pris là-bas déclarerait morte la moitié du catalogue en marchant très
 * bien chez vous. Un moniteur qui crie au loup ne se lit plus au bout d'une
 * semaine.
 *
 * ## Ce qui déclenche une alerte
 *
 * **La bascule, jamais l'état.** « vidzy est mort » n'est pas une nouvelle si
 * c'était déjà vrai hier ; « vidzy est tombé cette nuit » en est une. Le premier
 * relevé n'alerte donc sur rien : sans point de comparaison, il ne peut établir
 * aucune bascule, et alerter sur l'état enverrait dix-huit notifications d'un
 * coup pour des hébergeurs morts depuis des mois.
 *
 * Les retours en vie sont signalés aussi : c'est ce qui dit de retirer un nom de
 * `UNSUPPORTED_HOSTERS`.
 *
 *   node tools/dashboard/server.js
 */

const http = require('node:http');
const { spawn } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');

const ICI = __dirname;
const RACINE = path.resolve(ICI, '..', '..');
const HISTORIQUE = path.join(RACINE, 'tools', 'reports', 'history.jsonl');

const config = lisConfig();

function lisConfig() {
  const defauts = {
    port: 7788,
    intervalHours: 6,
    runOnStart: true,
    webhookUrl: '',
    gradle: './gradlew',
  };
  for (const nom of ['config.json', 'config.example.json']) {
    const f = path.join(ICI, nom);
    if (!fs.existsSync(f)) continue;
    try {
      return { ...defauts, ...JSON.parse(fs.readFileSync(f, 'utf8')) };
    } catch (e) {
      console.error(`[config] ${nom} illisible : ${e.message}`);
    }
  }
  return defauts;
}

// ── Le relevé ───────────────────────────────────────────────────────────────

let enCours = false;

/**
 * Lance les deux sondes et rend un instantané.
 *
 * Le dossier de rapport est temporaire et neuf à chaque fois : un relevé qui
 * échouerait à mi-parcours laisserait sinon le fichier du relevé précédent, et
 * on conclurait « rien n'a changé » sur des données périmées — le pire des
 * résultats, puisqu'il est rassurant.
 */
function releve() {
  return new Promise((resolve) => {
    if (enCours) return resolve({ error: 'un relevé est déjà en cours' });
    enCours = true;

    const sortie = fs.mkdtempSync(path.join(os.tmpdir(), 'moovie-report-'));
    const args = [
      ':app:desktopTest',
      '--tests', '*HosterHealthProbeTest',
      '--tests', '*ProviderHealthProbeTest',
      '--tests', '*QuickCoverageProbeTest',
      '-Dmoovie.probe=1',
      `-Dmoovie.report=${sortie}`,
      '--rerun-tasks',
    ];
    console.log(`[relevé] ${new Date().toISOString()} — démarrage…`);

    const p = spawn(config.gradle, args, { cwd: RACINE });
    let journal = '';
    p.stdout.on('data', (d) => { journal += d; });
    p.stderr.on('data', (d) => { journal += d; });

    p.on('error', (e) => {
      enCours = false;
      console.error(`[relevé] impossible de lancer Gradle : ${e.message}`);
      resolve({ error: e.message });
    });

    p.on('close', () => {
      enCours = false;
      const hosters = litJson(path.join(sortie, 'hosters.json'));
      const providers = litJson(path.join(sortie, 'providers.json'));
      const coverage = litJson(path.join(sortie, 'coverage.json'));
      fs.rmSync(sortie, { recursive: true, force: true });

      if (!hosters && !providers) {
        // Gradle a pu « réussir » sans que les sondes écrivent : sans rapport,
        // il n'y a pas de relevé, et surtout pas un relevé vide — qu'on
        // interpréterait comme « tout est mort ».
        const queue = journal.split('\n').slice(-15).join('\n');
        console.error(`[relevé] aucun rapport produit.\n${queue}`);
        return resolve({ error: 'aucun rapport produit' });
      }

      const instantane = {
        at: new Date().toISOString(),
        hosters: hosters?.hosters ?? [],
        providers: providers?.providers ?? [],
        coverage: coverage ? { covered: coverage.covered, total: coverage.total } : null,
      };
      const precedent = dernierInstantane();
      ajoute(instantane);

      const bascules = compare(precedent, instantane);
      if (precedent && bascules.length) previens(bascules, instantane);
      else if (!precedent) console.log('[relevé] premier relevé : référence posée, aucune alerte.');

      console.log(
        `[relevé] terminé — ${instantane.hosters.filter((h) => h.alive).length}/` +
        `${instantane.hosters.length} hébergeurs vivants, ${bascules.length} bascule(s)`,
      );
      resolve({ snapshot: instantane, changes: bascules });
    });
  });
}

function litJson(f) {
  try {
    return JSON.parse(fs.readFileSync(f, 'utf8'));
  } catch {
    return null;
  }
}

// ── L'historique ────────────────────────────────────────────────────────────

function ajoute(instantane) {
  fs.mkdirSync(path.dirname(HISTORIQUE), { recursive: true });
  fs.appendFileSync(HISTORIQUE, JSON.stringify(instantane) + '\n');
}

function litHistorique() {
  if (!fs.existsSync(HISTORIQUE)) return [];
  return fs.readFileSync(HISTORIQUE, 'utf8')
    .split('\n')
    .filter(Boolean)
    .map((l) => { try { return JSON.parse(l); } catch { return null; } })
    .filter(Boolean);
}

function dernierInstantane() {
  const h = litHistorique();
  return h.length ? h[h.length - 1] : null;
}

/**
 * La forme d'un relevé, déduite des champs qu'il porte.
 *
 * Sert à reconnaître qu'on a changé de **mesure** entre deux passages. Rien
 * n'est persisté : la signature se recalcule depuis les données, ce qui la rend
 * valable aussi sur les relevés écrits avant qu'elle existe.
 *
 * `null` sur une liste vide — il n'y a alors rien à comparer de toute façon, et
 * la traiter comme une forme à part suspendrait des comparaisons parfaitement
 * valides sur l'autre moitié du relevé.
 */
function signatureDe(liste) {
  return liste?.length ? Object.keys(liste[0]).sort().join(',') : null;
}

/**
 * Les bascules entre deux relevés.
 *
 * Un nom **absent** d'un des deux relevés n'est pas une bascule : les
 * catalogues ne rendent pas exactement les mêmes hébergeurs à chaque passage, et
 * traiter une absence comme une mort ferait alerter au gré des humeurs des
 * catalogues. Même leçon que les pierres tombales de la synchronisation : une
 * absence n'est pas une décision.
 *
 * ## Un changement de mesure n'est pas un changement du monde
 *
 * Le 24/08/2026, le relevé des catalogues est passé d'une sonde qui ne comptait
 * que la VF à une sonde sans filtre de langue. `vidapi` ne sert que de la
 * version originale : il lisait zéro depuis toujours, et il a suffi de changer
 * l'instrument pour qu'il paraisse **ressusciter**. Une notification est partie,
 * pour un événement qui n'a jamais eu lieu.
 *
 * On compare donc la forme des deux relevés, par nature — hébergeurs et
 * catalogues séparément, l'un pouvant changer sans l'autre — et on suspend la
 * comparaison quand elle diffère. Perdre une bascule réelle ce jour-là est sans
 * gravité : le passage suivant la verra. Crier au loup, non : un moniteur qui
 * annonce de faux événements cesse d'être lu, et ne sert alors plus à rien.
 */
function compare(avant, apres) {
  if (!avant) return [];
  const bascules = [];
  for (const genre of ['hosters', 'providers']) {
    const forme = signatureDe(avant[genre]);
    const formeApres = signatureDe(apres[genre]);
    if (forme && formeApres && forme !== formeApres) {
      console.log(
        `[relevé] la forme du relevé « ${genre} » a changé — comparaison suspendue ` +
        'pour ce passage (voir compare)',
      );
      continue;
    }
    const ancien = new Map((avant[genre] ?? []).map((x) => [x.name, x.alive]));
    for (const item of apres[genre] ?? []) {
      if (!ancien.has(item.name)) continue;
      const etait = ancien.get(item.name);
      if (etait !== item.alive) {
        bascules.push({ kind: genre === 'hosters' ? 'hébergeur' : 'catalogue', name: item.name, alive: item.alive });
      }
    }
  }
  return bascules;
}

// ── L'alerte ────────────────────────────────────────────────────────────────

function previens(bascules, instantane) {
  if (!config.webhookUrl) {
    console.log('[alerte] aucun webhook configuré — bascules non notifiées');
    return;
  }
  const tombes = bascules.filter((b) => !b.alive);
  const revenus = bascules.filter((b) => b.alive);
  const lignes = [];
  if (tombes.length) lignes.push(`🔴 **Tombé** : ${tombes.map((b) => `${b.name} (${b.kind})`).join(', ')}`);
  if (revenus.length) lignes.push(`🟢 **De retour** : ${revenus.map((b) => `${b.name} (${b.kind})`).join(', ')}`);
  const vivants = instantane.hosters.filter((h) => h.alive).length;
  lignes.push(`​\nHébergeurs vivants : **${vivants}/${instantane.hosters.length}**`);
  if (instantane.coverage) {
    lignes.push(`Couverture : **${instantane.coverage.covered}/${instantane.coverage.total}** titres`);
  }

  const corps = JSON.stringify({
    username: 'Moo-vie · sources',
    content: lignes.join('\n'),
  });

  const req = require('node:https').request(
    config.webhookUrl,
    { method: 'POST', headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(corps) } },
    (res) => {
      // Discord répond 204 sans corps quand tout va bien.
      if (res.statusCode >= 200 && res.statusCode < 300) console.log('[alerte] envoyée');
      else console.error(`[alerte] refusée : HTTP ${res.statusCode}`);
      res.resume();
    },
  );
  // Une alerte qui échoue ne doit pas tuer le service : le relevé, lui, est
  // écrit, et c'est lui qui porte l'information.
  req.on('error', (e) => console.error(`[alerte] échec réseau : ${e.message}`));
  req.end(corps);
}

// ── Le serveur ──────────────────────────────────────────────────────────────

const serveur = http.createServer((req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);

  if (url.pathname === '/api/history') {
    return json(res, litHistorique());
  }
  if (url.pathname === '/api/run' && req.method === 'POST') {
    releve().then((r) => json(res, r));
    return;
  }
  if (url.pathname === '/api/status') {
    return json(res, { running: enCours, intervalHours: config.intervalHours, alerting: Boolean(config.webhookUrl) });
  }

  const fichier = url.pathname === '/' ? 'index.html' : url.pathname.replace(/^\//, '');
  const chemin = path.join(ICI, 'public', fichier);
  // Ne jamais sortir de public/ : ce serveur écoute sur la machine où vit le
  // keystore, et un `../` bien placé lirait n'importe quel fichier.
  if (!chemin.startsWith(path.join(ICI, 'public')) || !fs.existsSync(chemin)) {
    res.writeHead(404).end('introuvable');
    return;
  }
  const type = fichier.endsWith('.html') ? 'text/html; charset=utf-8' : 'text/plain; charset=utf-8';
  res.writeHead(200, { 'Content-Type': type }).end(fs.readFileSync(chemin));
});

function json(res, data) {
  res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' }).end(JSON.stringify(data));
}

// Démarré seulement en exécution directe : requis comme module — pour vérifier
// `compare` sur l'historique réel, par exemple — il ne doit pas ouvrir de port.
if (require.main === module) {
serveur.listen(config.port, '127.0.0.1', () => {
  console.log(`Moo-vie · sources — http://127.0.0.1:${config.port}`);
  console.log(`  relevé toutes les ${config.intervalHours} h · alerte ${config.webhookUrl ? 'activée' : 'désactivée'}`);
  if (config.runOnStart) releve();
  setInterval(releve, Math.max(1, config.intervalHours) * 3600 * 1000);
});
}

module.exports = { compare, signatureDe, litHistorique };
