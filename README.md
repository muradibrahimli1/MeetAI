# MeetAI

An Android app that records voice, then on stop **transcribes** it, **detects multiple speakers**, supports **automatic language detection (multilingual)**, and generates a **summary** — with cloud-synced history.

## How it works

```
 ┌──────────┐   m4a    ┌─────────────┐  transcript   ┌────────────┐  summary  ┌───────────┐
 │  Record  │ ───────► │ AssemblyAI  │ ────────────► │  OpenAI    │ ────────► │ Supabase  │
 │ (mic)    │  upload  │ diarization │  + language   │  gpt-4o    │   notes   │ (Postgres)│
 └──────────┘          │  + detect   │               └────────────┘           └───────────┘
                       └─────────────┘
```

- **Recording** — `MediaRecorder` captures compressed AAC (`.m4a`). A foreground service keeps the mic alive in the background.
- **Transcription + speakers + language** — [AssemblyAI](https://www.assemblyai.com/) in one job (`speaker_labels: true`, `language_detection: true`). Whisper alone does **not** do speaker diarization, which is why AssemblyAI handles transcription here.
- **Summary** — OpenAI `gpt-4o` turns the speaker-labeled transcript into Markdown notes (Summary / Key Points / Decisions / Action Items), replying in the transcript's own language.
- **Auth + Sync** — [Supabase](https://supabase.com/) email/password auth (via GoTrue REST) and a `recordings` table (via PostgREST), scoped per user by row-level security. No SDK — plain OkHttp calls. Audio files stay on-device; only transcript/summary metadata sync.

> **Security note:** API keys are baked into the APK via `BuildConfig` (read from `local.properties`). This is fine for a **personal** app you don't distribute — anyone who decompiles a shipped APK could extract the keys. If you ever publish this, move the AssemblyAI/OpenAI calls behind a small backend proxy and keep the keys server-side.

## Tech stack

Kotlin · Jetpack Compose (Material 3) · Coroutines · OkHttp + kotlinx.serialization · Haze (glass blur) · Supabase (auth + Postgres) · AssemblyAI · OpenAI.

## Project layout

```
app/src/main/java/com/sabahhub/meetai/
├── MeetAiApp.kt            # Application + manual service locator
├── MainActivity.kt         # Compose host, nav, runtime permissions
├── audio/
│   ├── AudioRecorder.kt    # MediaRecorder wrapper (-> .m4a)
│   └── RecordingService.kt # foreground mic service
├── data/
│   ├── model/Recording.kt  # domain model + status enum
│   ├── remote/
│   │   ├── AssemblyAiClient.kt  # upload -> transcribe (diarize+lang) -> poll
│   │   ├── OpenAiClient.kt      # gpt-4o title + summary (JSON)
│   │   ├── dto/                 # serialization DTOs
│   │   └── supabase/            # SupabaseAuth, SupabaseRepository, SessionStore (REST)
└── ui/
    ├── MeetAiViewModel.kt  # orchestrates record -> transcribe -> summarize -> save/sync
    ├── theme/              # palette + gradient background (Haze source)
    ├── components/         # Glass (Haze), Waveform, MarkdownText
    └── screens/            # AppShell (nav), Recorder, Library, Settings, Detail
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

Set `ASSEMBLYAI_API_KEY`, `OPENAI_API_KEY`, and the Supabase values (see step 3).
Make sure `sdk.dir` points at your Android SDK.

### 3. Supabase (optional — for sign-in + cloud sync)
**You can skip this and build/run right away.** With `SUPABASE_URL`/`SUPABASE_ANON_KEY`
blank, sign-in is hidden and recordings are kept in an in-memory session history
(they don't survive an app restart). Add Supabase for email login + sync across devices.

1. Create a project at <https://supabase.com/dashboard>.
2. **Settings → API**: copy the **Project URL** and **anon public** key into
   `SUPABASE_URL` / `SUPABASE_ANON_KEY` in `local.properties`.
3. **Authentication → Providers → Email**: enable it and **turn off "Confirm email"**
   (so signup logs you straight in).
4. **SQL Editor**: run this to create the table + row-level security:
   ```sql
   create table public.recordings (
     id            uuid primary key,
     user_id       uuid not null default auth.uid() references auth.users (id) on delete cascade,
     created_at    int8 not null,
     title         text not null,
     duration_ms   int8 not null default 0,
     status        text not null default 'DONE',
     language      text,
     transcript    text not null default '',
     summary       text not null default '',
     error_message text,
     utterances    jsonb not null default '[]'::jsonb
   );

   alter table public.recordings enable row level security;

   create policy "own rows" on public.recordings
     for all
     using  (auth.uid() = user_id)
     with check (auth.uid() = user_id);
   ```

### 4. Build & run
Open the project in Android Studio and press Run, or:

```bash
./gradlew installDebug      # or :app:assembleDebug to just build the APK
```

> Requires JDK 17–21. If your system `java` is newer (e.g. 25), point Gradle at Android Studio's bundled JDK:
> `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`. Android Studio uses the right JDK automatically.

## Usage
1. (Optional) In **Settings**, sign up / sign in with email + password to enable cloud sync.
2. On the **Recorder** tab, tap the **mic** to start; it toggles **pause/resume**. Use **Discard** to drop it or **Save** to process.
3. Watch the status: *Uploading → Transcribing & detecting speakers → Summarizing*.
4. The finished recording appears in **Library** with an AI-generated title; tap it for the **Summary** and speaker-labeled **Transcript** tabs. Use **Share** to export as text.

## Notes & limits
- AssemblyAI bills per audio hour; OpenAI bills per token. Both are pay-as-you-go.
- Very long recordings = longer transcription polling; the HTTP client is configured with no overall call timeout to accommodate this.
- Language detection and diarization quality depend on audio clarity and having distinct speakers.
- Haze backdrop blur is best on Android 12+; older devices fall back to a tint.
- Cloud sync refreshes on sign-in and after each save/delete (no live realtime listener).

## Possible next steps
- Backend proxy for keys (if distributing).
- Upload audio to Supabase Storage for cross-device playback.
- Realtime sync (Supabase Realtime) instead of refresh-on-change.
- Rename recordings / search history.
