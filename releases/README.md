# ProofNest APK

| File | Version | Type |
|------|---------|------|
| `ProofNest-v1.0-debug.apk` | 1.0 (versionCode 1) | Debug, unsigned |

## Install on a device

1. Enable **Install unknown apps** for your file manager or browser (Android 8+).
2. Copy `ProofNest-v1.0-debug.apk` to the phone (USB, Drive, email, etc.).
3. Open the file and tap **Install**.

Requires **Android 7.0+** (API 24). Camera, location, and notification permissions are requested at runtime.

## Firebase

The APK is built against a Firebase project. For full cloud sync you need valid `app/google-services.json` when rebuilding from source. The committed APK includes whatever config was present at build time.

## Rebuild and refresh this file

```bash
./gradlew buildDebugApk
```
