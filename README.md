# MeetAI

An Android app that records voice, then on stop **transcribes** it, **detects multiple speakers**, supports **automatic language detection (multilingual)**, and generates a **summary** — with cloud-synced history.

## How it works

```
 ┌──────────┐   m4a    ┌─────────────┐  transcript   ┌────────────┐  summary  ┌───────────┐
 │  Record  │ ───────► │ AssemblyAI  │ ────────────► │  OpenAI    │ ────────► │ Firestore │
 │ (mic)    │  upload  │ diarization │  + language   │  gpt-4o    │   notes   │  (synced) │
 └──────────┘          │  + detect   │               └────────────┘           └───────────┘
                       └─────────────┘
```

- **Recording** — `MediaRecorder` captures compressed AAC (`.m4a`). A foreground service keeps the mic alive in the background.
- **Transcription + speakers + language** — [AssemblyAI](https://www.assemblyai.com/) in one job (`speaker_labels: true`, `language_detection: true`). Whisper alone does **not** do speaker diarization, which is why AssemblyAI handles transcription here.
- **Summary** — OpenAI `gpt-4o` turns the speaker-labeled transcript into Markdown notes (Summary / Key Points / Decisions / Action Items), replying in the transcript's own language.
- **Sync** — Firebase Auth (Google) scopes data per user; Firestore stores transcript + summary at `users/{uid}/recordings/{id}` with offline persistence (so it works offline too). Audio files stay on-device and are not uploaded.

> **Security note:** API keys are baked into the APK via `BuildConfig` (read from `local.properties`). This is fine for a **personal** app you don't distribute — anyone who decompiles a shipped APK could extract the keys. If you ever publish this, move the AssemblyAI/OpenAI calls behind a small backend proxy and keep the keys server-side.

## Tech stack

Kotlin · Jetpack Compose (Material 3) · Coroutines · OkHttp + kotlinx.serialization · Firebase Auth + Firestore · AssemblyAI · OpenAI.

## Project layout

```
app/src/main/java/com/sabahhub/meetai/
├── MeetAiApp.kt            # Application + manual service locator
├── MainActivity.kt         # Compose host, nav, runtime permissions
├── audio/
│   ├── AudioRecorder.kt    # MediaRecorder wrapper (-> .m4a)
│   └── RecordingService.kt # foreground mic service
├── auth/AuthManager.kt     # Google Sign-In -> Firebase Auth
├── data/
│   ├── model/Recording.kt  # domain model + status enum
│   ├── remote/
│   │   ├── AssemblyAiClient.kt  # upload -> transcribe (diarize+lang) -> poll
│   │   ├── OpenAiClient.kt      # gpt-4o summary
│   │   └── dto/                 # serialization DTOs
│   └── sync/FirestoreSync.kt    # per-user cloud store (offline-capable)
└── ui/
    ├── MeetAiViewModel.kt  # orchestrates record -> transcribe -> summarize -> save
    └── screens/            # HomeScreen (record + history), DetailScreen (summary/transcript)
```

## Setup

### 1. Prerequisites
- Android Studio (Koala or newer) with JDK 17.
- Min SDK 26 (Android 8.0+).

### 2. API keys
Copy the template and fill it in:

```bash
cp local.properties.template local.properties
```

Set `ASSEMBLYAI_API_KEY`, `OPENAI_API_KEY`, and `WEB_CLIENT_ID` (see step 3).
Make sure `sdk.dir` points at your Android SDK.

### 3. Firebase (optional — for sign-in + cloud sync)
**You can skip this and build/run right away.** Without `google-services.json`, the
`google-services` plugin is not applied, sign-in is hidden, and recordings are kept
in an in-memory session history (they don't survive an app restart). Add Firebase
when you want cloud sync across devices.

1. Create a project at <https://console.firebase.google.com>.
2. Add an Android app with package name **`com.sabahhub.meetai`**.
3. Download `google-services.json` into **`app/`**.
4. In **Authentication → Sign-in method**, enable **Google**.
5. Add your debug SHA-1 to the Android app (Project settings → your app → Add fingerprint). Get it with:
   ```bash
   ./gradlew signingReport      # look for the "debug" variant SHA-1
   ```
6. Copy the **Web client ID** (Authentication → Google provider, or Google Cloud → Credentials → "Web client (auto created…)") into `WEB_CLIENT_ID` in `local.properties`.
7. In **Firestore Database**, create a database and use these rules so each user only sees their own data:
   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{db}/documents {
       match /users/{uid}/recordings/{doc} {
         allow read, write: if request.auth != null && request.auth.uid == uid;
       }
     }
   }
   ```

### 4. Build & run
Open the project in Android Studio and press Run, or:

```bash
./gradlew installDebug      # or :app:assembleDebug to just build the APK
```

> Requires JDK 17–21. If your system `java` is newer (e.g. 25), point Gradle at Android Studio's bundled JDK:
> `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`. Android Studio uses the right JDK automatically.

## Usage
1. Launch the app and **Sign in with Google** (required for cloud sync).
2. Tap the **mic** to start recording; tap **stop** to finish.
3. Watch the status: *Uploading → Transcribing & detecting speakers → Summarizing*.
4. The finished recording appears in **History**; tap it for the **Summary** and speaker-labeled **Transcript** tabs. Use **Share** to export as text.

## Notes & limits
- AssemblyAI bills per audio hour; OpenAI bills per token. Both are pay-as-you-go.
- Very long recordings = longer transcription polling; the HTTP client is configured with no overall call timeout to accommodate this.
- Language detection and diarization quality depend on audio clarity and having distinct speakers.
- Summaries render as plain text today (Markdown source). Add a Markdown renderer (e.g. `compose-markdown`) if you want formatted output.

## Possible next steps
- Backend proxy for keys (if distributing).
- Upload audio to Firebase Storage for cross-device playback.
- Rename recordings / search history.
- Pause & resume recording.
