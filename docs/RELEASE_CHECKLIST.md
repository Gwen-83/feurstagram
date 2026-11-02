# Procédure de release Feurstagram vers Cloudflare

Ce document décrit la procédure complète pour chaque nouvelle version :

- construire le APK signé,
- mettre à jour le numéro de version et le changelog,
- générer le manifest OTA,
- l’envoyer directement vers Cloudflare R2,
- vérifier que le téléphone télécharge bien la mise à jour depuis le flux public Cloudflare.

Important : le flux de production est désormais Cloudflare R2. Render ne doit plus être la source principale du `updates.json`.

## 1. Version à publier

Avant de générer un build, il faut mettre à jour la version dans le code de l’application :

- `extensions/extension/src/main/java/com/feurstagram/extension/Config.java`

Exemple :

```java
public static String getAppVersionName() {
    return "1.0.8";
}

public static String getChangelogText() {
    return "test mise à jour v1.0.8\n\n"
            + "Feurstagram 1.0.8\n\n"
            + "- public Cloudflare update feed for OTA delivery\n"
            + "- fixed static hosting path for manifest and APK URLs\n"
            + "- English/French interface language selection\n"
            + "- search in settings and clearer group labels\n"
            + "- better accessibility labels for screen readers\n"
            + "- saved preferences snapshot and restore support\n"
            + "- remote update manifest and in-place install flow";
}
```

Le `version` du manifest doit correspondre exactement à cette valeur.

---

## 2. Clés et secrets à préparer

### Secrets GitHub Actions

Dans GitHub → Settings → Secrets and variables → Actions, il faut avoir ces variables :

- `R2_ACCESS_KEY_ID`
- `R2_SECRET_ACCESS_KEY`
- `R2_ENDPOINT`
- `R2_BUCKET`
- `R2_PUBLIC_BASE_URL`
- `R2_REMOTE`
- `FEURSTAGRAM_KEYSTORE_PASS`
- `FEURSTAGRAM_KEY_PASS`
- `FEURSTAGRAM_KEY_ALIAS`
- `FEURSTAGRAM_KEYSTORE_BASE64`

### Valeurs attendues

Exemple de configuration réelle utilisée par le projet :

```bash
export R2_ACCESS_KEY_ID='...'
export R2_SECRET_ACCESS_KEY='...'
export R2_ENDPOINT='https://<accountid>.r2.cloudflarestorage.com'
export R2_BUCKET='feurstagram-apk'
export R2_PUBLIC_BASE_URL='https://pub-xxxxxxxxxxxxxxxx.r2.dev'
export R2_REMOTE='r2:feurstagram-apk'

export FEURSTAGRAM_KEYSTORE_PASS='S7!mQ4#vK8@pL2$R'
export FEURSTAGRAM_KEY_PASS='S7!mQ4#vK8@pL2$R'
export FEURSTAGRAM_KEY_ALIAS='feurstagram'
```

Pour le keystore binaire :

```bash
base64 -w 0 feurstagram-release.keystore > /tmp/feurstagram-keystore.base64
```

Puis stocker la chaîne obtenue dans :

- `FEURSTAGRAM_KEYSTORE_BASE64`

---

## 3. Variables locales pour un build manuel

```bash
cd /home/gwenael/Documents/code/FeurStagram

export FEURSTAGRAM_KEYSTORE=/home/gwenael/Documents/code/FeurStagram/feurstagram-release.keystore
export FEURSTAGRAM_KEYSTORE_PASS='S7!mQ4#vK8@pL2$R'
export FEURSTAGRAM_KEY_PASS='S7!mQ4#vK8@pL2$R'
export FEURSTAGRAM_KEY_ALIAS='feurstagram'

export R2_ACCESS_KEY_ID='...'
export R2_SECRET_ACCESS_KEY='...'
export R2_ENDPOINT='https://<accountid>.r2.cloudflarestorage.com'
export R2_BUCKET='feurstagram-apk'
export R2_PUBLIC_BASE_URL='https://pub-xxxxxxxxxxxxxxxx.r2.dev'
export R2_REMOTE='r2:feurstagram-apk'
```

---

## 4. Construire le APK

```bash
./build.sh build/inputs/instagram-454.apkm
```

Le produit final est généré dans :

- `build/outputs/feurstagram.apk`

---

## 5. Préparer le `updates.json` et le binaire pour Cloudflare

On copie l’APK dans le dossier public pour le téléversement, puis on génère le manifest avec le bon nom de version.

```bash
cp -f build/outputs/feurstagram.apk public/feurstagram-release.apk
./scripts/publish_update.sh \
  build/outputs/feurstagram.apk \
  1.0.8 \
  "$R2_PUBLIC_BASE_URL" \
  public
```

Cela produit un fichier du type :

```json
{
  "version": "1.0.8",
  "apkUrl": "https://pub-xxxxxxxxxxxxxxxx.r2.dev/feurstagram-release.apk",
  "notes": "test mise à jour v1.0.8\n\nVersion 1.0.8 - mise à jour Feurstagram.\n\n- public Cloudflare update feed for OTA delivery\n- fixed static hosting path for manifest and APK URLs\n- English/French interface language selection\n- search in settings and clearer group labels\n- better accessibility labels for screen readers\n- saved preferences snapshot and restore support\n- remote update manifest and in-place install flow"
}
```

Le fichier produit est :

- `public/updates.json`

---

## 6. Envoyer vers Cloudflare R2

### Upload manuel local

```bash
rclone copy public/feurstagram-release.apk "$R2_REMOTE/"
rclone copy public/updates.json "$R2_REMOTE/"
```

### Vérification de la publication

```bash
rclone ls "$R2_REMOTE/"
curl -fsSL "$R2_PUBLIC_BASE_URL/updates.json"
```

Le manifest public doit afficher la bonne version, par exemple :

```json
{
  "version": "1.0.8",
  "apkUrl": "https://pub-xxxxxxxxxxxxxxxx.r2.dev/feurstagram-release.apk",
  "notes": "test mise à jour v1.0.8..."
}
```

---

## 7. Vérification côté application

On vérifie que le téléphone lit bien le manifest public Cloudflare et compare la version actuelle à la version distante.

Les points importants sont :

- la valeur par défaut dans `UpdateManager.java` pointe vers le Cloudflare public URL,
- le téléphone appelle `https://pub-...r2.dev/updates.json`,
- la version remote est supérieure à la version installée,
- le téléchargement de `feurstagram-release.apk` est accepté et l’install est déclenchée.

Valeur par défaut dans le code :

```java
public static final String DEFAULT_UPDATE_URL = "https://pub-6e8e77d36cc349f2a5221ebf65da0bc8.r2.dev/updates.json";
```

Le legacy Render URL est gardé seulement comme URL de secours historique, mais il ne doit plus être utilisé comme source principale.

---

## 8. Workflow GitHub Actions

Le workflow `.github/workflows/release-cloudflare.yml` automatise ce flux :

- téléchargement du SDK Android si nécessaire,
- décodage du keystore depuis `FEURSTAGRAM_KEYSTORE_BASE64`,
- build du APK signé,
- génération du manifest avec `scripts/publish_update.sh`,
- upload vers Cloudflare R2 avec `rclone`.

Pour lancer un build manuellement :

- GitHub → Actions → Release to Cloudflare R2 → Run workflow

---

## 9. Checklist de release à chaque version

1. Monter le numéro de version dans `Config.getAppVersionName()`.
2. Mettre à jour `Config.getChangelogText()`.
3. Vérifier le `public/updates.json` local.
4. Lancer le build.
5. Vérifier que `build/outputs/feurstagram.apk` est bien généré.
6. Copier le binaire dans `public/feurstagram-release.apk`.
7. Générer le manifest public via `scripts/publish_update.sh`.
8. Upload vers Cloudflare R2 avec `rclone`.
9. Vérifier `curl -fsSL "$R2_PUBLIC_BASE_URL/updates.json"`.
10. Vérifier que le téléphone reçoit la version 1.0.x avec l’APK correcte.

---

## 10. Résumé court

Le flux de production est :

```bash
./build.sh build/inputs/instagram-454.apkm
cp -f build/outputs/feurstagram.apk public/feurstagram-release.apk
./scripts/publish_update.sh build/outputs/feurstagram.apk 1.0.8 "$R2_PUBLIC_BASE_URL" public
rclone copy public/feurstagram-release.apk "$R2_REMOTE/"
rclone copy public/updates.json "$R2_REMOTE/"
curl -fsSL "$R2_PUBLIC_BASE_URL/updates.json"
```

Render ne doit plus être utilisé comme source principale pour la mise à jour OTA. Le fichier `updates.json` public sur Cloudflare est la source de vérité.
