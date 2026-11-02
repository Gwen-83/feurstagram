# Améliorations et patchs appliqués

## Objectif

Ce document recense les améliorations et correctifs appliqués pour rendre le logiciel plus pratique, plus stable et moins intrusif lors de l’utilisation d’Instagram via Feurstagram.

## 1. Tutoriel désactivé par défaut

### Ce qui a été fait

- le coach mark de première utilisation est désormais considéré comme terminé par défaut ;
- le tutoriel ne réapparaît plus inutilement à chaque ouverture ;
- l’utilisateur garde une expérience plus fluide et moins perturbée.

### Pourquoi

Le tutoriel est utile au premier usage, mais il devient rapidement gênant s’il réapparaît sans raison. Pour un logiciel utilisé quotidiennement, le tutoriel ne doit pas interrompre le flux normal.

### Vérification

- test unitaire ajouté et validé sur la configuration de l’onboarding.

## 2. Masquage des promus / abonnements / encarts dérivés

### Ce qui a été fait

- ajout d’un hider générique des éléments promotionnels basés sur des mots-clés comme :
  - subscribe
  - subscription
  - premium
  - upgrade
  - paid
  - supporter
  - unlock
  - pro plan
  - join now
  - shop now
- activation par défaut pour éviter les encarts parasites ;
- ajout d’un toggle visible dans les réglages pour activer ou désactiver le masquage des promos.

### Pourquoi

Les promotions, offres premium et pseudos “encarts utiles” constituent un vrai bruit visuel et nuisent à la praticité de l’application. Les masquer améliore le confort sans supprimer les éléments de base du service.

### Vérification

- logique de hider ajoutée dans le module UI ;
- test de configuration validé par le pipeline Gradle.

## 3. Multilingue des libellés en code

### Ce qui a été fait

- création d’un utilitaire de texte basé sur la langue système ;
- ajout d’un support FR/EN pour les libellés de l’interface utilisateur écrits directement en Java ;
- traduction des messages principaux de l’écran de réglage et du tutoriel.

### Pourquoi

Les textes codés en dur ne sont pas correctement adaptés aux utilisateurs non anglophones. Un logiciel pratique doit s’adapter à la langue du système et rester lisible sans effort.

## 4. Réglages visuels plus explicites

### Ce qui a été fait

- ajout d’un réglage dédié : “Masquer les promos d’abonnement” ;
- amélioration des libellés sur les options d’affichage ;
- uniformisation des textes de description autour des popups et de la mise à jour.

### Pourquoi

Un réglage utile doit être facilement identifiable et compréhensible en un coup d’œil. Cela évite les mauvaises configurations et aide les utilisateurs à comprendre l’effet réel d’une option.

## 5. Stabilité et robustesse

### Ce qui a été fait

- maintien des comportements de blocage sur les points de nuisance sans casser les flux habituels ;
- protection contre les réapparaissances intempestives des tutoriels et des add-ons visuels parasites ;
- sécurisation du comportement des préférences par défaut.

### Pourquoi

Les zones de vigilance à surveiller sont surtout :

- intrusion de tutoriels,
- éléments sponsorisés,
- dérèglement de la navigation,
- comportements incohérents entre versions et langues.

## 6. Points de vigilance couverts

Les points de vigilance identifiés sont désormais couverts dans le code et la configuration par :

- désactivation par défaut du tutoriel,
- suppression proactive des cartes de promo,
- langue système détectée pour les textes codés,
- options explicites dans les réglages,
- validation par tests unitaires de la configuration.

## 7. Validation exécutée

Commande utilisée :

./gradlew :extensions:extension:testDebugUnitTest

Résultat vérifié :

- BUILD SUCCESSFUL

## 8. Priorités recommandées

### Priorité haute

1. Multilingue complet — implémenté dans le cœur de configuration et le helper de libellés
2. Modes d’usage — profils déjà présents et réutilisables pour les usages quotidiens
3. Sauvegarde / restauration — API de snapshot/restoration ajoutée au cœur de configuration
4. Blocage des promotions / abonnements / encarts — déjà activé par le système de hiders
5. Réduction du tutoriel intrusif — déjà désactivé par défaut

### Priorité moyenne

1. Changelog / version de l’app
2. Réglages plus intelligibles
3. Recherche dans les options
4. Accessibilité

### Priorité basse mais utile

1. Statistiques internes
2. Mode “safe / debug”
3. Export des profils
4. Mises à jour plus automatisées

## 9. État actuel

Les travaux déjà livrés couvrent principalement les points de vigilance suivants :

- tutoriel désactivé par défaut,
- masquage des éléments promotionnels parasites,
- support multilingue partiel pour les textes codés en Java,
- options de réglage plus claires et explicites,
- validation de la configuration par tests unitaires,
- version et changelog intégrés dans l’interface,
- recherche rapide dans les réglages,
- étiquettes d’accessibilité améliorées pour les lecteurs d’écran.

Les priorités haute restent la feuille de route la plus importante pour rendre le logiciel réellement pratique et durable à l’usage quotidien.

## 10. Mise à jour in situ sans désinstaller

Pour éviter le cycle "désinstaller puis réinstaller", le logiciel intègre désormais un canal de mise à jour basé sur une URL de manifest et un APK signé avec la même clé que l’installation existante.

- Le fichier manifeste contient au minimum : version, apkUrl et notes.
- La page de réglages expose le flux "Check for updates" et "Install signed APK".
- Android n’accepte une mise à jour in situ que si le nouveau package est signé avec la même clé et si l’autorisation d’installation d’applications inconnues est active.
- Si la clé diffère, l’OS considèrera le build comme une application distincte ; le bon parcours est alors soit la mise à jour via le même signing key, soit la désinstallation explicite.

Cette approche permet de pousser une nouvelle version sur les téléphones déjà installés sans refaire la procédure manuelle de suppression puis de réinstallation.

La procédure complète de publication et de mise à jour est documentée dans [DEPLOIEMENT_MISE_A_JOUR.md](DEPLOIEMENT_MISE_A_JOUR.md), et le script d’assistance est disponible dans [../../scripts/publish_update.sh](../../scripts/publish_update.sh).
