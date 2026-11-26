# INCH PERFECT: v3 polish script ("Feel" update)

Date: 25 Nov 2026 · Builds on [BUILD_SCRIPT.md](BUILD_SCRIPT.md) and [BUILD_SCRIPT_V2.md](BUILD_SCRIPT_V2.md)

## 1. What the research says
- **Game feel ("juice").** Every meaningful action should give feedback, and the layers together make a game feel "expensive". Practitioners recommend:
  - **Tiered screen shake**, from light to catastrophic, kept short (0.1–0.3 s) and never overused.
  - **Hit-stop** on impacts.
  - **Easing** on every motion.
  - **Audio**, which is cheap and underused.
  - **One unified sensory language** for touch, sound and motion; the most retentive mobile games in 2026 do this.
  - **Zero extra input latency.**
- **Google Play quality bar (Android vitals).**
  - Crash rate under 1.09% and ANR rate under 0.47% overall (8% per device model).
  - Core loop at 30 fps or more, ideally 60.
  - Loading time and startup are tracked daily.
  - Titles below the bar lose discovery placement or get a listing warning.
- **What parking players praise:** graphics, interesting challenges and an "addictive" loop. They complain about performance and missing features.

## 2. Audit of the current build (Realme, 25 Nov)
| Area | Finding | Fix |
|---|---|---|
| System back in a level | Leaves the level instantly; progress is lost | Back opens Pause, and Back again resumes |
| Level start | The fly-over has no title card | Cinematic intro card with district, level number, name, 3★ target and "Tap to start" |
| Crash | Generic message | Crash card names what you hit (wall, car, bollard, traffic, kerb) plus a tip; hit-stop + heavy shake |
| Cones / hard braking | Weak feedback | Light shake tier + haptic tick |
| Car body | Static sprite | Brake dive and acceleration squat (body shifts along heading), shadow follows |
| Result | No sense of progress | "NEW BEST" badge, "LEVEL N+1 UNLOCKED" ribbon, animated coin burst |
| Saving | Progress file written on the main thread | Background writer (ANR safety) |
| Large system font | Landscape layouts can overflow | Cap font scale at 1.15× inside the game |
| Release build | Never run on device | Smoke-test the R8 release on the Realme |
| Theme | No neon themes allowed | **Done in v2:** Neon skin and wheels removed, glow toned down, "Midnight" theme |

## 3. Implementation list
1. `BackHandler` in GameScreen → pause / resume. Dialogs dismiss on back.
2. `IntroCard` overlay while the session is in INTRO, with a slide-in and fade-out.
3. `Hud.crashKind`, plus a crash card with an icon, a cause and a rotating tip.
4. Shake tiers in Camera: `LIGHT 0.25`, `MEDIUM 0.5`, `HEAVY 1.0`. Hit-stop of 90 ms on crash (the world clock freezes, particles keep running).
5. Body-motion offset from longitudinal acceleration, smoothed. Applied to the car sprite and its shadow.
6. Results:
   - `GameRepo.Outcome.newBest` → "NEW BEST" pill.
   - A first clear of n → "LEVEL n+1 UNLOCKED" ribbon.
   - Coin burst particles in the dialog.
7. `GameRepo.saveProgress` → single-thread background executor with atomic rename.
8. `MainActivity.attachBaseContext` → font scale capped at 1.15.
9. Smoke-test the release APK on the device, then reinstall the debug build (debug keeps the fake-ad unlocks).

## 4. Done when
- On the Realme, every item in §3 is visible and screenshot-checked.
- The level suite still passes 10,000 / 10,000.
- The release build launches and plays.
