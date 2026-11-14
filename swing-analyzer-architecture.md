# Swing Analyzer: architecture

Version 0.5 · 14 November 2026 · MVP built (project `swing-analyzer/`)

Changes in 0.5: the MVP is built to this design (section 19). Swing advice now comes from `swing-knowledge-base.md`, which holds the research, the fault catalogue, the drills and every threshold. The shaft finder was rebuilt so it no longer needs the line to pass through the wrists. The balanced-finish check now uses the hips only, and notes are stored as small files instead of a database.

Changes in 0.4: first test results on your sample clip (section 15) and the design changes they forced: a shaft finder for the top of the swing, hand-midpoint rules, no hand checks in the down-the-line follow-through, a new first-movement rule, and corrected tempo figures.

## 1. What the app does

1. You pick a swing video from your phone's gallery.
2. The app asks which club you used, from your full bag.
3. It analyses the swing (form at each key position, plus tempo) and gives you a short list of things to work on.
4. You can save that feedback as a note with the club recorded on it, and export notes as text files.

**Constraints.** Samsung Galaxy S24 Ultra, everything on the device, no internet needed to use it. The app stores key frames and numbers, never the video.

**Status of this design.** I inspected your sample clip frame by frame, read the source of Sports2D and GolfDB, and checked current documentation for the on-device parts and the phone. Body tracking, key positions and tempo have now been tested on the sample clip on a computer with the same MediaPipe models the phone would use (section 15). Nothing has run on the phone yet.

## 2. Your answers and what they settled

| Question | Your answer | Decision |
|---|---|---|
| Where it runs | Android phone, local, no internet | Native Android app; all analysis on the phone; no network permission |
| Which phone | Galaxy S24 Ultra | Heavy tracking model with graphics acceleration; Android 14 as the minimum; an on-device language model is practical (section 4) |
| Which language model | My recommendation | Built-in templates first. Optional later: Gemma 3 1B through LiteRT-LM |
| Test material | The clip you gave me | That clip is the acceptance test (section 14) |
| Clubs | Full bag | Every club selectable from day one; three anchor profiles plus the putter |
| What to keep | Key frames and numbers only | No video copy and no link to the video is stored (section 13) |

Knock-on changes from version 0.1: body tracking moved from Sports2D to MediaPipe Pose Landmarker, because running a desktop Python package inside an Android app isn't practical. Sports2D remains the reference for angle definitions and a desktop cross-check. SwingNet is converted once to a mobile format and joins as a cross-check, after landmark-based key positions work without it.

## 3. What the sample clip tells us

| Property | Your sample | What it means for the design |
|---|---|---|
| View | Down-the-line, right-handed, tripod-steady | The down-the-line check set applies. Face-on checks need a second clip. |
| Size | 1280×720, golfer about 60% of frame height | Fine for body tracking. Too small for SwingNet unless cropped first. |
| Frame rate | 30 fps, 115 frames, 3.8 s | The downswing lasts about 7–10 frames, depending on where "the top" is placed, and the club is a blur through impact. Form at address, top and finish is readable; tempo is coarse. |
| Top of swing | Hands still from frame 45 to 53; the shaft's angle barely changes from 46 to 50 and the club starts down at about frame 51.4 | "The top" is a stretch, not an instant. Only the club tells you when the downswing starts (section 8, stage 4). |
| Tempo, measured | Backswing 1.03 s, downswing 0.30 s, total 1.33 s | About 3.5:1 (3.4–3.7 across four test runs). This replaces my earlier eyeball estimate of "close to 3:1". |
| Content | One swing, already trimmed, one person | The swing finder has nothing to do here, but raw gallery clips will need it. |
| Audio | Loudest spike at about 2.14 s; impact in the picture measured at 2.01 s | Sound trails picture by 0.13 s, far more than sound travel explains. Sound can confirm and locate a strike but can't set the impact frame. |
| Encoding | Re-encoded export; no camera metadata | Slow motion can't be detected from this file. The app assumes normal speed and lets you correct it. |

## 4. Target phone: Galaxy S24 Ultra

| Property | Value | What it means for the design |
|---|---|---|
| Release | January 2024 | You wrote "2023 model". The 2023 Ultra is the S23 Ultra. The design fits it too, with somewhat slower analysis. |
| Chip and memory | Snapdragon 8 Gen 3, Adreno 750 graphics, 12 GB | The heavy tracking model with graphics acceleration is the default. A 4-second clip should take seconds to analyse; to be measured. |
| Android | Shipped with Android 14, with up to seven major upgrades | The app can require Android 14 or newer, which removes old-version workarounds. |
| Video modes | 1080p at 30, 60 and 240 fps; 4K at 30, 60 and 120 fps | Clips at 120 or 240 fps shrink tempo uncertainty four to eight times compared with the sample. |
| On-device language model | One of Google's own LiteRT-LM benchmark phones. Gemma 3 1B (about 1 GB) generates 24–33 tokens a second on it. | The optional model is practical: roughly ten seconds for a feedback paragraph, by my estimate from those figures. |
| Built-in Gemini Nano | The S24 series is not on Google's device list for its on-device Gemini Nano tools | That route is closed for this phone. LiteRT-LM with Gemma is the only option. |

**For when you analyse your own recordings on this phone**

- Pick the original file, not one trimmed in Samsung's Gallery editor. S23 and S24 Ultra owners have reported trimmed exports with altered frame rates.
- Use at least 60 fps in normal video mode; 120 fps is better where the camera offers it. Both keep real timing and sound.
- Slow motion at 240 fps gives the most detail. Samsung phones have been reported to save slow-motion clips as stretched 30 fps files with no record of the capture rate, so the app can't rely on the file to say so (section 8, stage 1).

## 5. Decisions in one page

1. **Native Android app, no internet permission.** The app never asks Android for network access, so nothing can leave the phone. All models ship inside the app.
2. **Body tracking on the phone with MediaPipe Pose Landmarker.** It is Google's on-device pose tool, takes decoded video frames, and returns 33 body landmarks including wrists, heels and toes.
3. **The repos become models and specifications, not running software.** SwingNet's weights are converted once on a computer. Sports2D's angle definitions and processing order are re-implemented, and Sports2D on a desktop checks the phone's numbers.
4. **Key positions come from landmark cues first, plus a shaft finder at the top.** Landmarks work for every club, including the putter; the shaft finder supplies the start of the downswing, which tempo depends on. SwingNet joins later as a second opinion for full swings.
5. **Rules decide; templates write.** Fixed rules find the faults; built-in templates turn the top findings into advice. No language model is needed.
6. **Club, shot type and camera view choose the checks.** Three anchor profiles cover the whole bag; the putter has its own.
7. **Every number carries a confidence.** When the clip can't support a judgement, the app says so.
8. **Key frames and numbers are the record.** The video is read once and never copied.
9. **The analysis logic is plain Kotlin with no Android dependency,** so most of it can be built and tested on your computer against data recorded from the sample clip.

## 6. System overview

```
 ┌───────────────────── Galaxy S24 Ultra ────────────────────────┐
 │  Screens                                                      │
 │  (pick video · choose club · result · notes · my bag)         │
 │        │                                                      │
 │        ▼                                                      │
 │  Analysis job (keeps running with the screen off)             │
 │    1  Intake and quality checks   ← Android video and audio   │
 │    2  Swing finder                                            │
 │    3  Body tracking               ← MediaPipe Pose Landmarker │
 │    4  Key positions               ← landmark cues             │
 │                                     (+ SwingNet, + sound)     │
 │    5  Measurements (form + tempo)                             │
 │    6  Findings                    ← rules × club profile      │
 │    7  Coaching text               ← templates                 │
 │        │                                                      │
 │        ▼                                                      │
 │  Storage: database (analyses, notes, bag, settings)           │
 │           + files (key-frame images, landmark tracks)         │
 │  Export:  system "save as" dialog and share sheet             │
 └───────────────────────────────────────────────────────────────┘
   Build time only, on a computer: convert SwingNet, bundle models,
   run Sports2D on the sample clip as a reference.
```

| Module | Responsibility | Depends on Android? |
|---|---|---|
| Screens | Jetpack Compose interface, navigation, progress | Yes |
| Media | Gallery picker, frame-by-frame decoding with timestamps, audio decoding | Yes |
| Vision | Pose engine and key-position model, each behind its own boundary so they can be swapped | Yes |
| Analysis | Key positions, measurements, tempo, findings | No |
| Coaching | Templates, drill library, optional model | No (model part: yes) |
| Data | Database, files, text export | Yes |
| Bundled data | Models, club profiles, rule list, drill library, kept as editable files | – |

Stack: Kotlin, Jetpack Compose, the system Photo Picker, Android's built-in video decoder, MediaPipe Tasks (vision), ONNX Runtime for Android (for SwingNet, later). Storage is small JSON files in the app's private folder plus shared preferences; the MVP uses no database, which keeps the build simple. Minimum Android 14. Building and installing needs a computer with Android Studio and an internet connection; using the app does not.

## 7. Screens and flow

1. **New analysis.** "Choose video" opens the system gallery picker, which needs no storage permission. Then the club question, showing the clubs in your bag.
2. **Shot type.** Asked only when it's ambiguous: wedges and short irons get "full swing or chip/pitch". The putter is always a putt.
3. **Confirm what was guessed.** View (down-the-line or face-on), handedness, and speed (normal, 4× or 8× slow motion). Pre-filled, one tap to correct.
4. **Progress.** Checking clip, finding the swing, tracking your body, finding key positions, measuring, writing feedback.
5. **Result.** Feedback at the top; a strip of key frames with the skeleton drawn on; tempo; the numbers behind each point; a "couldn't judge from this clip" list. While this screen is open, the video can also be played with the skeleton drawn over it. Buttons: Save note, Export .txt, Share, Add my own comment.
6. **Notes.** Newest first, filter by club. Open, export, share, delete. "Export all" gives one combined file and doubles as a backup.
7. **My bag and settings.** Tick the clubs you carry; height; handedness; default view.

## 8. The analysis pipeline

| # | Stage | What it does | Built on | Produces |
|---|---|---|---|---|
| 1 | Intake | Reads size, rotation, duration, each frame's timestamp, frame rate, capture rate and audio. Runs quality checks. | Android media tools | Clip facts, warnings |
| 2 | Swing finder | Finds the swing and its time window. Asks which one if there are several. | Sound spikes; a coarse tracking pass | Time range |
| 3 | Body tracking | 33 landmarks per frame, in picture coordinates and in rough 3D. | MediaPipe Pose Landmarker | Landmark tracks |
| 4 | Key positions | Swing events, each with a confidence. | Landmark cues; shaft finder at the top; SwingNet; sound | Event times |
| 5 | Measurements | Form at each event, movement between events, tempo. | Own logic; Sports2D's angle definitions | Metrics with confidence |
| 6 | Findings | Compares with the club profile and your reference swing; ranks. | Rules and profiles | Ranked findings |
| 7 | Coaching text | Advice with one drill per point. | Templates | Feedback text |

### Stage 1: intake and quality checks

- No re-encoding. Frames are decoded straight from the gallery file, turned upright, and mirrored in memory for left-handers so everything downstream assumes right-handed.
- **Slow motion.** Android video files can carry a capture rate that differs from the playback rate, and the app uses it when present. Because Samsung slow-motion clips may not carry it, the app also checks plausibility: a swing whose first movement to impact takes several seconds is flagged, and you confirm normal, 4× or 8×.
- **Checks, each a plain warning:** whole body in frame throughout; golfer at least a third of the frame height; camera steady; one person clearly most prominent; frame rate below 60 fps flags tempo and impact as coarse.
- Frames are processed one at a time and discarded. Only landmarks, key frames and results are kept, so memory stays small.

### Stage 2: swing finder

Sound gives the cheapest anchor: the strike is a short, loud spike. Hitting into a net adds a second bang when the ball lands, so the detector takes the first of a close pair. With no usable sound, a quick pass over every third or fourth frame finds the burst of hand speed. Full tracking then runs only on the few seconds around the swing, which matters for long or high-frame-rate clips.

### Stage 3: body tracking settings

| Setting | Choice | Why |
|---|---|---|
| Model size | Heavy | Nothing here is live, and this phone has the power. Full is the fallback if timing disappoints. |
| Acceleration | Graphics processor, with the main processor as fallback | Faster on this chip; results must match either way. |
| Mode | Video mode, each frame passed with its own timestamp | The tracker follows the body from frame to frame, and real timestamps keep slow motion honest. |
| People | One | The default. The golfer should be the most prominent person. |
| Kept per frame | Picture landmarks with visibility scores; 3D landmarks | Picture landmarks drive the checks. The 3D ones only feed rough turn estimates. |
| Clean-up | Fill short gaps, reject outliers, then smooth lightly | Same order as Sports2D, but with a gentler filter than its 6 Hz default, which suits walking, not a downswing. |
| Scale | Distances divided by torso length; your height (1.77 m) only where metres are shown | Removes the effect of camera distance. |

Angles follow Sports2D's published definitions (trunk from hip midpoint to shoulder midpoint, knee at 0° when straight, and so on), so a desktop Sports2D run on the same clip gives numbers that can be compared directly.

**Rules from the tests (section 15):**

- **Hands means the midpoint of both wrists.** Near impact the tracker's left and right wrist points split apart or land on the hip (frames 57–58 and 61), while their midpoint stays within about 25 px of the real hands.
- **No hand checks after impact in the down-the-line view.** From frame 63 to 67 the hands are hidden behind the body and the tracker guesses 50–80 px wrong. Finish checks resume once the hands reappear and come to rest.
- Hips, shoulders, knees and ankles were tracked with full confidence on every frame.

### Stage 4: key positions

| Event | Landmark cue | Shaft finder | SwingNet, when added | Sound |
|---|---|---|---|---|
| Address | Last still frame before the backswing | – | Weak | – |
| Toe-up | None: defined by the shaft | Later | Only source | – |
| Mid-backswing | Hands pass chest height, going up | – | Cross-check | – |
| Top and start of downswing | Hands' still stretch at the top: a fallback only | First frame the shaft steepens past its flattest angle | Cross-check | – |
| Mid-downswing | Hands pass chest height, coming down | – | Cross-check | – |
| Impact | Hands' lowest point near their address height | – | Cross-check | Confirms a strike |
| Mid-follow-through | None: defined by the shaft | Later | Only source | – |
| Finish | Hands at rest for 0.1 s after impact | – | Weak | – |

**How each cue works, as tested:**

- **First movement.** Find the moment the hands are clearly rising (15% of torso length above where they started), then step back to the last frame where they were still. A fixed starting position fails, because the tracked hands drift by up to 2% of torso length while you stand at address.
- **Impact.** The hands' lowest point after the top, refined between frames with a curve through the three frames around it.
- **Finish.** The first moment after impact when hand speed stays low for 0.1 s. A finish counts as balanced when the hips stay still for 0.3 s; the head is ignored because it normally keeps turning to watch the ball.
- **Shaft finder (new, required for tempo).** Around the top the shaft is a thin, straight line rising above the hands, darker or lighter than the pixels either side of it. The app searches every upward direction, and every line passing up to 0.3 torso lengths beside the wrists, because the wrists sit beside the grip rather than on the shaft line. It scores only the stretch of line next to the hands. On the sample this finds the shaft on all 20 frames from 38 to 57. The downswing starts at the first frame where the shaft is clearly steeper than its flattest angle (4° beyond the three flattest readings). This matches the Tour Tempo definition: the first frame where the club head starts back toward the ball.
- **Shaft finder limits.** It was tested only against open sky. In front of a net, a hedge or a wall it may fail; it then reports nothing, and the app falls back to the hands with a wide tempo range and a warning. SwingNet, when added, becomes the second fallback.
- Landmark cues give six of the eight positions; the shaft finder gives the start of the downswing. Toe-up and mid-follow-through need the full shaft and wait for later.
- Events must be in order. Where two sources disagree by more than a tolerance tied to the frame rate, the more confident one wins and the event is marked uncertain.
- Sound never sets the impact time unless it lands within a frame or two of the picture-based estimate.
- Putts and chips use landmark cues only: start, end of backstroke, impact, end. SwingNet was trained on full swings.
- One key-frame image is saved per position, with the skeleton drawn on. These images are the only pictures the app keeps.

**Adding SwingNet (later milestone).** On a computer, once: load the published weights, convert to a mobile format, and confirm the converted model picks the same frames as the original on the sample clip. In the app: crop each frame around the golfer using the landmarks, with headroom for the club, then resize to 160×160. Without the crop you would be about 54 pixels tall in the sample. High-frame-rate clips are fed to it at 30–60 fps and refined on the full-rate landmarks.

### Stage 5: tempo

- **Definition.** Backswing runs from the first movement to the moment the club starts down; downswing from there to impact. The usual full-swing reference is 3:1, with first movement to impact around 0.9–1.2 s among tour players. Chips, pitches and putts use 2:1.
- **The top must come from the club.** Measured on your sample, the same swing reads anywhere from 2.0:1 to 5.6:1 depending on which "top" is used. Using the moment the hands reach their highest point gives 2.0–2.6:1; using the moment the hands start down gives 5.2–5.6:1. Only the shaft finder's "club starts down" gives a consistent figure (3.4–3.7:1). When the shaft isn't found, the app shows the hand-based range and says the top is uncertain.
- **Pause at the top** is reported separately: the time the hands stay still (0.27 s in the sample).
- **Precision.** Shown in seconds and in the common "frames at 30 fps" form, always with a range. Each event is refined between frames: first movement is placed half a frame before the first moving frame, impact by a curve through three frames, and the start of the downswing by interpolating where the shaft crosses its threshold. Tested on the sample by re-sampling at 15 fps with two different frame offsets, and with two tracking models, the ratio stayed between 3.41 and 3.67 and impact between 2.005 and 2.014 s. Rounding every event to whole frames instead gave 3.75–4.0, so the refinement matters.

## 9. How the repos are used now

| Source | Role on Android | What carries over | Licence |
|---|---|---|---|
| MediaPipe Pose Landmarker | Runs in the app: body tracking | The model files and Android library, unchanged | Apache 2.0 |
| wmcnally/golfdb (SwingNet) | Runs in the app after a one-time conversion | Published weights; its crop, resize and decoding steps re-implemented | CC BY-NC 4.0: personal use only |
| davidpagnon/Sports2D | Not in the app. Specification and desktop reference | Angle definitions, clean-up order, the quality advice on frame rate; reference numbers for the sample clip | BSD-3-Clause |
| Strojove-uceni swing-improvement project | Ideas for rules. It also used MediaPipe for golf. | The pattern "position, check, plain message" | None stated on its page, so ideas only |
| sanjeevs/golftracker, MingHanLee/GolfPose | Later: club tracking | Nothing in the first version | To check when needed |

SwingNet conversion notes: the published code assumes an NVIDIA GPU and needs small changes to run on an ordinary processor before it can be exported; it needs two weight files, one on Google Drive and one from a separate MobileNetV2 repo; it has about five million parameters by my count, so it's small enough to bundle. Its published weights score 71.5% across all eight events, which is why it only ever cross-checks.

If MediaPipe loses the body through the downswing, the fallback is the model family Sports2D itself uses (RTMPose), which is distributed in a format ONNX Runtime can load on Android. That costs more work, so it stays a fallback.

## 10. What gets measured

Movement is always relative to the address frame.

**Down-the-line (camera behind you, looking at the target)**

| Position | Check | Reliability |
|---|---|---|
| Address | Forward bend of the trunk; knee flex; arms hanging under the shoulders; hips over mid-foot | High |
| Backswing to top | Forward bend kept; head rise or drop; trail knee keeps its flex; hand height and depth at the top | Medium |
| Downswing to impact | Early extension (hips moving toward the ball, trunk standing up); head height; hand path coming down outside the path going up (over the top) | Medium |
| Finish | Held balance; trail heel up; fully turned | High |

**Face-on (camera facing your chest)**

| Position | Check | Reliability |
|---|---|---|
| Address | Stance width against shoulder width; spine tilt away from the target; hand position | High |
| Backswing to top | Sway (hips sliding away from the target); head drift; lead arm straightness; trunk leaning toward the target at the top | Medium |
| Downswing to impact | Hip shift toward the target; lead leg firming up; head behind the ball; lead elbow folding early after impact | Medium |
| Finish | Weight over the lead foot; balance held | High |

**Chips, pitches and putts (basic set):** head and lower body staying still, stroke length back against through, tempo against 2:1.

**Rough only:** hip and shoulder turn, from the 3D landmarks. Shown as "more" or "less" than your reference, never as degrees.

**Not measurable in the first version:** club face, club path, shaft plane, wrist hinge and lag, grip, ball position, ball flight.

## 11. Full bag: clubs and profiles

Every club is selectable from day one and is stored on the note by name, for example "7-iron".

| Family | Clubs | Shot types |
|---|---|---|
| Driver | Driver | Full |
| Fairway woods | 3, 4, 5, 7 | Full |
| Hybrids | 2 to 5 | Full |
| Long irons | 1 to 4 | Full |
| Mid irons | 5 to 7 | Full |
| Short irons | 8, 9 | Full, chip/pitch |
| Wedges | Pitching, gap, sand, lob | Full, chip/pitch |
| Putter | Putter | Putt |

**Three anchor profiles instead of fourteen.** Setup references change steadily with club length, so only Driver, 7-iron and Wedge profiles are tuned by hand. Every other club's ranges are interpolated by its place in the bag. A club can be given its own override later.

| Aspect | Wedge end | 7-iron | Driver end |
|---|---|---|---|
| Stance | Narrower than the shoulders | About shoulder width | Wider than the shoulders |
| Posture | Most forward bend | Middle | Most upright |
| Spine tilt away from target at address | Almost none | Slight | Clearly more |
| Impact | Hands well ahead, weight on the lead side | Hands ahead | Head behind the ball; hands level or slightly ahead |
| Tempo reference | 3:1 full, 2:1 partial | 3:1 | 3:1 |

Thresholds are seed values to tune, kept as data. The putter has a separate small profile. A second baseline matters more than any table: mark one saved swing per club as **your reference**, and later swings with that club are compared with it.

## 12. From measurements to advice

**Finding.** Each rule that fires records: the check, the position, your value, the expected range, how far off, the confidence, and the key frame that shows it.

**Ranking.** Size of the miss, weighted by confidence, with setup faults ahead of movement faults because they cause them. At most three "work on" items per swing, plus one or two things that were in range.

**Drill library.** A fixed list of drills keyed by fault and club family.

**Templates (recommended, and the default).** Each fault has a few wordings by severity, with your numbers filled in. This is instant and can't invent a measurement.

**Optional on-device model (later).** Your phone can run one. LiteRT-LM runs Gemma fully offline from Kotlin, and the S24 Ultra is one of Google's benchmark phones for it.

| Model | File size | Speed on the S24 Ultra | Verdict |
|---|---|---|---|
| Gemma 3 1B | About 1 GB | 24–33 tokens a second | The one to try: roughly ten seconds per feedback paragraph |
| Gemma 3n E2B | About 3 GB | 16 tokens a second | Better wording, about twice as slow, three times the storage |
| Gemma 3n E4B | About 4.2 GB | 9 tokens a second | Too slow for this job |

I'd still ship templates first, because the model rewords the same findings and doesn't improve the advice. Whether a 1 GB model's wording is good enough is worth testing with real findings before committing. If added: the model file is copied onto the phone once and imported, so the app still needs no network access, and any output containing a number that isn't in the findings is discarded in favour of the template.

**Feedback layout.** Summary line · What's working · Work on (what was seen, why it matters, one drill) · Tempo · What this clip couldn't show.

## 13. What is stored, and export

**Stored per analysis:** the key-frame images with the skeleton drawn on, the landmark tracks, and all results. By my estimate that is 1–3 MB, mostly the images.

**Never stored:** the video, or any link back to it.

| Record | Holds |
|---|---|
| Analysis | Date, club, shot type, view, handedness, frame rate and slow-motion factor, event times, all metrics, findings, warnings, rules version, key-frame images, landmark tracks |
| Note | Link to its analysis; its own copy of club, date and view; the feedback text as shown; your own comment; reference-swing flag |
| Bag | The clubs you carry, in order |
| Settings | Height, handedness, default view |

What follows from keeping no video:

- Playing the video with the skeleton over it is possible only on the result screen, straight after analysis. A saved analysis reopens with key frames and numbers.
- When rules or profiles change, old swings can be re-scored from their stored landmark tracks.
- Re-tracking an old swing with a better tracking model means picking the video again.
- A note keeps its own copy of the club and text, so it survives deleting the analysis.

**Export.** Android's "save as" dialog writes the file wherever you choose, and the share sheet sends it to any app. Neither needs a permission. Exports are text only. One note per file, named by date, club and view:

```
SWING NOTE
Date:    <date and time>
Club:    <club name>
Shot:    <full swing, chip/pitch or putt>
View:    <down-the-line or face-on>
Clip:    <frame rate, slow motion yes/no>

FEEDBACK
<feedback text exactly as shown in the app>

TEMPO
<backswing> / <downswing>  ratio <x>:1  (range <low>–<high>)

KEY NUMBERS
<check>: <your value>  (expected <range>)

COULD NOT JUDGE
<list>

MY COMMENT
<your own text>
```

## 14. The sample clip as the acceptance test

Labels read by eye from numbered frames (30 fps, counting from 0), now with measured results. "15 fps" rows re-sample the clip from the even or the odd frames, which shifts the sampling by one original frame; their times are converted back to the original clip.

| Moment | Label | Tolerance | Heavy model, 30 fps | Full model, 30 fps | Heavy, 15 fps, two offsets | Result |
|---|---|---|---|---|---|---|
| First movement | Frame 20 (0.67 s) | ±2 frames | Frame 21 | Frame 20 | Frames 22 and 21 | Pass |
| Club starts down | Frame 51–52 (about 1.71 s) | ±2 frames | 1.713 s (frame 51.4) | 1.715 s | 1.722 s and 1.711 s | Pass |
| Hands start down | Frame 53 | ±2 frames | Frame 54 | Frame 54 | – | Pass |
| Impact | Between frames 60 and 61 | ±1 frame | 2.014 s (frame 60.4) | 2.005 s | 2.012 s and 2.009 s | Pass |
| Finish reached and held | Frames 74–78 | ±4 frames | Frame 73 | Frame 76 | Frames 74 and 75 | Pass |
| Loudest sound | 2.14 s | Must not be taken as impact | Not tested: sound is not wired in yet | – | – | Open |

The club-top label changed from version 0.3. I had read "club changes direction at frame 51" by eye; measured, the shaft's angle is nearly flat from frame 46 to 50, and the club clearly starts down at frame 51–52.

**Tempo on the sample:** backswing about 1.03 s, downswing about 0.30 s, total about 1.33 s, ratio 3.4–3.7:1 across all four runs. Pause at the top 0.27 s.

Expected results still to check once the app exists: one swing found; view down-the-line; right-handed; face-on checks listed under "couldn't judge".

## 15. Test results and what is left to prove

Tested on 14 November 2026, on a computer, with the same MediaPipe model files the phone would use (`pose_landmarker_heavy.task` and `pose_landmarker_full.task`, float16) in video mode.

| # | Test | Result | Design change |
|---|---|---|---|
| 1 | Tracking through the downswing | **Pass, with limits.** Both models found the body in all 115 frames. The hand midpoint stays within about 25 px of the real hands from address through impact (frames 0–62), including the blurred frames 57–61. Individual wrist points split near impact, and after impact the hands are hidden and misplaced by 50–80 px (frames 63–67). | Hand midpoint only; no hand checks between impact and finish in the down-the-line view (section 8, stage 3) |
| 3 | Key positions from landmarks | **Pass.** All events within tolerance with both models (section 14). The first-movement rule had to change: a fixed starting position failed on the full model (it fired at frame 12, mid-address). | New first-movement rule (section 8, stage 4) |
| 4 | Tempo between frames | **Pass, with a new requirement.** With refinement between frames the ratio held at 3.41–3.67 across both models and both 15 fps offsets. But no hand-based "top" gives a usable ratio (2.0–5.6:1). | Shaft finder added to stage 4, required for tempo |
| – | Heavy against full model | Same events within 0–3 frames; tempo within 0.26. On this workspace's two processor cores: heavy 77 ms per frame, full 27 ms per frame. Not a phone measurement. | Heavy stays the default; full is an acceptable fallback |

**Still to prove:**

1. **Speed on the S24 Ultra.** Time decoding plus tracking for the 115 frames, with and without graphics acceleration.
2. **Shaft finder away from open sky.** Needs a clip filmed in front of your practice net, a hedge or a wall. If it fails there, the app falls back as described in stage 4.
3. **Sound against picture.** Wire in the strike detector and confirm it locates the swing without being taken as impact.
4. **SwingNet conversion.** Same frames before and after conversion, then the same on the phone.
5. **Your own recordings:** what the phone's 60 fps, 120 fps and slow-motion files look like to the app (frame rate, duration, capture-rate tag, sound).
6. **Form measurements.** Posture, early extension and head movement have not been checked against the sample yet; that belongs to build step 4.

## 16. Build order

1. **Shell with no analysis:** pick a video, bag and club question, placeholder result, save note, export text. This delivers requirements 1, 2 and 4.
2. **Decoding and tracking:** skeleton over the video, landmark tracks and key frames saved.
3. **Key positions, shaft finder and tempo**, tested against section 14 with the same results as the computer tests.
4. **Down-the-line checks** for full swings with the three anchor profiles and template feedback. This completes requirement 3 for your sample's view.
5. **Face-on checks**, reference swing, per-club history.
6. **SwingNet cross-check.**
7. **Later:** putt and chip checks beyond the basic set, recording inside the app at high frame rate, the optional Gemma model, full club tracking, launch-monitor import.

## 17. Limits

- One camera sees two dimensions. Anything involving rotation or depth is an estimate.
- The club is found only near the top of the swing, and so far only against open sky. Club face, path and plane are out of scope for now.
- At 30 fps the downswing is only about 9 frames. Refinement between frames kept the sample's tempo within ±0.13 of its ratio, but higher frame rates are still safer.
- In the down-the-line view the hands can't be seen between impact and the finish.
- With no video kept, a saved swing can't be re-watched in the app, only reviewed through its key frames and numbers.
- SwingNet's licence is non-commercial. The app is fine for personal use; sharing it more widely would mean dropping SwingNet.

## 18. Open questions

1. Confirm the phone is the S24 Ultra (2024), not the S23 Ultra (2023).
2. Film one down-the-line swing in front of your practice net and run it through the app: it's the next test for the shaft finder (section 15).
3. Build route: GitHub Actions or Android Studio (section 19).

## 19. MVP status

The project folder `swing-analyzer/` implements build steps 1–4, with the face-on checks from step 5 included. The golf knowledge is in `swing-knowledge-base.md`, and the app reads it as data from `core/src/main/resources/knowledge.json`.

| Part | Where | State |
|---|---|---|
| Analysis core | `core/` (pure Kotlin) | Compiled and tested here: 25 of 25 checks pass (below) |
| Knowledge and rules | `knowledge.json` | 34 rules, 38 faults, 24 drills, 3 club anchors covering 23 clubs |
| Android app | `app/` | Written; first compile happens on the first build (this workspace can't reach Google's Android repositories) |
| Build | `.github/workflows/build.yml`, Gradle wrapper | GitHub Actions runs the core tests and builds the APK; Android Studio also works |

**Checks run on the sample (`core/src/test`):**
- Every section 14 event lands inside its tolerance.
- Tempo is measured from the shaft at 3.5:1.
- Shaft angles match the earlier reference within 6°.
- The view and handedness guesses are right.
- The setup, posture, early-extension and shoulder-plane checks pass on the sample.
- The results and notes survive saving and loading, and the note export records the club and your comment.
- An injected early extension is flagged and becomes the first "work on" item.
- A mirrored left-handed copy of the swing gives the same events, with tempo within 0.15.

**Deviations from this design in the MVP:**
- No sound-based swing finder: a quick tracking pass finds the swing in long clips instead.
- No comparison with your reference swing yet: you can mark a reference swing, but the app doesn't compare against it.
- No SwingNet and no on-device language model.
- Analysis runs while the screen is kept on, not as a background service.


## Sources

- MediaPipe Pose Landmarker: https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker
- LiteRT-LM, including S24 Ultra benchmarks: https://developers.google.com/edge/litert-lm/overview
- Gemini Nano device list: https://developers.google.com/ml-kit/genai
- Galaxy S24 Ultra specifications: https://m.gsmarena.com/_galaxy_s24_ultra-12771.php
- Samsung community, trimmed-video frame rates: https://eu.community.samsung.com/t5/galaxy-s23-series/s23-ultra-one-ui-6-1-issue-with-video-editing/td-p/9546434/page/2
- Samsung community, slow-motion files saved at 30 fps: https://eu.community.samsung.com/t5/galaxy-s21-series/how-to-record-a-video-at-240fps-that-outputs-a-video-at-240fps/td-p/2897897
- ONNX Runtime on mobile: https://onnxruntime.ai/docs/get-started/with-mobile.html
- Android capture rate: https://developer.android.com/reference/android/media/MediaFormat.html
- Sports2D: https://github.com/davidpagnon/Sports2D
- GolfDB and SwingNet: https://github.com/wmcnally/golfdb · paper: https://arxiv.org/abs/1903.06528
- Swing-improvement student project: https://github.com/Strojove-uceni/23206-final-pose-estimation-for-swing-improvement
- golftracker: https://github.com/sanjeevs/golftracker
- GolfPose: https://github.com/MingHanLee/GolfPose
- Tempo ratios: https://members.wayned.com/?p=2579 · https://thegolfwire.com/105752-2 · https://lpgawomensnetwork.com/?p=2852
