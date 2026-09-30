# Flow Dictation

A personal, free dictation app for Android. Tap a floating mic bubble, talk, tap again.
Your speech is transcribed by Groq Whisper, cleaned up by a fast Groq LLM (punctuation,
filler words, quotes, lists, self-corrections), and typed into whatever text field you were using.

Built for a Samsung Galaxy A36, sideloaded (not for the Play Store).

## 1. Put it on GitHub (once)

1. Create a **new public repository** on github.com (public = free, unlimited build minutes).
2. Upload the **contents** of this folder so that `settings.gradle.kts` sits at the top of the repo
   (not inside a sub-folder). Drag everything in, including the hidden `.github` folder.
   - Mac: press Cmd+Shift+. in Finder to show hidden folders.
   - If `.github` won't upload: in the repo click **Add file > Create new file**, type
     `.github/workflows/build.yml` as the name, and paste in the contents of that file.
3. Never commit your Groq key. You type it into the app on your phone, so it never touches GitHub.

## 2. Build the APK

1. In the repo open the **Actions** tab. If asked, click the button to enable workflows.
2. Pick **Build APK** on the left, then **Run workflow**.
3. Wait about 4 to 8 minutes. When it turns green, open the repo's **Releases** section (right side of the repo page).
4. Open that release **on your phone** and download `FlowDictation.apk`.

If the run turns red, open it, click the failed step, and send the last ~40 lines of the log to Claude.

## 3. Install on the Galaxy A36

- Tap the downloaded APK. Allow your browser to "install unknown apps" if asked.
- If Google Play Protect warns you, choose **Install anyway** (or "More details", then Install anyway).
- If the install is blocked outright, turn off Samsung **Auto Blocker**:
  Settings > Security and privacy > Auto Blocker > off (you can turn it back on afterwards).
- To update later: run the workflow again and install the new APK over the old one. Settings are kept.

## 4. First-run setup (the app walks you through it)

1. **Microphone**: tap Allow.
2. **Notifications**: tap Allow (shows the small "ready" notification).
3. **Accessibility service**: tap the button, open Installed apps, tap Flow Dictation, turn it on.
   - If Android says "Restricted setting": go back, tap **Open app info**, tap the three dots (top right),
     choose **Allow restricted settings**, then turn the service on. The three-dot option only shows up after
     you have tried to enable the service once.
4. **Groq key**: paste it, tap **Save & test connection**. Green checks mean you are good.
5. **Keep it running**: tap Allow background use (stops Samsung sleeping the app).
6. **Engine**: it starts automatically when you open the app. The status should say Running.
7. Tap the "Try it" box, and the mic bubble appears above the keyboard. Tap it, speak, tap again.

## Using it

- Tap the bubble to start, tap again to stop. Drag it anywhere; it snaps to the screen edge.
- Long-press the bubble while recording to cancel.
- Say "new line", "new paragraph", "bullet point", "colon", or "quote ... end quote".
- After a phone restart, open the app once so the engine starts again (Android does not allow
  microphone services to start by themselves after boot).

## Troubleshooting

- **No bubble**: turn off "Only show the mic button when the keyboard is open" in the app.
- **Bubble is faded**: the engine is off. Open the app and tap Start.
- **Text didn't appear but no error**: it is on your clipboard, so long-press and paste. Some apps block automatic typing.
- **"Groq rejected the API key"**: re-paste the key and press Save & test.
- **"Cleanup model not found"**: Groq changes its free models now and then. Test connection lists the current
  ones; paste one into Advanced > Cleanup model. Dictation still works without cleanup (raw text is used).
- **Slow**: lower the cleanup timeout in Advanced, or switch AI cleanup off for instant raw transcripts.

## Privacy

Audio goes to Groq for transcription and the text goes to Groq for cleanup, nothing else. The accessibility
service only looks for the focused input field to insert text. It does not read or store anything on screen and
skips password fields. Your API key is stored in the app's private storage on the phone.

## Roadmap

- Local Parakeet model (offline mode) via sherpa-onnx
- Compressed audio upload for faster uploads on slow mobile data
- Per-app tone presets
