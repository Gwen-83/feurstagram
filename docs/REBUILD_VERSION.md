# Recompiler Feurstagram pour une nouvelle version d'Instagram

Cette fiche reprend la procédure à chaque mise à jour. Elle suppose que les
outils et dépendances locales sont déjà configurés selon [le guide Linux](SETUP_LINUX.md).

## 1. Vérifier la compatibilité avant de télécharger

Une nouvelle version d'Instagram ne signifie pas automatiquement que les patches
Feurstagram sont déjà compatibles. Consulte les notes de version et le code du
projet pour vérifier que la version visée est prise en charge. La compilation
doit être considérée comme échouée si un patch échoue, même si un ancien
`build/outputs/feurstagram.apk` existe déjà.

Sur APKMirror, choisis le bundle stable correspondant à ton téléphone :
architecture `arm64-v8a` (ou `arm64-v8a + armeabi-v7a`) et version Android
compatible. Télécharge le bundle complet `.apkm` ; ne l'installe pas.
Le build conserve les architectures natives présentes dans le bundle d'entrée ;
il ne retire pas les bibliothèques et ne génère pas de variantes par ABI. Le
bundle Instagram 454 testé ici ne contient que `arm64-v8a`, donc l'APK produit
n'est pas universel : vérifie que ton appareil est compatible avant de continuer.

## 2. Définir le chemin du bundle téléchargé

Ouvre un terminal à la racine du projet :

```bash
cd ~/Documents/code/FeurStagram
```

Définis `APK` avec le nom exact du fichier téléchargé dans `Téléchargements`.
Remplace le texte entre chevrons, sans laisser les chevrons :

```bash
APK="$HOME/Téléchargements/<nom-exact-du-fichier>.apkm"
ls -lh "$APK"
```

Vérifie que la commande `ls` affiche le bon fichier. Conserve une copie de
chaque version téléchargée ; ne réutilise pas le nom d'un ancien bundle.

## 3. Compiler sans installer

```bash
./build.sh "$APK" --clone
```

Le script fusionne le bundle avec APKEditor si nécessaire, compile les patches
et écrit le résultat dans `build/outputs/feurstagram.apk`. `--clone` prépare une application
qui coexiste avec Instagram officiel. N'ajoute pas `--debug`.

Vérifie le statut détaillé du patcher avant toute installation :

```bash
python3 - <<'PY'
import json
from pathlib import Path

report = json.loads(Path("build/reports/patch-report.json").read_text())
print("Version Instagram :", report.get("packageVersion", "inconnue"))
print("Patches en échec :", report.get("failedPatches", []))
if report.get("success") is not True or report.get("failedPatches"):
    raise SystemExit("BUILD À NE PAS INSTALLER : le rapport indique un échec.")
if not Path("build/outputs/feurstagram.apk").is_file():
    raise SystemExit("BUILD À NE PAS INSTALLER : l'APK de sortie est absent.")
print("Rapport réussi ; APK présent.")
PY
```

Si le build s'arrête sur `Adapter has no submit(List)` ou une autre erreur de
patch, cette version d'Instagram n'est pas prise en charge par le code local :
n'installe pas l'APK. Mets à jour le projet avec une correction compatible, ou
choisis une version explicitement prise en charge.

Le script peut afficher une erreur Python lors de la mise en forme du rapport
après avoir terminé. Dans ce cas, le fichier JSON ci-dessus est la vérification
à consulter : il doit contenir `"success": true` et `"failedPatches": []`.

## 4. Vérifier la signature de l'APK

```bash
ls -lh build/outputs/feurstagram.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose build/outputs/feurstagram.apk
```

La commande `apksigner` doit afficher `Verifies`. L'APK de test est signé avec
une clé temporaire si aucune clé de signature permanente n'a été configurée.

## 5. Installer sur le téléphone

Branche le téléphone, active le débogage USB et accepte la demande
d'autorisation. Vérifie que l'appareil est autorisé :

```bash
adb devices
```

La ligne de l'appareil doit finir par `device` (et non `unauthorized`). Puis :

```bash
adb install -r build/outputs/feurstagram.apk
```

Si Android répond `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, l'application déjà
installée et le nouvel APK ont des signatures différentes. La désinstallation
de l'application clonée suivie d'une réinstallation peut résoudre ce problème,
mais efface ses données locales. Pour conserver les données entre les versions,
il faut signer chaque build avec le même keystore et le même alias ; garde ce
keystore en sécurité et ne le committe jamais.

## Résumé des commandes à chaque version

Après avoir téléchargé le nouvel APK bundle, remplace son nom dans la première
commande :

```bash
cd ~/Documents/code/FeurStagram
APK="$HOME/Téléchargements/<nom-exact-du-fichier>.apkm"
ls -lh "$APK"
./build.sh "$APK" --clone
```

Exécute ensuite le contrôle JSON de l'étape 3 et la vérification de signature de
l'étape 4. N'installe que si les deux validations réussissent.
