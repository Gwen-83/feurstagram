# Publier automatiquement une version Feurstagram sur Cloudflare R2

## 1) Configurer le remote rclone

Crée un fichier de config rclone :

```bash
mkdir -p ~/.config/rclone
cat > ~/.config/rclone/rclone.conf <<'EOF'
[r2]
type = s3
provider = Cloudflare
access_key_id = <TON_ACCESS_KEY_ID>
secret_access_key = <TON_SECRET_ACCESS_KEY>
endpoint = https://<TON_ACCOUNT_ID>.r2.cloudflarestorage.com
region = auto
acl = private
no_check_bucket = true
EOF
```

Vérifie que le remote fonctionne :

```bash
rclone ls r2:feurstagram-apk
```

## 2) Générer la build signée

```bash
export FEURSTAGRAM_KEYSTORE=/home/gwenael/Documents/code/FeurStagram/feurstagram-release.keystore
export FEURSTAGRAM_KEYSTORE_PASS='...'
export FEURSTAGRAM_KEY_PASS='...'
export FEURSTAGRAM_KEY_ALIAS='feurstagram'

./build.sh build/inputs/instagram-454.apkm
cp build/outputs/feurstagram.apk public/feurstagram-release.apk
```

## 3) Mettre à jour le manifeste OTA

Le fichier public/updates.json doit pointer vers le binaire public Cloudflare :

```json
{
  "version": "1.0.4",
  "apkUrl": "https://pub-6e8e77d36cc349f2a5221ebf65da0bc8.r2.dev/feurstagram-release.apk",
  "notes": "test mise à jour\n\nVersion 1.0.4 - mise à jour Feurstagram."
}
```

Ou, en ligne de commande :

```bash
cat > public/updates.json <<'EOF'
{
  "version": "1.0.4",
  "apkUrl": "https://pub-6e8e77d36cc349f2a5221ebf65da0bc8.r2.dev/feurstagram-release.apk",
  "notes": "test mise à jour\n\nVersion 1.0.4 - mise à jour Feurstagram."
}
EOF
```

## 4) Uploader automatiquement sur Cloudflare R2

```bash
rclone copy public/feurstagram-release.apk r2:feurstagram-apk/
rclone copy public/updates.json r2:feurstagram-apk/
```

## 5) Vérifier que le fichier est bien public

```bash
curl -I -L 'https://pub-6e8e77d36cc349f2a5221ebf65da0bc8.r2.dev/feurstagram-release.apk'
curl -I -L 'https://pub-6e8e77d36cc349f2a5221ebf65da0bc8.r2.dev/updates.json'
```

Résultat attendu :

- HTTP 200 OK
- Content-Type APK pour le binaire
- Content-Type JSON pour le manifeste

## 6) Script de publication rapide

Tu peux automatiser le flux avec cette commande :

```bash
./scripts/publish_update.sh build/outputs/feurstagram.apk 1.0.4 "https://pub-6e8e77d36cc349f2a5221ebf65da0bc8.r2.dev" ./public
rclone copy ./public/feurstagram-release.apk r2:feurstagram-apk/
rclone copy ./public/updates.json r2:feurstagram-apk/
```

Note : le plus important est que l’URL publique pointe vers un fichier binaire APK brut, pas vers une page HTML ou un partage Drive.
