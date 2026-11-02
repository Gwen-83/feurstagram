# Déploiement de mises à jour Feurstagram

Ce document décrit la procédure correcte pour envoyer une nouvelle version de Feurstagram à un téléphone déjà installé, sans désinstaller l’application.

## 1. Règle d’or : garder la même clé de signature

Android n’accepte une mise à jour que si le nouveau APK est signé avec la même clé que l’application déjà installée.

- même package Android
- même keystore
- même alias
- même clé de signature

Si la clé change, Android verra une autre application et la mise à jour ne s’appliquera pas correctement.

## 2. Store et alias requis

Le projet attend les variables d’environnement suivantes :

```bash
export FEURSTAGRAM_KEYSTORE=/home/gwenael/Documents/code/FeurStagram/feurstagram.keystore
export FEURSTAGRAM_KEYSTORE_PASS='S7!mQ4#vK8@pL2$R'
export FEURSTAGRAM_KEY_PASS='S7!mQ4#vK8@pL2$R'
export FEURSTAGRAM_KEY_ALIAS='feurstagram'
```

> Ne jamais committer le keystore ni les vrais mots de passe. Ce bloc est un exemple de format, pas la vraie valeur de production.

## 3. Générer ou retrouver la clé

### Si la clé n’existe pas encore

```bash
cd /home/gwenael/Documents/code/FeurStagram

export FEURSTAGRAM_KEYSTORE_PASS='S7!mQ4#vK8@pL2$R'
export FEURSTAGRAM_KEY_PASS='S7!mQ4#vK8@pL2$R'

keytool -genkeypair \
  -v \
  -keystore feurstagram.keystore \
  -alias feurstagram \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass "$FEURSTAGRAM_KEYSTORE_PASS" \
  -keypass "$FEURSTAGRAM_KEY_PASS" \
  -dname "CN=FeurStagram, OU=App, O=FeurStagram, L=Paris, ST=France, C=FR"
```

### Si la clé existe déjà

Il faut la réutiliser exactement, sans la remplacer.

```bash
keytool -list -v -keystore feurstagram.keystore -storepass "$FEURSTAGRAM_KEYSTORE_PASS"
```

## 4. Construire la nouvelle release

Le build officiel utilise le script [build.sh](../../build.sh) :

```bash
cd /home/gwenael/Documents/code/FeurStagram

export FEURSTAGRAM_KEYSTORE=/home/gwenael/Documents/code/FeurStagram/feurstagram.keystore
export FEURSTAGRAM_KEYSTORE_PASS='S7!mQ4#vK8@pL2$R'
export FEURSTAGRAM_KEY_PASS='S7!mQ4#vK8@pL2$R'
export FEURSTAGRAM_KEY_ALIAS='feurstagram'

./build.sh build/inputs/instagram-454.apkm
```

Le fichier APK produit se trouve dans :

```text
build/outputs/feurstagram.apk
```

## 5. Publier le fichier APK sur un serveur HTTPS

Le fichier APK doit être accessible publiquement sur HTTPS.

Exemple de structure :

```text
https://example.com/updates/
├── feurstagram-release.apk
├── updates.json
```

Le manifeste doit ressembler à ceci :

```json
{
  "version": "1.0.1",
  "apkUrl": "https://example.com/updates/feurstagram-release.apk",
  "notes": "Version 1.0.1 - correction de bugs, suppression des popups, amélioration du flux de mise à jour."
}
```

## 6. Configurer l’URL d’update dans l’application

L’application lit cette URL depuis le code de [UpdateManager.java](../../extensions/extension/src/main/java/com/feurstagram/extension/UpdateManager.java).

Exemple de valeur :

```text
https://example.com/updates/updates.json
```

Tu peux la définir via les réglages de l’application, ou via le code si tu veux l’inscrire en dur pour un environnement de test :

```java
UpdateManager.setUpdateUrl("https://example.com/updates/updates.json");
```

## 7. Vérification sur le téléphone

Sur le téléphone ciblé :

1. ouvrir l’application Feurstagram,
2. aller dans les réglages,
3. choisir “Check for updates”,
4. vérifier que la version distante est supérieure à la version locale,
5. télécharger puis installer le APK,
6. autoriser l’installation depuis des sources inconnues si nécessaire.

## 8. Checklist de sécurité

- même keystore que la version déjà installée
- même alias `feurstagram`
- APK signé avec la bonne clé
- fichier `updates.json` servi en HTTPS
- fichier APK public et accessible
- autorisation “sources inconnues” activée sur le téléphone

## 9. Cas d’erreur fréquents

### La mise à jour ne s’applique pas

Cause probable : clé différente entre l’APK produit et l’application installée.

### Le manifeste ne charge pas

Cause probable : URL incorrecte, serveur inaccessible, JSON invalide, ou fichier trop lent à répondre.

### L’installation est refusée

Cause probable : l’option “Installer des applications depuis des sources inconnues” est désactivée.

## 10. Procédure courte à suivre pour chaque release

```bash
# 1) vérifier et garder la même clé
export FEURSTAGRAM_KEYSTORE=/home/gwenael/Documents/code/FeurStagram/feurstagram.keystore
export FEURSTAGRAM_KEYSTORE_PASS='S7!mQ4#vK8@pL2$R'
export FEURSTAGRAM_KEY_PASS='S7!mQ4#vK8@pL2$R'
export FEURSTAGRAM_KEY_ALIAS='feurstagram'

# 2) build release
./build.sh build/inputs/instagram-454.apkm

# 3) upload APK + manifest vers le serveur HTTPS
# 4) mettre à jour updates.json avec la nouvelle version
# 5) configurer l’URL dans l’app
# 6) vérifier la mise à jour sur le téléphone
```

## 11. Point de vigilance pour le travail avec l’IA

Une IA ne peut pas envoyer une mise à jour tant que le serveur, l’URL du manifest, la clé de signature et l’autorisation téléphone ne sont pas en place. La logique de mise à jour est prête dans le code, mais l’environnement de publication doit être correctement configuré pour qu’un APK puisse être reçu et installé.

Une fois le serveur HTTPS et le bon keystore confirmés, le flux “check → download → install” est le bon mécanisme pour faire réceptionner la mise à jour sur le téléphone.
