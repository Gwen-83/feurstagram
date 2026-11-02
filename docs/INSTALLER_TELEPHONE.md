# Installer Feurstagram sur un téléphone avec ADB

Cette procédure compile l'APK depuis les fichiers locaux du projet puis
l'installe sur un téléphone Android connecté en USB avec le débogage USB actif.
Elle met à jour l'application en place : **ne désinstalle pas Instagram**.

## Préparer le téléphone

1. Active les options développeur et le débogage USB sur le téléphone.
2. Branche-le en USB et accepte la demande d'autorisation RSA affichée à l'écran.
3. Si Android demande le mode USB, choisis un mode permettant la connexion ADB.

## Vérifier la connexion

Copie-colle ces commandes dans un terminal Linux :

```bash
cd "$HOME/Documents/code/FeurStagram"

export JAVA_HOME="$HOME/.local/opt/temurin-21"
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

adb devices -l
```

La ligne de ton téléphone doit afficher l'état `device`. Si elle affiche
`unauthorized`, déverrouille le téléphone et accepte la demande d'autorisation,
puis relance `adb devices -l`. Ne continue pas si aucun appareil autorisé
n'apparaît. Débranche les autres appareils Android pour éviter toute ambiguïté.

## Compiler et installer

Pour reconstruire et installer la version Instagram 454 déjà présente dans le
projet :

```bash
cd "$HOME/Documents/code/FeurStagram"
./build.sh build/inputs/instagram-454.apkm --install
```

Le script compile les patches, vérifie le rapport et la signature, puis lance
`adb install -r`. Il s'arrête en cas d'échec de compilation ou de patch. La
commande ne doit afficher `BUILD SUCCESSFUL`, les patches vérifiés et
`Performing Streamed Install` suivi de `Success` pour confirmer la réussite.

Pour installer une autre version d'Instagram, place son bundle `.apkm` dans le
projet puis remplace le chemin du fichier dans la commande, par exemple :

```bash
./build.sh "build/inputs/nom-du-bundle.apkm" --install
```

N'ajoute pas `--debug`. N'ajoute `--clone` que si tu veux installer une
application séparée à côté d'Instagram officiel ; ce guide installe la version
classique qui remplace l'application du paquet `com.instagram.android`.

## Vérifier l'installation

```bash
adb shell dumpsys package com.instagram.android \
  | grep -E 'versionName=|firstInstallTime=|lastUpdateTime='
```

Vérifie le numéro de version et l'heure de mise à jour. Puis ouvre Feurstagram
sur le téléphone.

## Si Android refuse la mise à jour

- `device unauthorized` : déverrouille le téléphone et accepte la demande RSA.
- `more than one device/emulator` : débranche les autres appareils Android.
- `The supplied javaHome seems to be invalid` avec un chemin vers un JDK
  inexistant : une ancienne instance Gradle peut encore référencer ce JDK.
  Depuis la racine du projet, arrête les daemons Gradle puis vérifie Java :

  ```bash
  ./gradlew --stop
  "$JAVA_HOME/bin/java" -version
  ```

  Si Java 21 s'affiche, relance ensuite la commande `./build.sh ... --install`.
  N'exécute `./gradlew --stop` que lorsqu'aucune autre compilation Gradle n'est
  en cours : cette commande arrête aussi les daemons Gradle 9.5 d'autres projets.
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE` : la signature de l'APK ne correspond
  pas à celle de l'application installée. **N'exécute pas `adb uninstall`** si
  tu veux garder les données. Il faut reconstruire avec le même keystore et le
  même alias que l'installation précédente ; changer de clé peut obliger à
  désinstaller et faire perdre les données locales.
- Échec du build ou d'un patch : ne tente pas d'installer un ancien APK ni de
  contourner l'erreur. Vérifie la compatibilité de la version Instagram et
  corrige le build avant de recommencer.
- Alerte de sécurité Android : ne désactive pas Play Protect ou une autre
  protection et ne contourne pas l'avertissement. Arrête l'installation et
  vérifie la provenance de l'APK.

Les réglages restent normalement présents lors d'une mise à jour acceptée avec
la même signature. Android/Huawei peut aussi restaurer le fichier de préférences
Feurstagram depuis une sauvegarde, mais cela dépend des réglages et du service
de sauvegarde de l'appareil. Une désinstallation efface les données locales ;
la sauvegarde ne garantit pas leur récupération.
