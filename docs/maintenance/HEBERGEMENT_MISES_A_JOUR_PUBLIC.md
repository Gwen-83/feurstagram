# Hébergement public des mises à jour Feurstagram

Cette procédure permet de publier la mise à jour Android sur un endpoint HTTPS public, accessible depuis n’importe quel téléphone, même hors Wi‑Fi.

## 1. Principe

Le téléphone ne télécharge pas directement l’APK depuis le code. Il lit d’abord un fichier JSON public, par exemple :

```text
https://updates.example.com/updates.json
```

Puis il télécharge l’APK depuis :

```text
https://updates.example.com/feurstagram-release.apk
```

Le JSON doit contenir :

```json
{
  "version": "1.0.2",
  "apkUrl": "https://updates.example.com/feurstagram-release.apk",
  "notes": "Version 1.0.2 - mise à jour publique."
}
```

## 2. Déploiement recommandé

### Option A — Cloudflare R2 + domaine public

C’est le meilleur choix pour un APK :

- stockage d’objets public
- HTTPS automatique
- débit stable pour les téléchargements
- adapté aux mises à jour Android

Étapes :

1. créer un bucket R2
2. activer l’accès public sur les objets
3. rendre les fichiers disponibles via un domaine ou un endpoint public
4. upload du bundle APK + JSON
5. pointer l’URL update dans l’application vers le JSON public

### Option B — Render

Adapté à un setup rapide de fichiers statiques, et souvent préférable à Cloudflare quand le fichier APK est volumineux ou quand on veut un hébergement simple sans quota d’objets.

Étapes détaillées :

1. créer un compte Render et un nouveau service “Static Site”
2. brancher le dépôt Git ou le dossier contenant les fichiers de publication
3. mettre le dossier de sortie sur `public/` (ou le dossier qui contient `updates.json` et `feurstagram-release.apk`)
4. ne pas utiliser de rewrite global vers `/index.html` ; les URLs directes `/updates.json` et `/feurstagram-release.apk` doivent rester accessibles
5. attendre un domaine HTTPS public du type `https://feurstagram-updates.onrender.com`
6. utiliser exactement cette URL comme base de publication pour le manifest

Exemple de structure de fichiers à publier :

```text
public/
├── updates.json
├── feurstagram-release.apk
```

Le fichier `updates.json` doit être servi à la racine du site :

```text
https://feurstagram-updates.onrender.com/updates.json
```

et le fichier APK pourra être téléchargé via :

```text
https://feurstagram-updates.onrender.com/feurstagram-release.apk
```

### Option C — Vercel

Possible, mais moins naturel pour un APK volumineux. Il reste viable pour un simple test ou un environnement de préprod.

## 3. Script de publication automatique

Le script [scripts/publish_update.sh](../../scripts/publish_update.sh) est maintenant prêt pour un pipeline de publication.

Exemple de commande :

```bash
export PUBLIC_UPDATE_BASE_URL='https://updates.example.com'
export UPLOAD_MODE='scp'
export UPLOAD_TARGET='deploy@server:/var/www/updates/'
./scripts/publish_update.sh build/outputs/feurstagram.apk 1.0.2 "$PUBLIC_UPDATE_BASE_URL" ./dist
```

Cela génère :

- ./dist/updates.json
- ./dist/feurstagram-release.apk

Et, selon le mode, l’envoie automatiquement sur le serveur distant.

## 4. Règles essentielles

- même keystore que la version déjà installée
- même alias Android
- APK signé avec la bonne clé
- JSON public servi en HTTPS
- fichier APK public et accessible
- même package Android

## 5. Workflow de release

```bash
# 1) garder la même clé
export FEURSTAGRAM_KEYSTORE=/home/gwenael/Documents/code/FeurStagram/feurstagram.keystore
export FEURSTAGRAM_KEYSTORE_PASS='...'
export FEURSTAGRAM_KEY_PASS='...'
export FEURSTAGRAM_KEY_ALIAS='feurstagram'

# 2) build la release
./build.sh build/inputs/instagram-454.apkm

# 3) publier la bundle publique
./scripts/publish_update.sh build/outputs/feurstagram.apk 1.0.2 https://updates.example.com ./dist

# 4) mettre à jour l’URL d’update dans l’app
# 5) vérifier sur le téléphone que le JSON est bien lu et que l’APK est bien téléchargé
```

## 6. Ce qu’il manque pour aller en prod

Il faut un vrai hébergement public et un domaine HTTPS. L’environnement local ne suffit pas pour qu’un autre téléphone, hors réseau, puisse recevoir la mise à jour.

En pratique il faut :

- un compte Cloudflare / Render / Vercel
- un domaine public ou un sous-domaine
- un bucket ou site statique public
- la clé de signature conservée de version en version

## 7. Résumé

Le code est prêt pour un flux OTA complet :

- build release signé
- fichier JSON public
- APK public
- app qui lit le JSON et propose la mise à jour

Le point final qu’il reste à mettre en place est uniquement l’hébergement public réel.
