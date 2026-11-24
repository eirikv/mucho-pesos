# Google Cloud OAuth Setup Guide — Mucho Pesos

Mucho Pesos uses a **shared Google Sheet** as its sole database and sync layer—no central server is required. To enable real Google Sign-In, Google Sheets sync, and Google Drive ledger creation/picking (outside of Demo Mode), follow these one-time setup steps in the Google Cloud Console.

---

## Prerequisites

- A free [Google Cloud Console](https://console.cloud.google.com/) account.
- Two Google/Gmail accounts if you want to test partner sharing end-to-end (Partner A creates & shares; Partner B links).
- Android app package name: **`vige.land.muchopesos`**

---

## Step 1: Create a Google Cloud Project & Enable APIs

1. Open the [Google Cloud Console](https://console.cloud.google.com/) and create a new project (for example, **`Mucho Pesos`**).
2. In the left navigation menu, go to **APIs & Services → Library**.
3. Search for and **Enable** both of the following APIs:
   - **Google Sheets API**
   - **Google Drive API**

---

## Step 2: Configure the OAuth Consent Screen

1. Go to **Google Auth Platform → Overview** (or **APIs & Services → OAuth consent screen**).
2. Choose **External** user type (so both you and your partner can sign in with standard Gmail accounts).
3. Fill in the required fields:
   - **App name**: `Mucho Pesos`
   - **User support email**: your Gmail address
   - **Developer contact information**: your Gmail address
4. Under **Data Access / Scopes**, add the three minimal scopes used by the app:
   - `https://www.googleapis.com/auth/spreadsheets`
   - `https://www.googleapis.com/auth/drive.file`
   - `https://www.googleapis.com/auth/drive.readonly`
5. Under **Audience / Test users** (while the app is in Testing mode), add **both** your Gmail address (Partner A) and your partner's Gmail address (Partner B).
   > **Tip**: Leaving the app in **Testing** mode with your two Gmail addresses added as Test Users is completely free and skips Google's public app verification process.

---

## Step 3: Get Your App's SHA-1 Signing Certificate Fingerprint

Google requires your Android signing certificate's SHA-1 fingerprint to authorize Credential Manager and `AuthorizationClient` requests for `vige.land.muchopesos`.

### Debug Keystore SHA-1 (for local development & Pixel testing)

Run this command on macOS/Linux:

```bash
keytool -list -v \
  -alias androiddebugkey \
  -keystore ~/.android/debug.keystore \
  -storepass android \
  -keypass android
```

Or via Gradle from the project root:

```bash
./gradlew signingReport
```

Copy the **SHA-1** fingerprint (format: `AA:BB:CC:...`).

*(For a release build later, run `keytool -list -v` against your release `.jks`/`.keystore` and create a second Android OAuth Client ID with the release SHA-1.)*

---

## Step 4: Create the Android OAuth 2.0 Client ID

1. Go to **APIs & Services → Credentials** (or **Google Auth Platform → Clients**).
2. Click **Create Credentials → OAuth client ID**.
3. Set **Application type** to **Android**.
4. Enter:
   - **Name**: `Mucho Pesos Android (Debug)`
   - **Package name**: `vige.land.muchopesos`
   - **SHA-1 certificate fingerprint**: paste the SHA-1 from Step 3.
5. Click **Create**.

> **Note**: You do not need to copy the Android Client ID into code; Google Play Services matches it automatically using the app's package name and signing SHA-1.

---

## Step 5: Create the Web Application OAuth 2.0 Client ID

Android's **Credential Manager** (`GetGoogleIdOption`) requires a **Web Application** OAuth client ID in the **same Google Cloud project** to issue Google ID tokens.

1. In **Credentials**, click **Create Credentials → OAuth client ID**.
2. Set **Application type** to **Web application**.
3. Enter:
   - **Name**: `Mucho Pesos Web Client`
   - *(No redirect URIs or JavaScript origins are needed.)*
4. Click **Create** and copy the generated **Client ID** (it ends in `.apps.googleusercontent.com`).

---

## Step 6: Add `GOOGLE_WEB_CLIENT_ID` to `local.properties`

Open `/Users/eirikvigeland/Projects/mucho-pesos/local.properties` (this file is gitignored and never committed) and add your Web Client ID:

```properties
sdk.dir=/Users/eirikvigeland/Library/Android/sdk
GOOGLE_WEB_CLIENT_ID=123456789012-abcdefghijklmnopqrstuvwxyz.apps.googleusercontent.com
```

Gradle injects this value into `BuildConfig.GOOGLE_WEB_CLIENT_ID` at build time. Rebuild and install the app:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Step 7: Testing With Two Accounts

1. **Partner A (Ledger Creator)**:
   - Sign in with Google on Device A and approve the Sheets + Drive consent prompt.
   - Choose **Create Shared Ledger**, enter a ledger name, select your shared home currency (e.g., `USD`, `EUR`, `NOK`), enter your display name, Partner B's Gmail address, and Partner B's display name.
   - Mucho Pesos creates a formatted Google Sheet (`Config` and `Ledger` tabs) in Partner A's Drive and grants Editor access to Partner B.
2. **Partner B (Ledger Joiner)**:
   - Sign in with Google on Device B and approve the consent prompt.
   - Choose **Link Existing Ledger** and either tap **Pick from Google Drive** to select the shared sheet or paste the spreadsheet URL/ID.
3. Both partners can now add, edit (append-only `VOID` + replacement), delete (append-only `VOID`), and settle up expenses directly against the shared Google Sheet.

---

## Troubleshooting

- **`GOOGLE_WEB_CLIENT_ID is not configured`**: Ensure `GOOGLE_WEB_CLIENT_ID=...` is set in `local.properties` (without quotes) and run a clean rebuild.
- **`10: DEVELOPER_ERROR` or Sign-In failure**: Double-check that the Android OAuth Client ID uses exact package `vige.land.muchopesos`, matches the SHA-1 of the keystore signing your APK, and belongs to the **same** Google Cloud project as the Web Client ID.
- **`403 access_denied` on OAuth consent**: Verify both Gmail accounts are listed under **Test users** on your OAuth consent screen.
- **Trying without Google Cloud first**: Tap **Try Demo Mode** on the sign-in screen to test every feature locally against a simulated append-only sheet.
