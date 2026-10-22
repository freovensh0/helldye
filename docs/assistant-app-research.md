# Android assistant app research

Status: research only. No app code has been written.
Date: 2026-10-22.
Target: a phone app that the user selects as the default digital assistant, opens with the system
assist gesture or a power-button long press (where the OEM supports it), and can read screen context
after the user enables it.

## 1. Executive summary

The app is feasible, but two constraints shape the whole design:

1. **The app cannot own the power button.** Android has no public API to register a long-press
   handler for `KEYCODE_POWER`. The system owns the gesture and routes it to the current default
   assistant. On Pixel and many stock builds the user must enable
   `Settings > System > Gestures > Press and hold power button > Digital assistant`. Some OEMs have a
   similar setting, some do not, and some reserve the gesture for their own assistant. The reliable
   fallback is the system assist gesture (long-press home on 3-button navigation, or the corner swipe
   on gesture navigation).
2. **Screen context is system-mediated and user-consented.** The app does not request a normal
   runtime permission for "read the screen". The user grants it in the same digital-assistant
   settings area, and the system decides what to send. The app must handle "no structure", "no
   screenshot", and "user disabled it" as normal states. On Android 17 / API 37 there is an
   additional normal permission for screen content in `AssistStructure`; see section 7.

The minimum viable architecture is four pieces:

- `VoiceInteractionService`: lightweight, always-running entry point declared in the manifest.
- `VoiceInteractionSessionService`: creates one session per interaction.
- `VoiceInteractionSession`: receives `onShow`, assist data, screenshots, and lifecycle callbacks.
- `AssistantActivity`: a `ComponentActivity` with Compose UI, launched over the current app.

`RoleManager.ROLE_ASSISTANT` is the supported way to ask the user to make the app the default
assistant. The system settings screen also exposes the screen-context toggles. Neither can be
enabled silently by the app.

## 2. What the user actually sees

### 2.1 Selecting the assistant

Typical path on Pixel/stock Android:

```
Settings > Apps > Default apps > Digital assistant app > HELLDYE
```

or the app can show the system role dialog:

```kotlin
val roleManager = getSystemService(RoleManager::class.java)
if (roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT) &&
    !roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
) {
    roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
}
```

The role request opens a system confirmation UI. On some OEM builds the role is unavailable or the
candidate list is restricted to preinstalled apps. Check `isRoleAvailable()` before showing the
button, and treat a negative result as a supported state with an explanation, not a crash.

To qualify for `ROLE_ASSISTANT`, an app must either implement `VoiceInteractionService` or handle
`Intent.ACTION_ASSIST`. Implementing `VoiceInteractionService` is the path that also provides
`AssistStructure`, `AssistContent`, and screenshots; an `ACTION_ASSIST` activity alone cannot receive
that session data.

Role privileges: the AndroidX `RoleManagerCompat` documentation states that the assistant role can
access call log and SMS for its functionality. Treat this as a privilege, not a requirement. Declare
only the permissions the product actually uses; unnecessary call-log/SMS access creates Play policy
and privacy risk.

### 2.2 Screen context permission

On Pixel/Google builds the digital-assistant page exposes toggles such as **Use text from screen**
and **Use screenshot** (also reachable from Assistant settings > General > Use screen context). Labels
and availability vary by OEM and Android version. The app cannot turn them on programmatically.

The app can open the system settings page:

```kotlin
startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
```

Check `resolveActivity()` first and fall back to `Settings.ACTION_SETTINGS` or
`Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS` on devices that do not handle the voice-input action.

At runtime, inspect what the user disabled:

```kotlin
val disabled = getUserDisabledShowContext() // bitmask of SHOW_WITH_ASSIST / SHOW_WITH_SCREENSHOT
```

The method reports global user settings, not per-app or transient restrictions. A missing flag or a
null value means "work without that data", never "ask again in a loop".

### 2.3 Power button

- The app cannot intercept `KEYCODE_POWER`, and there is no broadcast for a power long press.
- The system assist gesture is the supported trigger. The default assistant receives it.
- On Pixel 4+ with Android 12+, the user can choose
  `Settings > System > Gestures > Press and hold power button > Digital assistant`.
  The same screen also offers **Power menu**, in which case a long press will not open the assistant.
- Samsung, Xiaomi, OnePlus, and others map side-key gestures differently; some do not expose a
  third-party assistant option. Do not promise power-button support in the product copy without
  checking the target device.
- When a session is invoked from a physical button, `onShow` may include
  `SHOW_SOURCE_PUSH_TO_TALK` (API 29+). Use it as a hint to start listening immediately; do not rely
  on it as proof that the power button was used.
- Do not use an `AccessibilityService` to capture the power key or the screen. It is the wrong API,
  it is a policy risk, and it is unnecessary for a default assistant.

### 2.4 End-to-end flow for the first device

1. Install HELLDYE and open it once.
2. Tap **Set as default assistant**. The system role dialog appears; confirm.
   The same result is reachable through `Settings > Apps > Default apps > Digital assistant app >
   HELLDYE`.
3. On the same digital-assistant page, enable the screen-context options the device offers
   (**Use text from screen**, **Use screenshot**, or a single **Screen context** toggle).
4. On Pixel 4+ with Android 12+, open `Settings > System > Gestures > Press and hold power button` and
   choose **Digital assistant**.
5. Long-press the power button. The system starts HELLDYE; the app reads the assist state only if the
   user left screen context enabled.
6. If the device has no power-button option, use the system assist gesture (long-press home or the
   corner swipe) instead. The app receives the same session either way.

## 3. Platform building blocks

| API | Added | Role |
| --- | --- | --- |
| `VoiceInteractionService` | 21 | Always-running entry point; declares the session service and capabilities. |
| `VoiceInteractionSessionService` | 21 | Creates a `VoiceInteractionSession` for one interaction. |
| `VoiceInteractionSession` | 21 | Receives `onShow`, assist state, screenshots, and close/hide callbacks. |
| `VoiceInteractionSession.AssistState` | 29 | Bundles `AssistStructure` + `AssistContent`; replaces the deprecated three-argument `onHandleAssist`. |
| `RoleManager.ROLE_ASSISTANT` | 29 | System role for the default assistant; request through `createRequestRoleIntent`. |
| `startAssistantActivity(Intent)` | 26 | Launches the assistant activity on top, outside multi-window. |
| `SHOW_SOURCE_PUSH_TO_TALK` | 29 | Show flag hint for a physical-button invocation. |
| `READ_ASSIST_STRUCTURE_SCREEN_CONTENT` | 37 | Normal permission needed to receive screen content in `AssistStructure`. |
| `usesAssistStructureScreenContent` | 37 | Service XML flag declaring that the assistant uses that content. |
| `SHOW_WITH_ASSIST_STRUCTURE_SCREEN_CONTENT` | 37 | Show flag that requests screen content in the structure; combine with `SHOW_WITH_ASSIST`. |

Recommended platform baseline: **minSdk 29** so `RoleManager` and `AssistState` are available without
legacy branches. Use `compileSdk`/`targetSdk` 37. If minSdk 26 is required, override the deprecated
`onHandleAssist(Bundle, AssistStructure, AssistContent)` for API 26–28 and keep the modern path for
29+.

## 4. Architecture

### 4.1 Components and process model

```
System assist gesture / power gesture
        |
        v
HelldyeVoiceInteractionService        (process :interaction, lightweight, always running)
        |
        v
HelldyeSessionService                 (main process)
        |
        v
HelldyeSession                        (main process)
   |            |             |
   v            v             v
AssistState   Screenshot   Voice/TTS
   \            |            /
        AssistInbox (StateFlow, memory only)
                 |
                 v
AssistantActivity (Compose, main process)
```

Keep the always-running `VoiceInteractionService` free of UI and heavy work. Put the session service
and the assistant activity in the same process so a simple in-memory `AssistInbox` can carry the
state without Binder plumbing. If the session service is moved to a separate process later, replace
the singleton with an explicit bound service or IPC.

### 4.2 Session flow

1. The system binds `HelldyeVoiceInteractionService` because the app holds the assistant role.
2. A gesture starts `HelldyeSessionService.onNewSession()`.
3. `HelldyeSession.onPrepareShow()` runs before any UI. If the UI will be an activity, call
   `setUiEnabled(false)` so the session does not create a second window.
4. `onShow()` receives `showFlags`. Publish the show state and launch `AssistantActivity` with
   `startAssistantActivity(Intent)`. On API 26+ this places the activity above the current app in a
   dedicated layer; it is not affected by multi-window.
5. The system calls `onHandleAssist(AssistState)` for the focused activity and possibly additional
   activities in multi-window. It may call it more than once. Merge by `getIndex()`/`getCount()` and
   prefer `isFocused()`. `onHandleScreenshot(Bitmap)` follows when a screenshot was requested and
   allowed.
6. The Compose UI observes `AssistInbox` and updates from "reading context" to the answer.
7. On close, the activity finishes, the session calls `hide()`, `onHide()` clears the inbox and
   cancels speech/network work. Use `finish()` only when the session should be destroyed; `hide()` is
   the normal choice.

`onHandleAssist` can arrive after `onShow`. Do not block `onShow` waiting for it; show a loading state
and update when the data lands. `onAssistStructureFailure(Throwable)` means the transfer failed; fall
back to text-only or no-context mode.

### 4.3 State holder

`AssistInbox` is a process-local object with a `MutableStateFlow<AssistUiState>`:

```kotlin
data class AssistUiState(
    val visible: Boolean = false,
    val showFlags: Int = 0,
    val userDisabledFlags: Int = 0,
    val screenText: String = "",
    val webUri: String? = null,
    val screenshot: Bitmap? = null,
    val loading: Boolean = false,
)
```

Keep the bitmap in memory only. Clear the object in `onHide()` and when the activity finishes. Do not
persist screen text, screenshots, or URLs unless the user explicitly asks to save a result; if the
product needs history, store the user's question and the answer, not the captured screen.

## 5. Manifest and XML

Names, packages, and theme names below are placeholders; keep them aligned with the actual project.
The snippets are a skeleton, not compilable files.

### 5.1 `AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
<!-- API 37+: required for screen content in AssistStructure. -->
<uses-permission android:name="android.permission.READ_ASSIST_STRUCTURE_SCREEN_CONTENT" />

<application ...>
    <service
        android:name=".voice.HelldyeVoiceInteractionService"
        android:exported="true"
        android:permission="android.permission.BIND_VOICE_INTERACTION"
        android:process=":interaction">
        <intent-filter>
            <action android:name="android.service.voice.VoiceInteractionService" />
        </intent-filter>
        <meta-data
            android:name="android.voice_interaction"
            android:resource="@xml/helldye_voice_interaction" />
    </service>

    <service
        android:name=".voice.HelldyeSessionService"
        android:exported="true"
        android:permission="android.permission.BIND_VOICE_INTERACTION">
        <intent-filter>
            <action android:name="android.service.voice.VoiceInteractionSessionService" />
        </intent-filter>
    </service>

    <activity
        android:name=".ui.AssistantActivity"
        android:exported="false"
        android:excludeFromRecents="true"
        android:launchMode="singleTask"
        android:taskAffinity=""
        android:theme="@style/Theme.Helldye.Assistant" />

    <activity
        android:name=".ui.SettingsActivity"
        android:exported="true" />
</application>
```

Both services are bound by the system, so they need `android:exported="true"` and the
`BIND_VOICE_INTERACTION` permission. Missing either is the usual reason the app never appears in the
digital-assistant list.

### 5.2 `res/xml/helldye_voice_interaction.xml`

```xml
<voice-interaction-service
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:sessionService="dev.scdeal.helldye.voice.HelldyeSessionService"
    android:recognitionService=""
    android:settingsActivity="dev.scdeal.helldye.ui.SettingsActivity"
    android:supportsAssist="true"
    android:supportsLaunchVoiceAssistFromKeyguard="true"
    android:supportsLocalInteraction="true"
    android:usesAssistStructureScreenContent="true"
    tools:targetApi="37" />
```

Notes:

- `sessionService` is required.
- `recognitionService` is required by the schema. Since Android 12 it does nothing, but the platform
  still requires the attribute; the AOSP sample uses an empty string. Point it at a real
  `RecognitionService` if the app implements one.
- `supportsAssist="true"` is what makes the service an assist candidate.
- `supportsLaunchVoiceAssistFromKeyguard="true"` allows invocation from the lock screen.
- `settingsActivity` is optional but gives the system a place to send the user for setup.
- `usesAssistStructureScreenContent` is API 37+; older platforms ignore it. `tools:targetApi` keeps
  lint quiet.

## 6. Role request and onboarding

```kotlin
private val roleLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult(),
) { result ->
    if (result.resultCode == Activity.RESULT_OK) refreshAssistantState()
}

fun requestAssistantRole(activity: Activity) {
    val roleManager = activity.getSystemService(RoleManager::class.java)
    if (!roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
        showUnsupportedDeviceMessage()
        return
    }
    if (!roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
        roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
    }
}
```

Onboarding screen checklist:

- Role available? If not, explain that the device/OEM does not allow third-party assistants.
- Role held? If not, show one **Set as default assistant** button. Do not re-prompt on every launch.
- Screen context: show the current state from `getUserDisabledShowContext()` with an **Open assistant
  settings** button. Explain that the toggle is system-controlled.
- Microphone permission: request `RECORD_AUDIO` here, not during an assist session.
- Power button: on Pixel, show the exact path; on other devices, say "If your device supports it" and
  point to the assist gesture. Never claim the app can change it.
- A short privacy note: what screen data is read, where it goes, and how to turn it off.

## 7. Screen context in detail

### 7.1 What the system sends

- `AssistStructure`: a tree of `WindowNode`/`ViewNode` objects for the foreground activity. Each node
  can expose `text`, `contentDescription`, `hint`, `className`, `idEntry`, and bounds. Password fields
  are stripped by the platform; do not assume every field is safe to log.
- `AssistContent`: `webUri`, `intent`, and clip data. Useful when the foreground app is a browser.
- Screenshot: a `Bitmap` via `onHandleScreenshot()` when `SHOW_WITH_SCREENSHOT` is set and the user
  allows screenshots.
- `FLAG_SECURE` windows are excluded from screenshots; assist structure may also be limited. Treat a
  missing screenshot as normal.

### 7.2 Flags

```kotlin
val canReadStructure =
    showFlags and VoiceInteractionSession.SHOW_WITH_ASSIST != 0 &&
        disabledFlags and VoiceInteractionSession.SHOW_WITH_ASSIST == 0

val canReadScreenshot =
    showFlags and VoiceInteractionSession.SHOW_WITH_SCREENSHOT != 0 &&
        disabledFlags and VoiceInteractionSession.SHOW_WITH_SCREENSHOT == 0

val canReadScreenContent = Build.VERSION.SDK_INT >= 37 &&
    showFlags and VoiceInteractionSession.SHOW_WITH_ASSIST_STRUCTURE_SCREEN_CONTENT != 0
```

`SHOW_WITH_ASSIST_STRUCTURE_SCREEN_CONTENT` has no effect unless `SHOW_WITH_ASSIST` is also set; the
system sets these flags, the app reads them. `getUserDisabledShowContext()` is the app-facing signal
for the user's toggles.

### 7.3 API 37 change

Starting with API 37, a `VoiceInteractionService` that wants screen content in `AssistStructure` must
hold the normal permission `android.permission.READ_ASSIST_STRUCTURE_SCREEN_CONTENT`. Without it, the
docs say the structure contains only window data and omits screen content. Declare the permission and
set `android:usesAssistStructureScreenContent="true"` on the `<voice-interaction-service>` tag. This
is in addition to the user's screen-context toggle, not a replacement for it.

### 7.4 Extracting text

```kotlin
private fun AssistStructure.flattenText(): String = buildString {
    for (i in 0 until windowNodeCount) {
        appendNode(windowNodeAt(i).rootViewNode)
    }
}

private fun StringBuilder.appendNode(node: AssistStructure.ViewNode?) {
    if (node == null) return
    node.text?.let { appendLine(it) }
    node.contentDescription?.let { appendLine(it) }
    for (i in 0 until node.childCount) {
        appendNode(node.getChildAt(i))
    }
}
```

The assistant should send the smallest useful context: the focused window's text, the web URI, and a
screenshot only if the user enabled it and the product needs pixels. Truncate long text and drop
duplicate lines before sending it to a model.

### 7.5 Privacy and safety

- Screen content can contain messages, passwords, financial data, and personal photos. Do not log it,
  write it to disk, or upload it by default.
- Keep screenshots in memory; clear them on `onHide()` and when the activity stops.
- If the product sends content to a backend, require explicit consent, use TLS, keep the provider key
  on the server, publish a privacy policy, and complete the Play data-safety declaration.
- Do not use `AccessibilityService` or `MediaProjection` as a shortcut. The Assist APIs are the
  supported path; the alternatives are policy and privacy risks.
- Android 17 deprecates `ContentCaptureManager.setContentCaptureEnabled(false)` for targeting apps;
  `FLAG_SECURE` is the supported way for an app to keep its window out of system capture. This is
  relevant when testing what the assistant receives.

## 8. Voice input and output

- Use `SpeechRecognizer` with `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` for the MVP. It uses the
  system recognizer and avoids implementing a `RecognitionService`.
- A custom `RecognitionService` is only needed if the app wants to be the system speech recognizer or
  serve other apps. It is not required for the assistant role on phones.
- Request `RECORD_AUDIO` during onboarding. If it is missing at assist time, show a clear action
  ("Open settings") instead of failing silently.
- Use partial results for a live transcript; debounce and cancel the recognizer when the session
  hides.
- Use `TextToSpeech` for spoken answers; stop it in `onHide()` and when the activity is destroyed.
- Cancel network calls and TTS on hide. The session can be destroyed at any time.
- Do not ship a provider API key in the APK. Call a backend you control, with authentication and
  rate limits.

## 9. Compose UI

A full-screen assistant activity works better than a `ComposeView` inside the session window for the
MVP: it gets a normal lifecycle, ViewModel, and testing setup. The session only carries data.

Suggested screen structure:

```
+----------------------------------+
| HELLDYE                 context  |   context chip: on / off / partial
|                                  |
|          [ waveform ]            |
|       "Listening..."             |
|                                  |
|  transcript / answer             |
|  - what was asked                |
|  - what the assistant found      |
|                                  |
|  [ Cancel ]        [ Open mic ]  |
+----------------------------------+
```

Compose rules that matter here:

- `collectAsStateWithLifecycle()` for the session state.
- `LaunchedEffect(sessionId)` to start listening once; `DisposableEffect` to cancel speech/TTS.
- `BackHandler` closes the activity and calls the session close path.
- `enableEdgeToEdge()` and `imePadding()`; the overlay must not hide the input under the keyboard.
- Show real states: reading context, listening, thinking, answer, error, no microphone permission,
  screen context disabled.
- Do not use `rememberSaveable` for screen text or screenshots; they are sensitive and should not
  survive process death. A user-visible answer may be kept only if the product explicitly supports
  history.
- Accessibility: label the mic, the close button, and the context chip; do not rely on color alone.

## 10. Testing and debugging

### 10.1 Role and trigger commands

```sh
# Current assistant role holders
adb shell cmd role get-role-holders android.app.role.ASSISTANT

# Grant/remove the role for local testing (shell; may be restricted on some builds)
adb shell cmd role add-role-holder android.app.role.ASSISTANT dev.scdeal.helldye
adb shell cmd role remove-role-holder android.app.role.ASSISTANT dev.scdeal.helldye

# What the system thinks the assistant is
adb shell settings get secure assistant

# Trigger the assist key (KEYCODE_ASSIST = 219)
adb shell input keyevent 219
adb shell am start -a android.intent.action.ASSIST

# Inspect roles and the session service
adb shell dumpsys role
adb shell dumpsys activity service dev.scdeal.helldye/.voice.HelldyeVoiceInteractionService
```

The normal user path is the system role dialog. The `cmd role` path is for development and may not be
available on production builds.

### 10.2 Test matrix

| Case | Expected |
| --- | --- |
| Role available, not held | Onboarding shows one button; system dialog opens. |
| Role held | Triggering assist opens the overlay, no chooser. |
| Role removed while app is open | App does not crash; session ends; onboarding shows the new state. |
| Power long press set to Digital assistant (Pixel) | Overlay opens; `SHOW_SOURCE_PUSH_TO_TALK` may be set. |
| Power long press set to Power menu | Power menu opens; app does not receive the gesture. Use the assist gesture. |
| Screen context off | Overlay still works; shows a "context off" state; no structure/screenshot. |
| Text on, screenshot off | Text/URI present; screenshot null. |
| Screenshot on | Bitmap present unless the foreground window is `FLAG_SECURE`. |
| Browser foreground | `AssistContent.webUri` populated when the browser provides it. |
| Secure banking app | No screenshot; structure may be limited. |
| Multi-window | Multiple `AssistState` callbacks; focused one wins. |
| Rotation / process death | No crash; sensitive state is not restored from disk. |
| API 37 device | Permission declared; screen content present when the toggle is on. |
| API 29–36 device | No API 37 permission/attr crash; legacy behavior works. |

### 10.3 Logs

Useful tags: `VoiceInteractionService`, `AssistGesture`, `AssistStructure`, `RoleManager`,
`VoiceInteractionSession`. Filter logcat by your package and by these tags. Do not log the captured
text or bitmap.

## 11. Risks and open questions

**Platform risks**

- OEMs may restrict the assistant role to preinstalled apps; `ROLE_ASSISTANT` can be unavailable.
- Power long-press behavior is OEM- and version-specific; the product cannot guarantee it.
- The screen-context toggles may be absent for third-party assistants on some builds.
- Screen data can be text-only, screenshot-only, or absent; design for all three.
- The assistant role can carry call-log/SMS privileges; use the minimum privilege set.
- Play policy may treat an assistant that uploads screen content as sensitive; verify policy and
  data-safety requirements before release.

**Product questions for the next phase**

1. Which phone/OEM and Android version is the first target? This decides whether power long-press is
   available at all.
2. Is the assistant cloud-backed, on-device, or both? This decides the privacy and latency design.
3. Should screen context be text-only for the first release? It is lower risk and cheaper than
   screenshots.
4. Does the product need keyguard invocation, or only unlocked use? Keyguard adds security review.
5. Is a hotword required? It is a separate, much heavier feature (`AlwaysOnHotwordDetector`); the MVP
   does not need it.

## 12. MVP plan

| Phase | Deliverable | Acceptance |
| --- | --- | --- |
| 0 | Project skeleton, manifest, role request, onboarding | App appears in Digital assistant app and can be selected. |
| 1 | `VoiceInteractionService`, session service, `AssistantActivity` | `adb shell input keyevent 219` opens the Compose overlay; close returns to the previous app. |
| 2 | SpeechRecognizer + TextToSpeech | User speaks, sees a transcript, hears an answer; cancel works. |
| 3 | Assist structure + content + optional screenshot | With context on, the overlay shows screen text/URI; with it off, the UI says so and still works. |
| 4 | Backend/LLM, streaming, errors | Answer streams; cancel stops the request; no key in the APK. |
| 5 | Power-button onboarding, settings, test matrix | Pixel power gesture documented and tested; OEM fallback documented; role loss handled. |
| 6 | Privacy review, Play data safety, release build | No screen content in logs/storage; policy checklist complete. |

## 13. Recommended baseline

- `minSdk 29` (Android 10) for `RoleManager` and `AssistState`; use 26 only if legacy support is a
  hard requirement.
- `compileSdk`/`targetSdk` 37; guard API 37 features with `Build.VERSION.SDK_INT >= 37`.
- AGP, Gradle, Kotlin, JDK: the newest stable set that the compatibility matrix allows. At the time
  of writing the local projects use AGP 9.x, Kotlin 2.4.x, JDK 25, and Compose BOM 2026.06.00; verify
  the current stable versions before scaffolding rather than copying these numbers blindly.
- Compose BOM for all Compose artifacts; no individual Compose versions.
- Dependencies for the MVP: `androidx.core:core-ktx`, `androidx.activity:activity-compose`,
  `androidx.lifecycle:lifecycle-runtime-compose`, `androidx.lifecycle:lifecycle-viewmodel-compose`,
  `androidx.compose.material3:material3`, `androidx.compose.ui:ui-tooling-preview`. No DI, database,
  or navigation library until a second caller justifies it.
- Permissions: `RECORD_AUDIO`, `INTERNET`, `READ_ASSIST_STRUCTURE_SCREEN_CONTENT` (API 37), optional
  `POST_NOTIFICATIONS` for setup reminders.

## 14. Sources

- [VoiceInteractionService](https://developer.android.com/reference/android/service/voice/VoiceInteractionService)
- [VoiceInteractionSessionService](https://developer.android.com/reference/android/service/voice/VoiceInteractionSessionService)
- [VoiceInteractionSession](https://developer.android.com/reference/android/service/voice/VoiceInteractionSession)
- [VoiceInteractionSession.AssistState](https://developer.android.com/reference/android/service/voice/VoiceInteractionSession.AssistState)
- [RoleManager](https://developer.android.com/reference/android/app/role/RoleManager)
- [RoleManagerCompat](https://developer.android.com/reference/androidx/core/role/RoleManagerCompat)
- [AssistStructure](https://developer.android.com/reference/android/app/assist/AssistStructure)
- [AssistContent](https://developer.android.com/reference/android/app/assist/AssistContent)
- [READ_ASSIST_STRUCTURE_SCREEN_CONTENT](https://developer.android.com/reference/android/Manifest.permission#READ_ASSIST_STRUCTURE_SCREEN_CONTENT)
- [usesAssistStructureScreenContent](https://developer.android.com/reference/android/R.attr#usesAssistStructureScreenContent)
- [AOSP voice interaction app development guide](https://source.android.com/docs/automotive/voice/voice_interaction_guide/app_development)
- [AOSP voice interaction overview](https://source.android.com/docs/automotive/voice/voice_interaction_guide)
- [AOSP VoiceInteractionService sample](https://android.googlesource.com/platform/development/+/refs/heads/main/samples/VoiceInteractionService/)
- [AOSP VoiceInteractionService attrs](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/res/res/values/attrs.xml)
- [Android 17 behavior changes](https://developer.android.com/about/versions/17/behavior-changes-17)
- [Google Assistant: screen context](https://support.google.com/assistant/answer/7393909)
- [Pixel: Assistant with the power button](https://support.google.com/pixelphone/answer/9475056)
- [AOSP Role shell commands](https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/main/PermissionController/src/com/android/permissioncontroller/role/Role.md)
