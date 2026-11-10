# Clozed Code

An on-device Android coding agent — a clean-room re-implementation of the
`com.clozed.code` app recovered from `Clozed Code_1.0.apks`.

The agent runs entirely on the phone: it talks to a provider you configure
(Anthropic, OpenAI, or any OpenAI-compatible endpoint including local servers
such as Ollama at `http://127.0.0.1:11434/v1`), streams responses, and drives a
set of file / search / shell tools against a project folder on shared storage.

## What is real (recovered byte-for-byte from the APK)

These assets were copied verbatim from `base.apk` and are the strongest anchor
for fidelity — the app behaviour around them is a re-implementation.

- `app/src/main/assets/providers.json` — the full provider + model catalog
  (200+ providers, 9-column model rows).
- `app/src/main/assets/prompts/*.txt` — `system`, `anthropic`, `gpt`,
  `gpt-astra`, `kimi`, `meta`, `trinity`.
- `app/src/main/assets/skills/*/SKILL.md` — `code-review`, `port-from-project`,
  `skill-creator`, `spike`, `systematic-debugging`.
- Launcher icons (`mipmap-*`), adaptive-icon foreground/monochrome PNGs.
- Manifest facts: package, permissions, components, intent filters.

## What is a re-implementation (clean-room)

The shipped bytecode is protected by **PairIP**: `classes3.dex` (the app's own
code) is encrypted at rest and decrypted by a native stub at runtime. Only
strings, class/method names and assets are recoverable without a desktop JADX
unpack of the decrypted DEX. Everything below was rebuilt from the recovered
names, strings and the REA skill's methodology — it is faithful in structure and
behaviour, **not** a verified 100% duplicate.

Recovered architecture that this project mirrors:

- `data/` — `Block`, `ChatItem`, `Screen`, `Prefs`, `ProviderDef`, …
- `engine/` — `Llm` (Anthropic + OpenAI SSE), `LocalBackend` (the agent loop),
  15 built-in tools (`read` `write` `edit` `patch` `glob` `grep` `shell`
  `todowrite` `task` `skill` `question` `diagnostics` `webfetch` `websearch`
  … plus delete), `Catalog`, `ProviderStore`, `PermRules`, `Commands`,
  `PromptBuilder`, `ToolRepair`, `Sess`/`SessionStore`, MCP (`McpClient`,
  `McpManager`, `McpOAuth` with PKCE + `.well-known` discovery), `Notifier`,
  `VoiceAnnouncer`, `AgentService`.
- `ui/` — Compose screens: chat, sessions, settings, skills, MCP, permissions,
  provider backup, folder picker, advanced.

## Build

Requires JDK 17 and Android SDK 35. **A build cannot be run on the device this
source was written on** (no JDK / Gradle / Android SDK) — build it locally or in
CI:

```bash
# with a JDK 17 + Android SDK installed
./gradlew assembleRelease
# or open the folder in Android Studio and press Run
```

The Gradle wrapper JAR is not included; open the project in Android Studio (it
will provision the wrapper) or run `gradle wrapper` once with Gradle 8.11+.

### Build the APK in the cloud (no PC needed)

`.github/workflows/build-apk.yml` builds this repository on GitHub's runners,
which already ship the Android SDK. It is modelled on
[`ni554n/apk-builder-action`](https://github.com/ni554n/apk-builder-action) but
self-contained (it builds *this* repo instead of cloning a remote one).

1. Push this `ClozedCode/` folder to a GitHub repository.
2. **Actions → Build Android APK → Run workflow** (pick `assembleDebug` or
   `assembleRelease`; a release build is signed with a throwaway debug key).
3. Download the APK from the run's **Artifacts** section, or from the *Summary*.

## Using it

1. Settings → Providers & API keys → pick a provider, tap **API key**, paste a
   key, tap **Enable**. For a local model select `ollama` / `lmstudio` and set
   the endpoint (already pointing at `127.0.0.1`).
2. Settings → **Choose project folder** (defaults to
   `/storage/emulated/0/ClozedProjects`).
3. Chat. Slash commands: `/help /init /model /models /effort /compact /undo
   /new /sessions /connect /folder /clear`.

## Notes / follow-ups

- The real app also ships a handful of styles/colors in `resources.arsc` that
  were not fully decoded; the Compose theme here is a close approximation.
- A truly 1:1 clone would need the decrypted `classes3.dex` (desktop JADX/REA
  unpack) to match exact logic, prompts concatenation order and error strings.
