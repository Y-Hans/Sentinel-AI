# Setup

## Prerequisites

- Android Studio with its bundled JDK 17.
- Android SDK 34 installed.
- An Android device or emulator running Android 8.0 (API 26) or later.
- Git.

## Clone the Repository

```bash
git clone <repository-url>
cd sentinel-ai
```

Replace `<repository-url>` with the repository's Git URL. The Android Gradle project is inside the `android-app` directory.

## Open in Android Studio

1. Start Android Studio.
2. Select **Open**.
3. Choose the repository's `android-app` directory.
4. Wait for the Gradle project to sync.

No backend service or ML download is required for local model inference. The TensorFlow Lite model and scaler are bundled with the app.

## Run the App

1. Start an emulator or connect an Android device with USB debugging enabled.
2. Select the `app` run configuration.
3. Select the target device.
4. Click **Run**.

## Enable Protection Modes

The app explains each permission during setup. Grant only the modes needed for testing:

- **Notification access:** Required for incoming notification scanning.
- **Post notifications:** Required for warning notifications on Android 13 and later.
- **Contacts:** Optional; improves known-sender checks.
- **Display over other apps:** Used by urgent-alert presentation.
- **Default browser role:** Required for click-time link interception.

Manual scans and on-device URL inference can be demonstrated without enabling every permission.

## Command-Line Build

From the `android-app` directory:

```bash
./gradlew assembleDebug
```

On Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

If Gradle cannot locate Java, configure it to use Android Studio's bundled JDK 17.

