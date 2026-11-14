# Swing Analyzer (MVP)

An offline Android app that analyses a golf swing video from your gallery and gives you advice you can save and export as text. Built to `swing-analyzer-architecture.md` (v0.5); the golf knowledge behind every rule is in `swing-knowledge-base.md`.

- Pick a video → choose club (full bag) and shot → on-device analysis → feedback, tempo, key frames, numbers → save a note with your comment → export `.txt` or share.
- No internet permission; no video is kept, only numbers and four key-frame images per swing.
- Target: Samsung Galaxy S24 Ultra, Android 14+.

## Project layout

| Path | What it is |
|---|---|
| `core/` | Pure Kotlin analysis: key positions, shaft finder, metrics, rules, coaching text, notes, export. No Android dependency. |
| `core/src/main/resources/knowledge.json` | Rules, club profiles, fault texts and drills (knowledge base §11). Edit thresholds here. |
| `core/src/test/` | Acceptance checks on the sample swing (architecture §14), plus synthetic early-extension and left-handed tests. |
| `app/` | Android app: gallery picker, frame decoding, MediaPipe Pose Landmarker, Compose screens, storage, export. |
| `app/src/main/assets/pose_landmarker_heavy.task` | MediaPipe pose model (Apache 2.0), bundled so the app works offline. |
| `tools/make_fixtures.py` | Regenerates the test fixtures from a sample video. |

## Build the APK

Either route needs internet once, to download Gradle, the Android SDK and the libraries. The app itself never does.

**Option A: GitHub (no Android Studio needed)**
1. Push this folder to a GitHub repository.
2. The **Build APK** workflow runs automatically: it runs the core tests, then builds the app.
3. Open the run → **Artifacts** → download `swing-analyzer-apk`, unzip, copy the `.apk` to the phone and open it (allow "install unknown apps" for your file manager when asked).

**Option B: Android Studio**
1. Install Android Studio, then *Open* this folder and let Gradle sync.
2. Connect the phone with USB debugging on, or use *Build → Build App Bundle(s) / APK(s) → Build APK(s)*.
3. Core tests on their own: `./gradlew :core:test`.

## Using it

1. **Settings:** height (only for showing centimetres), right- or left-handed, usual camera view, the clubs in your bag.
2. **Analyze → Choose swing video.** Pick the original clip, not one trimmed in the gallery editor.
3. Choose the club, the shot (full swing or chip for short irons and wedges; the putter is always a putt), the view, and the speed (normal or slow-motion factor).
4. Read the result, add a comment, tick *reference swing* if it's one to compare against later, and **Save note**.
5. **Notes** tab: filter by club, open a note, export it or all of them as `.txt`, share, or delete.

## Status

- **Core:** compiled and run here against the sample swing: 25 of 25 checks pass (events within the §14 tolerances, tempo 3.5:1 from the shaft finder, no false faults, early extension flagged when injected, left-handed mirror matches).
- **Android layer:** written but not yet compiled; the build environment used to write it couldn't reach Google's Android repositories. The first GitHub or Android Studio build is its first compile.
- **Not yet built:** sound-based swing finding, comparison with your reference swing, and the optional on-device language model (architecture §12).
- **Face-on view:** rules are implemented, but they haven't been checked on a real face-on clip.
- **Shaft finder:** tested against open sky; test it in front of a net.
