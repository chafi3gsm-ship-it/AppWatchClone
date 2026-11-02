# AppWatch Clone - Application Android Native (Kotlin)

Cette application surveille l'utilisation des applications en arrière-plan à l'aide de `UsageStatsManager` et d'un `ForegroundService` (Android 14+ compatible avec `foregroundServiceType="specialUse"`).

## Comment générer le fichier APK (.apk) :

### Méthode 1 : Dans Android Studio (Le plus simple)
1. Ouvrez Android Studio (Ladybug, Iguana ou version récente).
2. Cliquez sur **File > Open** et sélectionnez le dossier extrait.
3. Dans la barre de menu supérieure, cliquez sur :
   👉 **Build > Build Bundle(s) / APK(s) > Build APK(s)**
4. Après quelques secondes, une bulle s'affiche en bas à droite avec le lien **"locate"**.
5. Votre fichier APK est prêt :
   📂 `app/build/outputs/apk/debug/app-debug.apk`
6. Transférez ce fichier sur votre téléphone et installez-le !

### Méthode 2 : En ligne de commande (Terminal)
```bash
./gradlew assembleDebug
```
L'APK sera créé dans `app/build/outputs/apk/debug/app-debug.apk`.

### Méthode 3 : Gratuit en ligne via GitHub Actions
Le projet contient déjà `.github/workflows/build-apk.yml`. Si vous publiez ce code sur un dépôt GitHub, GitHub va compiler l'APK sur ses serveurs et vous donner le lien direct de téléchargement !