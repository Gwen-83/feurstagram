# Préparer et compiler Feurstagram sous Linux

Ce guide décrit la préparation du poste, la compilation de Feurstagram et son
installation sur un téléphone Android. Les outils installés par cette session
se trouvent dans le dossier utilisateur, sans installation système :

- JDK Temurin 21 : `~/.local/opt/temurin-21`
- Android SDK : `~/Android/Sdk` (platform-tools, plateforme Android 35 et
  build-tools 35.0.0)
- Morphe CLI : `tools/morphe-desktop-1.20.0-all.jar`

Le JDK et le SDK Android ont été téléchargés depuis Eclipse Adoptium et Google.
Le Morphe CLI vient de la page officielle
[Morphe Desktop Releases](https://github.com/MorpheApp/morphe-desktop/releases)
et son SHA-256 `679f579b3ad743ac0ca697ef71e57430d00be59411b04494afd1845c43fb79f0`
a été vérifié après téléchargement.

## 1. Configurer l'environnement du terminal

À exécuter dans chaque nouveau terminal. Pour rendre ces variables persistantes,
ajoute ces lignes à `~/.bashrc`, puis exécute `source ~/.bashrc`.

```bash
export JAVA_HOME="$HOME/.local/opt/temurin-21"
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
```

Vérifie les outils :

```bash
java -version
adb version
"$ANDROID_HOME/build-tools/35.0.0/apksigner" version
```

La version de Java doit être 21.

## 2. Dépendances locales

Le script lance Gradle en mode hors ligne : il n'essaie pas de récupérer des
plugins ou bibliothèques. Sur ce poste préparé, les dépendances nécessaires sont
déjà dans le cache Gradle local. Si une dépendance manque, le build s'arrête avec
une erreur au lieu de la télécharger. Aucun jeton GitHub n'est nécessaire pour
la compilation sur ce poste.

## 3. Préparer l'APK Instagram

Le projet modifie un APK existant ; il ne fournit pas Instagram et ne demande
pas ton mot de passe Instagram pour compiler. Récupère un APK compatible
`arm64-v8a` d'une source en laquelle tu as confiance et place-le dans
`build/inputs/` (crée ce dossier si nécessaire), par exemple sous le nom
`instagram.apkm`.

Un APK simple peut être compilé directement. Pour un bundle `.apkm`, `.xapk`
ou `.apks`, le script a aussi besoin d'APKEditor dans `tools/` ; suis la
documentation de préparation des outils locaux s'il signale que cet outil
manque.

## 4. Compiler et installer par USB

Active les options développeur et le débogage USB sur le téléphone, branche-le,
déverrouille-le et accepte l'autorisation de débogage affichée. Dans le
terminal configuré à l'étape 1 :

```bash
cd ~/Documents/code/FeurStagram
adb devices
./build.sh build/inputs/instagram.apkm --clone --install
```

`--clone` installe Feurstagram à côté de l'application Instagram officielle.
Si `adb devices` affiche `unauthorized`, accepte la demande sur le téléphone,
puis relance la commande.

Pour compiler sans installer tout de suite :

```bash
./build.sh build/inputs/instagram.apkm --clone
adb install -r build/outputs/feurstagram.apk
```

Les APK d'entrée restent dans `build/inputs/`, les fichiers intermédiaires dans
`build/intermediates/`, le rapport dans `build/reports/` et l'APK compilé dans
`build/outputs/feurstagram.apk`. Ces fichiers locaux sont ignorés par Git.
N'utilise pas `--debug` pour une installation quotidienne : cette option active
une interface de débogage réservée au développement.

## Dépannage

- **Plugin Morphe introuvable** : vérifie que le jeton est dans
  `~/.gradle/gradle.properties`, qu'il n'y a pas de faute de frappe et que son
  compte a accès aux packages Morphe.
- **Morphe CLI introuvable** : vérifie la présence de
  `tools/morphe-desktop-1.20.0-all.jar`.
- **Appareil absent de `adb devices`** : vérifie le câble USB, le mode de
  connexion et l'autorisation de débogage USB sur le téléphone.
- **Signature** : sans mot de passe de keystore configuré, le script utilise
  une clé temporaire, adaptée aux tests mais pas aux mises à jour d'une
  installation signée avec une autre clé.

## Données et confidentialité

Les téléchargements du JDK, du SDK Android et du CLI ont été faits depuis les
sources officielles mentionnées ci-dessus. Lors de l'installation, Android CLI
a affiché un avis sur la collecte de métriques d'utilisation. Selon Google,
celles-ci comprennent les commandes et options utilisées, mais pas les réponses
de l'outil ni les valeurs de paramètres personnalisés ou chemins locaux. Pour
désactiver cette collecte lors de ses prochaines utilisations, ajoute cette
ligne à `~/.androidrc` :

```text
--no-metrics
```

Le build Feurstagram utilise Gradle, `adb` et `apksigner` ; il n'a pas besoin
d'exécuter Android CLI. Gradle fonctionne hors ligne avec le cache préexistant.

La compilation ne requiert pas tes identifiants Instagram. L'application
Instagram modifiée continue toutefois de communiquer avec Meta comme client
Instagram ; Feurstagram ne remplace pas ces communications par un proxy.
