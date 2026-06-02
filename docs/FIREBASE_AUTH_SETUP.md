# Firebase Auth setup (fix sign-up `CONFIGURATION_NOT_FOUND`)

If logcat shows:

```text
RecaptchaCallWrapper: ... CONFIGURATION_NOT_FOUND
FirebaseAuth: Creating user with ... empty reCAPTCHA token
```

the app cannot complete Email/Password sign-up because **Firebase does not have your Android signing certificate** registered.

## What the other log lines mean (safe to ignore)

| Log | Meaning |
|-----|---------|
| `AutofillManager` / `ImeTracker` / `GoogleInputMethodService` | Normal keyboard focus on the password field — **not errors**. |
| `No AppCheckProvider installed` | Warning only unless you enforce App Check in Firebase Console. |
| `X-Firebase-Locale ... null` | Harmless SDK noise. |

## Fix (required once per machine / release key)

### 1. Get SHA-1 and SHA-256

**Debug builds** (Android Studio default):

```bash
keytool -list -v -keystore ~/.android/debug.keystore \
  -alias androiddebugkey -storepass android -keypass android
```

Copy the **SHA1** and **SHA256** lines.

**Release builds** use your release keystore instead of `debug.keystore`.

### 2. Add fingerprints in Firebase

1. Open [Firebase Console](https://console.firebase.google.com/) → project **dispute-documenter**.
2. **Project settings** (gear) → **Your apps** → Android app `com.example.tenant_landlorddisputedocumenter`.
3. **Add fingerprint** → paste SHA-1, then add SHA-256.
4. Save.

### 3. Download new `google-services.json`

1. On the same app card, click **Download google-services.json**.
2. Replace `app/google-services.json` in this repo.
3. Rebuild and reinstall the app.

After this step, `google-services.json` should contain a non-empty `"oauth_client"` array (yours was empty, which causes `CONFIGURATION_NOT_FOUND`).

### 4. Enable Email/Password

Firebase Console → **Authentication** → **Sign-in method** → enable **Email/Password**.

### 5. Optional APIs (if sign-up still fails)

In [Google Cloud Console](https://console.cloud.google.com/) for the same project, ensure these are enabled:

- Identity Toolkit API  
- Token Service API  

## Debug builds in this project

`ProofNestApplication` calls `setAppVerificationDisabledForTesting(true)` in **debug** only so emulators can sign up while you fix SHA/reCAPTCHA. **Release builds still need correct SHA fingerprints.**

## Verify

1. Uninstall the old APK from the device/emulator.
2. Install a fresh debug build.
3. Sign up again — you should see `FirebaseAuth: ... success` instead of `CONFIGURATION_NOT_FOUND`.

## Example debug fingerprints (this dev machine)

Add these in Firebase if you build on the same machine as the project author:

- SHA-1: `BD:37:82:1F:F7:E5:CE:41:9B:8C:76:0B:B8:65:11:C6:6C:6E:95:85`
- SHA-256: `19:55:D4:0C:BB:81:A2:25:91:E6:74:74:FB:B0:5F:FB:98:C1:8A:32:98:B5:F3:D6:B6:23:D7:CA:20:B5:24:01`

Your machine’s debug SHA will differ unless you share the same `debug.keystore`.
