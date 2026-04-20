# GpsLogger — APK pour Samsung Z Flip 7

Application Android minimaliste qui enregistre la position GPS toutes les 10 secondes
dans un fichier JSON, sans aucune interface graphique.

---

## Fichier de sortie

Les logs sont écrits dans :
```
/sdcard/GpsLogger/gps_log_YYYY-MM-DD_HH-mm-ss.json
```

Format d'un enregistrement :
```json
[
  {
    "timestamp": "2026-04-20T14:30:00Z",
    "latitude": 36.8065,
    "longitude": 10.1815,
    "altitude_m": 12.5,
    "accuracy_m": 4.2,
    "speed_ms": 0.0,
    "bearing_deg": null,
    "provider": "fused"
  },
  ...
]
```

---

## Compilation avec Android Studio

### Prérequis
- Android Studio Hedgehog (2023.1.1) ou plus récent
- JDK 17+
- Un compte Google (pour les Play Services sur l'émulateur, optionnel)

### Étapes

1. **Ouvrir le projet**
   - Lancer Android Studio → "Open" → sélectionner le dossier `GpsLogger/`

2. **Sync Gradle**
   - Android Studio va télécharger les dépendances automatiquement
   - Attendre "Gradle sync finished"

3. **Connecter le Z Flip 7**
   - Activer le mode développeur : Paramètres → À propos → taper 7x sur "Numéro de build"
   - Activer "Débogage USB" dans les options développeur
   - Brancher via USB

4. **Générer l'APK release**
   - Menu : Build → Generate Signed Bundle/APK → APK
   - Créer un keystore si nécessaire
   - Choisir "release"
   - L'APK est dans : `app/release/app-release.apk`

   **OU en debug (plus rapide) :**
   - Menu : Build → Build Bundle(s)/APK(s) → Build APK(s)
   - L'APK est dans : `app/build/outputs/apk/debug/app-debug.apk`

5. **Installer sur le téléphone**
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```
   Ou directement via le bouton "Run ▶" dans Android Studio.

---

## Compilation en ligne de commande (sans Android Studio)

```bash
# Sur Linux/Mac, depuis le dossier GpsLogger/
./gradlew assembleDebug

# Sur Windows
gradlew.bat assembleDebug
```

APK généré : `app/build/outputs/apk/debug/app-debug.apk`

---

## Permissions à accorder après l'installation

Au premier lancement, l'app demande les permissions GPS.
**IMPORTANT sur Android 12+ :** accorder "Toujours autoriser" (Allow all the time)
pour que le GPS fonctionne en arrière-plan.

1. Paramètres → Applications → GpsLogger → Autorisations → Position
2. Sélectionner **"Toujours autoriser"** (pas seulement "Pendant l'utilisation")

---

## Comportement

- **Aucune fenêtre** : l'activité se ferme immédiatement après avoir démarré le service
- **Notification discrète** : une notification silencieuse apparaît (obligatoire Android 8+)
- **Démarrage automatique** : le service redémarre au boot du téléphone
- **Résilience** : si le service est tué par Android, il redémarre automatiquement (START_STICKY)
- **WakeLock** : le CPU reste actif pour ne pas rater d'enregistrements

---

## Lire les fichiers JSON depuis un PC

```bash
adb pull /sdcard/GpsLogger/ ./logs/
```

---

## Arrêter le service

```bash
adb shell am stopservice com.gpslogger/.GpsService
```

Ou désinstaller l'app :
```bash
adb uninstall com.gpslogger
```

---

## Notes Samsung One UI (Z Flip 7)

Samsung peut tuer les services en arrière-plan. Pour éviter ça :
- Paramètres → Batterie → Gestion de l'énergie → GpsLogger → **"Non restreint"**
- Désactiver l'optimisation de batterie pour GpsLogger
