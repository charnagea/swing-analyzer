# Swing Analyzer: knowledge base

Version 1.1 · 14 November 2026 · companion to `swing-analyzer-architecture.md`

This is the golf knowledge the app's advice is built from: what a sound swing does, how clubs change it, the common faults, how each one shows up on video, the thresholds the app uses, and the drills it recommends. Every rule in the app's `knowledge.json` traces back to a section here. Numbers in brackets like [3] point to the sources at the end.

## 1. How to read this document

- **Research figures** come from motion-capture studies, large lesson databases (GolfTEC's SwingTRU study of about 30,000 golfers [3]; TPI's screening of over 90,000 golfers [1, 2]) and 3D phone analysis (Sportsbox AI tour ranges [4]). They describe averages, not rules: good players differ.
- **App thresholds** are my translation of those figures into what one phone camera can measure. They are starting values, marked "seed", to be tuned against your own swings. Section 11 lists them all.
- **Units.** The app measures distances in *torso lengths* (T): the distance from the midpoint of the hips to the midpoint of the shoulders at address. By standard body proportions that is about 0.29 × body height, so about 51 cm for someone 1.77 m tall. As a rule of thumb, **0.05 T ≈ 2.5 cm ≈ 1 inch**. Using torso lengths removes the effect of camera distance.
- **Views.** DTL = down-the-line (camera behind you, looking at the target). FO = face-on (camera facing your chest). Many checks need one view or the other.

## 2. What matters most for a beginner

Teachers agree on an order. Grip, setup and posture come first, because a faulty setup forces compensations later in the swing [10, 11, 12]. GolfTEC's guidance for beginners is grip, then setup (posture, alignment, balance), then solid contact, with smooth, balanced swings mattering more than effort [12].

The app follows that order when it ranks advice:

1. **Setup:** posture, knee flex, arm hang, stance width, tilt. Cheap to fix, and they cause later faults.
2. **Pivot and posture in motion:** sway, loss of posture, early extension, head movement. These decide where the club bottoms out, and so the quality of contact.
3. **Weight shift and sequence:** getting onto the lead side by impact, finishing balanced.
4. **Tempo.**
5. **Style details:** lead-arm bend, trail-knee straightening. Shown as numbers only, rarely as advice.

It gives at most three "work on" points per swing and one drill each, because changing one thing at a time is how swings actually improve.

## 3. What a good full swing does, stage by stage

### 3.1 Setup (address)

| Element | What good looks like | Research | App can see |
|---|---|---|---|
| Posture | Bend forward from the hip joints with a fairly straight back, not a rounded upper back or slumped neck. Enough bend that you don't need to curl your neck to see the ball [13]. | Tour players show about 30° of chest bend at setup [4]. Ben Hogan's spine angle was about 29° and changed little from driver to 7-iron [14]. | DTL: trunk forward bend (hip midpoint to shoulder midpoint, from vertical). Your sample: 28.5°. |
| Knee flex | Athletic, slight flex, knees roughly over the balls of the feet. Not a deep squat, not locked. | Common guidance is 15–25° for irons, a little less with driver; under 5–10° feels rigid [15]. | DTL: knee angle (0° = straight). Sample: 22–25°. |
| Arms | Arms hang naturally from the shoulders; hands roughly under the shoulders. | Qualitative guidance from all sources [13, 16]. | DTL: horizontal offset of hands from shoulders. Sample: hands 0.10 T inside the shoulder line. |
| Balance | Weight centred over the middle of the feet, 50/50 left-right for full shots [16]. | – | Not measurable (no pressure data). |
| Stance width | Driver wider than shoulders; mid-irons about shoulder width; wedges narrower [17, 18, 19]. | Teaching rule of thumb, not lab data. | FO: ankle distance ÷ shoulder distance. |
| Spine tilt | Upper body tilted slightly away from the target, more with longer clubs; it helps the driver catch the ball on the way up [4, 20]. | Tour players average about 15° of chest side-bend at setup, increasing to over 30° by impact [4]. | FO: trunk line from vertical, away from target. |
| Ball position | Driver off the lead heel or instep; moving back about one ball-width at a time, to about centre for wedges [17, 19]. | GolfTEC: for all clubs except driver, about one clubhead inside the lead foot, with stance width changing [19]. | Not measurable: the app doesn't track the ball. |
| Grip and aim | Club in the fingers; clubface, feet, hips and shoulders parallel to the target line [10, 11]. | – | Not measurable. |

### 3.2 Takeaway (address to shaft parallel)

- The club, hands, arms and chest move away together, low and wide, without early wrist hinge or rolling the hands [21, 22].
- At shaft parallel, the hands are roughly in line with the trail leg and the clubhead is slightly outside the hands [21, 23].
- The app can't check the club in the takeaway yet. It reports when you started moving (tempo) and whether posture held.

### 3.3 Backswing and top

- **Turn, don't slide.** The hips rotate around a braced trail leg. The pelvis should not slide away from the target: tour players' pelvis moves less than about 2 inches away on the way back [4]. A sway is excess lateral movement that puts weight onto the outside of the trail foot [2].
- **Keep the forward bend.** Loss of posture is any significant change from the setup angles [1]. GolfTEC finds tour players return close to their address bend in the downswing [24].
- **Shoulders turn on a tilted plane.** At the top the lead shoulder works down, not level. Tour players have about 36° of downward shoulder tilt at the top versus 29.6° for high handicappers, and a 30-handicap measures about 25° [3, 25]. A flat shoulder plane (shoulders turning level, "standing up") is a TPI fault [1]. Titleist's check: with a club across the shoulders, at the top it should point down at the ground beyond the ball, not at the horizon [26].
- **Spine tilted away from the target at the top.** Leaning toward the target at the top is reverse spine angle, linked to lower back pain [27, 28].
- **Size of turn.** Tour players turn the chest about 100° and the pelvis about 40° at the top [4, 29]. One camera can't measure these turns; the app only compares them roughly with your reference swing.
- **Trail knee.** Keeping some trail-knee flex helps hold a loaded hip [29]. Straightening is common and partly a style choice; the app shows it as a number only.
- **Lead arm.** A fairly straight lead arm gives width, but a slight bend is fine for beginners. The app notes a strongly folded lead arm only.

### 3.4 Transition and downswing

- **Sequence from the ground up.** Efficient swings reach peak rotation speed in order: pelvis, then thorax, then lead arm, then club [30, 31, 32]. Amateurs often fire the arms before the thorax [32]. Skilled golfers start rotating the pelvis earlier, with a quicker shift onto the lead foot [33]. About 70% of PGA Tour players started the downswing from the pelvis in one study [33]. The app can't measure rotation speeds; it measures the visible consequences: hip shift, early extension, over-the-top hand path.
- **Shift toward the target.** By impact the hips should be ahead of where they started. GolfTEC: pros +1.6 inches toward the target, high handicappers −0.4 inches (behind) [3]. TPI describes a normal shift as about 4 inches toward the target at impact [34]. Sportsbox: tour pelvis up to about 5 inches toward the target by impact, while the chest stays roughly centred [4].
- **Stay in posture.** Early extension, the pelvis moving toward the ball and the torso standing up, is the most common characteristic TPI sees. About 64–67% of amateurs show it, while 99% of over 100 tour players tested didn't [1, 2, 35]. It typically causes blocks and hooks [36].
- **Hands drop, not out.** Over the top (club moving outside the hands' backswing path) is one of the leading causes of the slice [1]. GolfTEC finds the best players keep their hands in line with the middle of the trail bicep early in the downswing [37].

### 3.5 Impact

- Hips open toward the target: pros average 36° open, high handicappers 19.5° [3]. Not measurable with one camera; the app looks at hip shift instead.
- Hips ahead of address; head roughly over the middle of the stance or behind the ball [3, 38].
- Trail shoulder clearly lower than the lead shoulder: pros 39° of tilt at impact, high handicappers 27.5°. GolfTEC calls this one of the hallmarks of elite players [3].
- Weight mostly on the lead foot: tour players load at least 80% into the lead leg at impact with the driver [39]; studies report 81–142% of body weight on the lead side at impact once forces are included [33].
- Shaft leaning forward with irons (ball first, then turf) [40]. Not measurable without club tracking.

### 3.6 Follow-through and finish

- A balanced finish is the best single summary of the swing: weight almost entirely on the lead foot, belt buckle and chest facing the target, trail heel up with only the toe touching [41, 42].
- Hold it for a few seconds; falling backward or stumbling means the weight didn't get forward [42].
- Good players finish tall: torso stretched back away from the target. Pros average 32° of back bend at the finish versus 3.2° for high handicappers [3].

## 4. Tempo

- **Full swing:** good players' backswing takes about three times as long as the downswing (3:1), whatever their speed. Common tour frame counts at 30 fps are 21/7, 24/8 and 27/9 [43, 44]. First movement to impact usually takes 0.93–1.20 s [44].
- **Short game and putting:** about 2:1 [45, 46].
- **Measuring it.** The downswing starts when the club head first moves back toward the ball [47]. That is why the app uses the shaft, not the hands (architecture, section 8).
- **Your sample:** about 1.03 s back, 0.30 s down, ratio about 3.5:1, total 1.33 s. That is slightly slower overall than the tour range, with a relatively long backswing.

## 5. How clubs change the setup

| Club family | Stance width | Ball position | Posture and tilt | Strike |
|---|---|---|---|---|
| Driver | Widest, wider than shoulders | Off lead heel or instep | Most upright; most tilt away from target | Up on the ball |
| Fairway woods, hybrids | About shoulder width | A ball's width inside lead heel | Between driver and irons | Sweeping |
| Long and mid irons | About shoulder width | Just forward of centre | Middle | Slightly down, ball first |
| Short irons, wedges (full) | Narrower than shoulders | Centre | Most bent over; little tilt | Down, ball first |
| Chip and pitch | Narrow | Centre to slightly back | Weight 60–80% forward, hands slightly ahead | Down, small motion |
| Putter | About shoulder width | Slightly forward of centre | Eyes over the ball, arms hanging | Level to slightly up |

Sources: [17, 18, 19, 48, 49, 50, 51]. The app turns this into three anchor profiles (wedge, 7-iron, driver) and interpolates the clubs in between (architecture, section 11).

## 6. Fault catalogue

Each entry gives the definition, how common it is, why it matters, how the app detects it, likely causes and the drills it recommends. TPI stresses that many faults come from physical limits as much as technique, so a fault that won't go away with drills is worth a physical screening [1, 52].

### 6.1 Setup posture: too upright, too bent, or poor knee flex

- **What it is.** Standing too tall with straight legs, or bending too far with too much knee bend. S-posture (too much arch in the lower back) and C-posture (rounded upper back) are TPI's named setup faults [1].
- **Why it matters.** Setup posture controls how freely you can turn and how well you keep your angles. Too much knee bend with little hip bend is effective in bunkers but rarely for full shots [53].
- **Detection (DTL).** Forward bend and knee flex compared with the club profile. S- and C-posture need spine curvature, which 2D landmarks can't measure reliably, so the app doesn't name them.
- **Drills.** Hip hinge with a club along the spine; "athletic stance" knee check.

### 6.2 Arms reaching or crowded

- **What it is.** Hands far out from under the shoulders (reaching) or jammed into the thighs.
- **Why it matters.** Reaching puts weight on the toes and encourages losing posture; crowding blocks the arms' path.
- **Detection (DTL).** Hands' horizontal offset from the shoulders at address.
- **Drill.** Arm-hang setup: let the arms hang, then grip where they fall.

### 6.3 Stance too narrow or too wide for the club

- **Why it matters.** Too narrow limits stability; too wide restricts hip turn [19, 54].
- **Detection (FO).** Ankle distance ÷ shoulder distance compared with the club profile. Low confidence until you calibrate it with your own reference swing.
- **Drill.** Club-drop stance check: hold a club against each shoulder and let it hang to find each foot [55].

### 6.4 Not enough (or too much) tilt away from the target at address

- **Why it matters.** Little or no tilt makes it hard to hit up with the driver and tends to put the upper body ahead of the ball. Too much tilt with an iron moves the low point behind the ball.
- **Detection (FO).** Trunk side-bend at address compared with the club profile.
- **Drill.** Hip bump at setup: hips slightly toward target, nose behind an alignment stick on the ball [56].

### 6.5 Sway

- **What it is.** Excess lateral movement of the lower body away from the target in the backswing, putting weight onto the outside of the trail foot [2]. About 37% of golfers TPI screened sway [52].
- **Why it matters.** It makes it hard to shift back to the lead side and adds a variable the downswing has to correct [2, 52]. Often caused by limited trail-hip internal rotation [57].
- **Detection (FO).** Hip midpoint movement away from the target at the top. Warn above 0.10 T (about 2 inches, the edge of the tour range [4]); fault above 0.20 T, or if the hips move outside the trail ankle.
- **Drills.** Alignment stick just outside the trail hip, turn without touching it [58, 59]; feet-together swings.

### 6.6 Reverse spine angle and reverse pivot

- **What it is.** The upper body leaning toward the target at the top, or excess lead-side bend [27]. A reverse pivot puts weight on the lead foot going back and the trail foot coming down [60]. About 38% of golfers TPI screened show a reverse spine angle [52].
- **Why it matters.** It is the leading swing cause of lower back pain [1, 27], and it usually leads to hanging back [34].
- **Detection (FO).** Trunk leaning toward the target at the top (shoulder midpoint ahead of hip midpoint); head moving toward the target in the backswing.
- **Drills.** Nose behind an alignment stick through the backswing [56, 60]; cross-arms pivot drill [60].

### 6.7 Loss of posture and flat shoulder plane

- **What it is.** Any significant change from the setup angles during the swing [1]. About 64% of players TPI screened show it [52]. Standing up in the backswing usually flattens the shoulder plane [26].
- **Why it matters.** Thin and topped shots, inconsistent contact [16, 61].
- **Detection (DTL).**
  - Forward bend change from address to the top and to impact: warn at 6° lost by the top and 8° by impact, fault at 12° and 15°.
  - Head height change: warn at 0.12 T up or 0.20 T down. Byrne and Kenny found low-handicap golfers' head moved about 2 cm vertically versus about 12 cm for higher handicaps [62].
  - Shoulder plane at the top. The shoulder line, seen from behind, should tilt down by about your address bend or more; the sample shows 36° against 28.5° of bend. Warn below 0.65 × address bend or below 15°.
- **Drills.** Club across the shoulders: at the top it should point down beyond the ball [26]; windmill drill [63].

### 6.8 Early extension

- **What it is.** The pelvis moving toward the ball, the torso straightening, in the backswing or downswing [35, 36]. About 64–67% of amateurs early extend; nearly no tour players do [2, 35, 52].
- **Why it matters.** The arms get stuck behind the body, the torso lifts through impact, and the typical misses are a block right and a hook left [36]. Often linked to an inability to do a full deep squat, hamstring or glute limits [36, 64].
- **Detection (DTL).**
  - Hip midpoint movement toward the ball by impact: warn above 0.08 T (about 1.5 inches), fault above 0.15 T. TPI's example of a clear early extender moved over 3 inches [65].
  - Also flagged in the backswing above 0.06 T.
  - Paired with forward bend loss (section 6.7).
  - Sample: +0.01 T, so no early extension.
- **Drills.** Wall drill: backside against a wall, keep contact through impact [66, 67]; chair drill [68]; Ludvig Åberg's version adds feeling the lead glute stay back on the way down [69].

### 6.9 Over the top (hand path)

- **What it is.** The club moving outside its backswing path early in the downswing, approaching the ball from outside the target line. About 43.5% of golfers TPI screened [52]. It is one of the leading causes of the slice [1].
- **Detection (DTL, low confidence).**
  - Compares the hands' downswing path with their backswing path near the top (between shoulder height and a quarter of the way to the hips).
  - Hands moving more than 0.15 T toward the ball on the way down are reported as a *possible* over-the-top move.
  - The app can't see the club path itself, and lower in the downswing the hands normally move out toward the ball, so only the early part is used.
- **Drills.** Headcover just outside the ball line behind the ball [70, 71]; hands toward the trail bicep early in the downswing [37].

### 6.10 Hanging back, slide and forward lunge

- **Hanging back.** Little or no shift toward the target in the downswing; the player is still on the back foot at impact [34]. About 32% of golfers TPI screened [52]. It costs speed; fat shots are the common miss [34, 72].
- **Slide.** Excess lateral movement of the lower body toward the target in the downswing [1, 25]. About 31% of golfers TPI screened [52].
- **Forward lunge.** The upper body lunging toward the target in transition [1].
- **Detection (FO).** Hip midpoint shift toward the target at impact:
  - Below 0 T (hips behind address): hanging back, fault.
  - 0 to 0.04 T: little shift, warn.
  - 0.04 to 0.30 T: good, matching GolfTEC's 1.6 inches through Sportsbox's 5 inches [3, 4].
  - Above 0.30 T: slide, warn.
  - Head moving more than 0.10 T toward the target by impact: lunge.
- **Drills.** Step-through drill [42, 59]; feet-together swings; holding the finish.

### 6.11 Level shoulders at impact

- **What it is.** Shoulders close to level at impact, typical of an arms-dominated swing [3].
- **Detection (FO).** Shoulder line angle at impact (trail shoulder lower). Warn below 20°; pros average 39°, high handicappers 27.5° [3].
- **Drill.** Belt buckle over the front foot, head over the middle of the stance at impact [3].

### 6.12 Lead arm folding and chicken wing

- **What it is.** A bent lead arm at the top (loss of width), or the lead elbow folding through impact (chicken wing), which limits speed and power [1, 73].
- **Detection (FO, low confidence).** Lead elbow flexion: noted at the top above 40°. After impact the arms are often hidden, so the chicken wing is reported only when the elbow is clearly visible.
- **Drill.** Towel under both armpits, half swings [73, 74].

### 6.13 Unbalanced or incomplete finish

- **Why it matters.** Weight left on the trail foot means the weight shift didn't happen, often with thin or fat strikes [41, 42].
- **Detection (both views).**
  - Trail heel lifted at the finish: warn below 0.08 T of lift. The sample shows 0.36 T.
  - Body still for 0.3 s once the finish is reached.
  - FO only: hips over the lead side, and torso extended back away from the target. A torso extension below 10° is shown as a number only.
- **Drills.** Hold the finish for a count of three, checking belt buckle to target, trail heel up and weight on the lead foot [41, 42].

### 6.14 Faults the app can't judge yet

These need club or ball tracking, wrist sensors or pressure plates:

- casting and scooping (early release)
- clubface and path
- grip and aim
- ball position
- kinematic sequence speeds
- hip and chest turn angles
- pressure shift

The app says so instead of guessing.

## 7. Short game

### 7.1 Chips and pitches

- **Setup.** Narrow stance; ball centre to slightly back; weight forward, about 60–80% on the lead foot; hands slightly ahead of the ball [48, 49, 50, 75].
- **Motion.** A pendulum-like stroke driven by the arms and shoulders, wrists quiet, with the hands staying ahead through impact. The head and lower body stay quiet, though not rigid [49, 50, 76]. A 2:1 tempo is the common reference [45, 77].
- **Common misses.** The head and chest dropping down and back move the low point behind the ball, causing duffs [78]. Too much forward press or a ball too far back makes the club dig [79, 80].
- **App checks.**
  - Head and hip movement: warn above 0.06 T and 0.10 T.
  - Tempo against 2:1: good range 1.6–2.6.
  - Follow-through length at least as long as the backswing.
  - FO only: weight forward at address.

### 7.2 Putting

- **Setup.** Eyes over or slightly inside the ball; arms hanging, forming a triangle with the shoulders; weight even [51, 81, 82].
- **Stroke.** The shoulders rock the triangle; wrists firm; head and body still until well after impact. The backstroke and through-stroke are about the same length, and stroke length controls distance [51, 82, 83]. Tempo about 2:1 [46, 83].
- **App checks.**
  - Head movement: warn above 0.03 T.
  - Hip movement: warn above 0.04 T.
  - Tempo: good range 1.6–2.6.
  - Through-stroke against backstroke length: good 0.8–1.4; below 0.7 suggests decelerating.
  - Eyes over the ball can't be judged because the ball isn't tracked.
- **Drills.** Gate drill [82]; "listen, don't look" [83]; matching stroke lengths.

## 8. How the advice is worded

1. Start with one or two things that were in range.
2. At most three "work on" items. Faults (past the fault threshold) come before warnings; within each, setup comes first, then pivot, then shift and finish, then tempo; within a group, the biggest miss weighted by confidence comes first. The same fault found at two points (for example early extension in the backswing and at impact) is listed once.
3. Each item says what was seen with the number and the expected range, why it matters in one line, and one drill.
4. Low-confidence findings are worded as "possible" or moved to "couldn't judge".
5. No medical claims. Where a fault is commonly physical (sway, early extension, reverse spine angle), the app adds a single line: if drills don't change it, a coach or a TPI-certified professional can check whether mobility is the limit. If something hurts, stop.

## 9. Your sample, read through this knowledge base

Down-the-line, mid-iron, measured on a computer with the same pose model as the app:

- **In range:**
  - Address forward bend 28.5°, knee flex 22–25°, arms hanging just inside the shoulders.
  - Forward bend kept at the top (+0.1°) and impact (+0.8°).
  - No early extension: hips +0.01 T toward the ball.
  - Shoulder plane at the top 36°, matching the tour average.
  - Head height within 0.02 T at the top.
  - Trail heel well up at the finish (0.36 T), and a still, balanced finish.
- **Watch:**
  - The head dropped about 0.16 T (about 8 cm) by impact: under the warning level, and common in good players with longer clubs [24].
  - The trail knee straightened from about 25° to 5° at the top, a style point the app only shows as a number.
- **Tempo:** about 3.5:1, total 1.33 s. The backswing is a little long relative to the downswing; a 24/8 or 27/9 tempo count is the suggested drill.
- **Couldn't judge from this view:** stance width, tilt, sway, hip shift, shoulder tilt at impact. These need a face-on clip.

## 10. Safety and the body–swing connection

TPI's screening research found significant links between physical tests and swing faults. Golfers who couldn't do an overhead deep squat or balance on their lead leg were 2–3 times more likely to early extend, lose posture or slide [64]. The app therefore never treats a persistent fault purely as a technique problem.

## 11. App thresholds (seed values)

T = torso length (section 1). Ranges are inclusive. "Warn" goes to "work on" when it ranks in the top three; "fault" always does; "info" is shown as a number only.

| ID | View | Check | Measure | Warn | Fault | Source |
|---|---|---|---|---|---|---|
| A1 | DTL | Address forward bend | Degrees from vertical; wedge 30–45, 7-iron 26–42, driver 20–38 | Outside range | More than 8° outside | 4, 14, 16 |
| A2 | DTL | Address knee flex | Degrees, average of both knees; wedge and iron 12–32, driver 10–28 | Outside range | More than 10° outside | 15 |
| A3 | DTL | Hands at address | Offset toward ball from shoulders, T; −0.25 to +0.15 | Outside range | More than 0.15 T outside | 13, 16 |
| B1 | DTL | Posture kept to top | Forward bend lost, degrees | > 6 | > 12 | 1, 24 |
| B2 | DTL | Head height to top | Rise / drop, T | Rise > 0.12 or drop > 0.20 | Rise > 0.20 or drop > 0.30 | 62 |
| B3 | DTL | Hips toward ball, backswing | T | > 0.06 | > 0.12 | 2, 35 |
| B4 | DTL | Shoulder plane at top | Shoulder line tilt ÷ address bend | < 0.65 or tilt < 15° | < 0.45 | 3, 26 |
| B5 | DTL | Trail knee flex at top | Share of address flex kept | info below 35% | – | 29 |
| C1 | DTL | Early extension | Hips toward ball at impact, T | > 0.08 | > 0.15 | 35, 36, 65 |
| C2 | DTL | Posture at impact | Forward bend lost, degrees | > 8 | > 15 | 1 |
| C3 | DTL | Head height at impact | Rise / drop, T | Rise > 0.12 or drop > 0.20 | Rise > 0.20 or drop > 0.30 | 62 |
| C4 | DTL | Possible over the top | Hands' down path outside up path near the top, T | > 0.15 (worded "possible") | – | 37, 52 |
| D1 | Both | Trail heel at finish | Heel lift, T | < 0.08 | < 0.03 | 41, 42 |
| D2 | Both | Balanced finish | Body still for 0.3 s | Not reached | – | 42 |
| F1 | FO | Stance width | Ankle ÷ shoulder distance; wedge 0.9–1.35, 7-iron 1.15–1.6, driver 1.4–1.9 | Outside range (low confidence) | – | 17, 18, 19 |
| F2 | FO | Tilt at address | Trunk side-bend away from target, degrees; wedge −3 to 8, 7-iron 2–12, driver 7–20 | Outside range | More than 8° outside | 4, 20 |
| F3 | FO | Sway | Hips away from target at top, T | > 0.10 | > 0.20, or outside trail ankle | 2, 4 |
| F4 | FO | Reverse spine angle | Trunk lean toward target at top, degrees | > 5 | > 10 | 27, 28 |
| F5 | FO | Head in backswing | Toward target / away, T | Toward > 0.06; away > 0.20 | Toward > 0.12 | 60 |
| F6 | FO | Hip shift at impact | Toward target, T | 0 to 0.04 (little shift); > 0.30 (slide) | < 0 (hanging back) | 3, 4, 34 |
| F7 | FO | Shoulder tilt at impact | Degrees, trail shoulder lower | < 20 | < 10 | 3 |
| F8 | FO | Head at impact | Toward target, T | > 0.10 | > 0.18 | 1, 3 |
| F9 | FO | Lead arm at top | Elbow flexion, degrees | info above 40 | – | 1, 73 |
| F10 | FO | Finish over lead side | Hip position across stance (0 = trail ankle, 1 = lead ankle) | < 0.5 | < 0.3 | 41, 42 |
| F11 | FO | Tall finish | Torso lean back from target, degrees | info below 10 | – | 3 |
| T1 | Both | Full-swing tempo | Backswing ÷ downswing | Outside 2.5–3.8 | Outside 2.0–4.5 | 43, 44 |
| T2 | Both | Full-swing total time | First move to impact, seconds | info outside 0.85–1.45 | – | 44 |
| S1 | Both | Chip head movement | Maximum displacement, T | > 0.06 | > 0.10 | 49, 50, 78 |
| S2 | Both | Chip lower body | Hip movement, T | > 0.10 | > 0.15 | 49, 50 |
| S3 | Both | Short-game tempo | Backswing ÷ downswing | Outside 1.6–2.6 | Outside 1.3–3.2 | 45, 46 |
| S4 | Both | Chip follow-through | Through ÷ back hand travel | < 0.9 | < 0.6 | 49, 77 |
| P1 | Both | Putt head movement | Maximum displacement, T | > 0.03 | > 0.06 | 51, 82, 83 |
| P2 | Both | Putt lower body | Hip movement, T | > 0.04 | > 0.08 | 51, 82 |
| P3 | Both | Putt stroke length | Through ÷ back | Outside 0.8–1.4 | < 0.6 | 82, 83 |

## Sources

1. TPI, swing characteristics overview: https://www.mytpi.com/improve-my-game/swing-characteristics
2. TPI, Early extension swing characteristic (prevalence, definition): https://www.mytpi.com/articles/swing/early-extension-swing-characteristic
3. Golf Digest / GolfTEC, "Swing By Numbers" (SwingTRU study): https://www.golfdigest.com/story/swing-by-numbers-new-study-unlocks-6-swing-secrets
4. Golf Digest, "10 advanced golf swing stats" (Sportsbox AI tour ranges): https://www.golfdigest.com/story/useful-golf-swingfacts-sportsbox-ai-graph
5. GolfTEC SwingTRU summary: https://golftec.com/SwingTRU
6. GolfWRX, GolfTEC SwingTRU interview: https://www.golfwrx.com/?p=377904
7. Golf.com, two metrics becoming easier to measure: https://golf.com/instruction/2-important-golf-swing-metrics-become-easier-measure/
8. Golf Digest, Tiger Woods pelvis sway (Sportsbox): https://www.golfdigest.com/story/Tiger-woods-golf-swing-technology-metric-golf-digest-video
9. Golf.com, slicer vs tour player sway (Sportsbox): https://golf.com/instruction/fatal-mistake-causing-your-slice/
10. Golf.com, 5 common setup mistakes: https://golf.com/instruction/5-common-setup-mistakes-how-to-avoid-them
11. Performance Golf, the golf swing setup: https://www.performancegolf.com/blog/the-golf-swing-setup-7-steps-to-a-solid-foundation
12. GolfTEC, what should a beginner work on first: https://golftec.com/blog-posts/what-should-a-beginner-golfer-work-on-first
13. RotarySwing, biomechanically correct golf posture: https://rotaryswing.com/golf-instruction/golfbiomechanics/biomechanically-correct-golf-posture
14. RotarySwing, Ben Hogan address position: https://rotaryswing.com/golf-instruction/ben-hogan/ben-hogan-address-position
15. Golf knee bend guide: https://golfblog.pages.dev/posts/2026/02/golf-swing-knee-bend-how-much-is-too
16. Giraffy, golf posture setup keys: https://giraffyco.com/blogs/news/perfect-golf-posture-setup-keys-for-consistent-ball-striking
17. USGTF, ball position and stance width: https://www.usgtf.com/the-importance-of-ball-position-and-stance-width/
18. MyGolfSpy, golf stance width rules: https://mygolfspy.com/news-opinion/instruction/golf-stance-width-3-setup-rules-every-golfer-should-know/
19. GolfTEC, ball position and setup with every club: https://golftec.com/video/master-ball-position-and-setup-with-every-club-drills-from-golftec
20. Australian Golf Digest, Sportsbox swing facts: https://www.australiangolfdigest.com.au/useful-golf-swingfacts-sportsbox-ai-graph
21. Swyng, the golf swing takeaway: https://swyng.co.uk/tips/golf-swing-takeaway
22. HackMotion, one-piece takeaway: https://hackmotion.com/one-piece-takeaway-in-golf/
23. Golf Chicago, takeaway checkpoint: https://www.golfchicagomagazine.com/publications/gc_jul_25/GC%202025-4%20instruction.pdf
24. GolfTEC, explaining the head drop: https://scramble.golftec.com/blog/2015/04/explaining-head-drop-golf-swing/
25. GolfTEC SwingTRU summary (handicap trend), same page as [5]: https://golftec.com/SwingTRU
26. Titleist, flat shoulder plane swing fault: https://titleist.com/instruction/flat-shoulder-plane
27. TPI, reverse spine angle: https://cdn.site.mytpi.com/improve-my-game/swing-characteristics/reverse-spine-angle
28. Miami Valley Golf, reverse spine angle (face-on diagnosis): https://www.miamivalleygolf.org/about/partners/visit-our-partners-web-sites/reverse-spine-angle-3474c7928.html
29. Golf Digest, Sportsbox swing facts (chest turn, trail knee): https://www.golfdigest.com/story/useful-golf-swingfacts-sportsbox-ai-graph
30. Cheetham, kinematic sequence: https://philcheethamthe3dguy.blogspot.com/2009/11/kinematic-sequence-signature-of.html
31. PMC, kinematics across clubs: https://pmc.ncbi.nlm.nih.gov/articles/PMC12416643
32. PMC, Swing Performance Index (pros vs amateurs sequencing): https://pmc.ncbi.nlm.nih.gov/articles/PMC9816382
33. Okuda et al., trunk rotation and weight transfer, JSSM 2010: https://jssm.org/jssm-09-127.xml-Fulltext
34. TPI, hanging back: https://cdn.site.mytpi.com/improve-my-game/swing-characteristics/hanging-back
35. TPI, early extension (90,000 golfers): https://www.mytpi.com/articles/swing/early-extension-swing-characteristic?search=fat
36. TPI, early extension (ball flight, causes): https://cdn.site.mytpi.com/improve-my-game/swing-characteristics/early-extension
37. GolfTEC, hand path drill: https://www.golftec.com/video/golftec-hand-path-drill-to-hit-straighter-golf-shots
38. MyGolfSpy, upper body behind the ball without hanging back: https://mygolfspy.com/news-opinion/instruction/how-to-keep-your-upper-body-behind-the-ball-without-hanging-back/
39. GolfWRX, BodiTrak pressure data: https://www.golfwrx.com/?p=372764
40. MyGolfSpy, beginner checklist (low point after the ball): https://mygolfspy.com/news-opinion/instruction/beginner-golf-checklist-7-must-know-basics-for-new-golfers/
41. Bruce Bolt, golf follow-through: https://brucebolt.us/blogs/news/golf-follow-through
42. CaddieHQ, weight transfer and finish checkpoints: https://www.caddiehq.com/resources/how-to-transition-weight-in-golf-swing
43. Wayne DeFrancesco, tempo: https://members.wayned.com/?p=2579
44. The Golf Wire, Tour Tempo: https://thegolfwire.com/105752-2
45. MyGolfSpy, Tour Tempo review (2:1 short game): https://mygolfspy.com/tour-tempo-review/
46. GolfLink, putting tempo: https://www.golflink.com/instruction/putt-consistently
47. LPGA Women's Network, start of downswing definition: https://lpgawomensnetwork.com/?p=2852
48. Human Kinetics, chip shots: https://us.humankinetics.com/blogs/excerpt/chip-shots
49. Golf Monthly, how to chip: https://golfmonthly.com/tips/short-game-tips/how-to-chip-in-golf
50. PGA, dynamics of chipping: https://pga.com/story/the-dynamics-of-chipping-on-the-course
51. Human Kinetics, putting techniques: https://canada.humankinetics.com/blogs/excerpt/putting-techniques
52. Powers Health / TPI "Body-Swing Connection" prevalence slides: https://powershealth.org/-/media/files/pdfs/cme/2024/the-body-swing-connection.ashx
53. GolfWRX, posture and swing dynamics: https://www.golfwrx.com/?p=228439
54. Fulcria, golf stance width: https://fulcria-labs.github.io/blog/golf-stance-width
55. Golf Monthly, how wide should your stance be: https://www.golf-monthly.co.uk/videos/golf-swing-tips/how-wide-should-your-golf-stance-be
56. Top Speed Golf, nose behind the stick drill: https://members.topspeedgolf.com/?p=109746
57. TPI, sway (hip mobility): https://cdn.site.mytpi.com/articles/swing/how-jordan-does-it
58. Golf Influence, how to stop swaying: https://www.golfinfluence.com/skill/how-to-stop-swaying-in-golf-swing/
59. CaddieHQ, how not to slide the hips: https://www.caddiehq.com/resources/how-to-not-slide-hips-in-golf-swing
60. HackMotion, reverse pivot: https://hackmotion.com/reverse-pivot-in-golf/
61. HackMotion, losing spine angle: https://hackmotion.com/stop-losing-spine-angle-in-downswing/
62. Byrne and Kenny, swing patterns of high and low handicap golfers (ISBS 2008): https://ojs.ub.uni-konstanz.de/cpa/article/view/1991/1859
63. Golf Magic, windmill drill: https://www.golfmagic.com/best-tips-and-instruction/posture-tip-2-windmill-drill
64. Gulgin et al., TPI screens and swing faults (J Strength Cond Res 2014): https://pubmed.ncbi.nlm.nih.gov/24476744/
65. TPI, No Laying Up visit (pelvis thrust example): https://cdn.site.mytpi.com/articles/swing/what-you-can-learn-from-no-laying-up's-visit-to-tpi
66. Skillest, how to fix early extension: https://skillest.com/blog/how-to-fix-early-extension/
67. GolfLink, stop early extension: https://www.golflink.com/instruction/stop-early-extension
68. Golf.com, chair drill: https://golf.com/instruction/drill-skills-how-a-chair-can-fix-the-problem-plaguing-70-percent-of-amateur-golfers/
69. Golf Digest, Ludvig Åberg early extension drill: https://www.golfdigest.com/story/ludvig-aberg-go-to-swing-drill-fix-early-extension-get-more-shaft-lean
70. MyGolfSpy, drills for over the top: https://mygolfspy.com/instruction/my-3-go-to-drills-for-fixing-an-over-the-top-golf-swing/
71. Golf.com, range fix for over the top: https://golf.com/instruction/range-fix-stop-you-swinging-over-top/
72. HackMotion, stop hanging back: https://hackmotion.com/stop-hanging-back-in-golf-swing/
73. IOL, fixing the chicken wing: https://iol.co.za/thepost/sport/2026-07-08-how-to-fix-the-golf-chicken-wing-drills-to-improve-swing-extension/
74. GolfWRX, towel drill: https://www.golfwrx.com/?p=732132
75. RotarySwing, Tiger Woods chip shot: https://rotaryswing.com/golf-instruction/shortgame/golf-chipping-how-to-guide/tiger-woods-chip-shot
76. Golf.com, Kellie Stenzel chipping steps: https://golf.com/instruction/short-game/10-steps-become-ultimate-chipper-kellie-stenzel/
77. Golfers Authority, chipping tips (2:1): https://golfersauthority.com/proven-golf-chipping-tips/
78. Me and My Golf, chipping contact: https://meandmygolf.com/golf-tips/chipping-tips/golf-chipping-tips-clean-contact/
79. MyGolfSpy, weight too far forward when chipping: https://mygolfspy.com/news-opinion/instruction/can-your-weight-be-too-far-forward-when-chipping/
80. RotarySwing, chipping ball position, same page as [75]: https://rotaryswing.com/golf-instruction/shortgame/golf-chipping-how-to-guide/tiger-woods-chip-shot
81. Golf Magic, fundamentals of putting: https://www.golfmagic.com/instruction/putting/the-fundamentals-of-putting/14632
82. Fulcria, putting fundamentals: https://fulcria-labs.github.io/blog/putting-fundamentals
83. Human Kinetics, putting techniques (stroke length): https://us.humankinetics.com/blogs/excerpt/putting-techniques
