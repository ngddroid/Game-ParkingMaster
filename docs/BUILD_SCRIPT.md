# INCH PERFECT: Car Parking (working title)
## Single-phase build script, v1.0 (for approval)

Date: 25 Nov 2026 · Project: `/Volumes/Work/Raj/Android/ParkingMaster` · Test device: Realme RMX5313 (Android 16, Unisoc UMS9230E, 720×1600, 60/90 Hz, 6 GB)

Once you approve this, I run sections 6–13 from start to finish without stopping. The only exceptions are the permission items listed in §14.

---

## 1. Research summary

### 1.1 Reference app teardown
Sources: I played levels 1–3 on the Realme (landscape), read the Play listing and studied your 7 store screenshots.

**Store facts.** "Parking Master: Car Parking" by IVsystems: 1K+ downloads, Puzzle category, rated 3+, updated 25 Sep 2026. It collects and shares no data, is committed to the Families policy, works offline and supports keyboard input. It has 100 levels and 6 cars (Sedan, SUV, Sport Coupe, Muscle, Supercar…), with paint colours bought using stars.

**Observed while playing:**

| Area | What it does |
|---|---|
| Home | Split screen. On the left, a car selector with stat bars (Turning / Size / Accel / Speed), a BUY $ button and paint swatches priced in stars. On the right, a 6×3 paged level grid ("LEVELS 1–18 (1/6)") and a big yellow PLAY button. Stars and cash sit top-left. |
| HUD | Menu button, "LEVEL x / 100", a timer with 3 stars that drop once the target time passes (the timer turns red), and a restart button. |
| Controls | Steering wheel bottom-left that springs back to centre on release, a D/R slide gear, and brake + gas pedals. Needs multi-touch. |
| Camera | Follows the car with look-ahead. A yellow edge arrow points to an off-screen bay. |
| Physics | Gradual acceleration with momentum, so the car keeps rolling after you let go. Tight turning radius. |
| Feedback | Green/yellow/red sensor arcs with beeps, headlight cones, red brake glow, white reverse lights. |
| Fail | Any contact ends the run: "CRASH! Mind the other cars and walls", then Try Again / Menu. |
| Win | Whole car inside the dashed bay, nose matching the arrow, car stopped. Then "PARKED!", a star reveal, "Time 13.4s (target 12s)", "+$5", Next / Replay / Menu and confetti. |
| Tutorial | Text toasts only: L1 "turn the wheel, press gas…", L3 "arrow shows where the nose must point… back in". |
| Level types | Tight perpendicular rows, curb parallel parking, lorry/bus docks, traffic crossings, cone corridors, reverse into garage, multi-lane switchbacks. |

**Weaknesses we will beat:**
1. **The controls cover the play area.** On L3 the target bay sat directly under the steering wheel.
2. **The timer starts as soon as the level loads,** so you lose stars while you are still reading the layout.
3. **Any touch fails from level 1.** I overshot L1 once and crashed, so a beginner fails right away.
4. **There is little to come back for:** 100 levels, no dailies, missions, events or achievements.
5. **The world looks flat.** There is no weather, no height or depth, no overview, minimap or zoom.
6. **Rewards are tiny** ($5 for 1★), so progression is slow. The only things to spend on are 6 cars and some paint.
7. **The level select is a small paged grid** with no sense of a journey.

### 1.2 Market scan (Play search "car parking game", Nov 2026)

| Segment | Examples (★) | Why players install | Gap we fill |
|---|---|---|---|
| 3D sim / open world | Car Parking Multiplayer 4.4, CPM2 4.4, Dr. Parking 4 4.1, Real Car Parking 3.7 | Realism, freedom | Heavy downloads, poor on low-end phones, lots of ads |
| Parking-jam puzzles | Parking Jam 3D 4.0, Car Out! 3.9, Parking Jam Out 4.8, Bus Jam, Car Jam Solver 4.6 | Thousands of short levels | No driving skill involved |
| **Top-down skill parking (our lane)** | Parking Mania / Deluxe 4.4/4.3, Valet Master 4.4, Drive & Park, Drift & Park 3.9, Draw & Park 3.6, Parking Master (ref) | Quick precision challenges | Dated art, few levels, thin meta-game |

**Player complaints across review aggregators:** too many ads (even after paying); controls that feel "weird" or unresponsive; slippery trucks; frustrating parallel parking; difficulty that is either flat or spiky; freezes between levels; expensive car unlocks.
**What players praise:** simple, responsive controls; good graphics; variety; arrow guidance; few ads.

**Positioning:** *The precision driving of top-down parking games, the level count of jam puzzles, and premium 2.5D art in a light (< 30 MB), offline, fair-difficulty package.*

---

## 2. Identity and compliance (avoiding any policy violation)

| Rule | What we do |
|---|---|
| Name | **"Inch Perfect: Car Parking"** (25 characters). No Play app uses "Inch Perfect". "Parking Master" is already used by at least 2 apps (IVsystems, LIHUHU), so we avoid it in the title, keywords and icon. |
| Package | Change `com.ngstudio.parkingmaster` → `com.ngstudio.inchperfect`. It is free to change because the app is not published yet. |
| Look | Our own **"Midnight"** identity: deep navy `#0B1026`, electric cyan `#19E3FF`, sunset orange `#FF7A1A` CTA, lime `#9BFF3C` success, magenta `#FF3DA5` rare. The reference uses yellow/black hazard stripes and blue tiles; we use neither as a brand. |
| Art | 100% original and procedurally drawn in code: cars, props and textures. Cars are fictional, with no real brands, logos or recognisable silhouettes. |
| Fonts | **Bungee** (road-signage display font) and **Exo 2** (UI), both SIL Open Font License and bundled. |
| Audio | Every sound and music loop is synthesised by our own code. Nothing is sampled. |
| Levels | Built by our own generator. No reference layout is copied. |
| Store listing | Original text. Screenshots come only from our build. |
| Data safety | "No data collected / shared" until ads are added; at that point the policy and form get updated. |
| Audience | Recommend **13+ target audience** (IARC "Everyone" content rating) so standard AdMob stays possible later. If you want the Kids/Families programme instead, the ad SDK must be Families-certified. |
| Android 16 | Set `android:appCategory="game"` so the landscape lock is respected on large screens. |

---

## 3. Game design

### 3.1 Core loop
Pick a level → camera fly-over shows the bay (the timer has not started) → drive → park → precision meter + stars + coins → spend coins in the Garage and Store → next level. Daily rewards, missions and the spin wheel bring players back each day.

### 3.2 Controls (3 schemes, chosen during onboarding and changeable in Settings)
1. **Wheel + Pedals (default).** A wheel with ±450° of rotation, optional auto-centre and adjustable sensitivity. Gas and brake pedals that animate as you press them. A big **D | R** toggle by the pedals that changes gear with one tap and a haptic "clunk". A horn button, just for fun.
2. **Arrows.** ◀ ▶ steer buttons and ▲ forward / ▼ reverse pedals. The gear switches automatically.
3. **Tilt.** Steer by tilting the phone, with pedals on screen.

Shared by all three:
- **Left-handed swap.**
- **HUD editor:** drag, resize and set opacity for every control.
- **Haptics.**
- **Keyboard and gamepad:** WASD / arrows / space.
- **Camera framing rule:** the camera always frames the car and the bay inside the area *not* covered by controls. This fixes reference weakness #1.

### 3.3 Car physics (deterministic, fixed 120 Hz)
- **Model.** Kinematic bicycle model with wheelbase L, max steer δ and a steering rate. Speed dynamics cover throttle, rolling drag, braking and automatic-gearbox **creep** (0.6 m/s), which makes fine parking easy. Top speed is 8 m/s forward and 4 m/s in reverse.
- **Visible steering.** The front wheels visibly turn on the sprite.
- **Surfaces.** On rain (grip 0.85) and ice (grip 0.45) we add lateral slip, so cars slide and need longer to brake.
- **Collision.** Rounded-rectangle car shape against boxes, circles and walls using SAT. Sub-steps stop fast cars passing through obstacles.
- **Two kinds of obstacle:**
  - **Solid** (cars, walls, pillars, trucks, buildings): contact = crash.
  - **Soft** (cones, tape barriers): the cone tumbles over with real animation and costs stars, but **does not fail** the level. A 3★ result needs zero cones hit. This is our own twist and feels fairer than the reference's instant fail.

### 3.4 Parking and scoring
- **Parked when:** all 4 car corners are inside the bay, heading is within tolerance and the nose points along the bay arrow, and speed is below 0.05 m/s for 0.5 s. A fill ring and a "click" sound play while it counts.
- **Precision %** combines how centred the car is (offset) and its angle error. **≥ 90% = "INCH PERFECT!"**, which earns a gold banner, +25% coins and counts toward missions and achievements.
- **Timer** starts on your first input, not when the level loads.
- **Stars:**
  - ★ = parked.
  - ★★ = time ≤ t2 and at most 1 cone hit.
  - ★★★ = time ≤ t3 and no cones hit.
  - t3 comes from the level's reference solution (§4.5); t2 = 1.5 × t3.

### 3.5 Forgiveness
- **Levels 1–10:** crashes are free. You get slow-mo and an "Oops!" banner, then the game rewinds 2 seconds automatically. **You cannot lose**, as you asked.
- **Level 11+:** a crash triggers slow-mo, sparks and a camera shake, then the dialog offers Retry, **Rewind** (a booster) or Menu.

### 3.6 Features the reference doesn't have
1. **Intro fly-over** from the bay to the car, with a "Tap to start".
2. **Smart camera.** Auto-zoom keeps the car and the bay in view. A **Peek** button (or pinch) gives a whole-level overview.
3. **Minimap** (can be turned off).
4. **Rewind booster.** Undo the last 3 seconds, crash included.
5. **Ghost Guide booster.** A translucent ghost car drives the verified solution path ahead of you.
6. **Run replay.** Watch your own run again with cinematic auto-camera at 1× or 2×.
7. **Precision scoring** (Inch Perfect %).
8. **Weather and time of day.** Day, golden hour, dusk and night with working headlights, street lamps and underglow, plus rain with puddles and snow/ice. These affect gameplay, not just looks.
9. **2.5D depth.** Buildings, walls, pillars, containers and trucks are extruded with camera parallax. Tree canopies and overpasses draw above the car.
10. **New mechanics:**
    - Moving traffic
    - Timed gates and rising bollards
    - Turntables
    - Ice patches
    - Trailer towing (late game)
    - Collectible coins placed on the solution path
11. **Meta-game:**
    - 20 themed districts
    - Daily login calendar
    - Lucky spin
    - 3 daily missions
    - 40 achievements
    - Star chests
    - Statistics page
    - Daily Challenge level
    - "Test Lot" free-drive sandbox
12. **Garage customisation.** 12 fictional cars; paint in 5 finishes (solid, metallic, pearl, matte, chrome); decals; **underglow** (visible at night); tyre-trail colour; custom number plate; horn sounds.
13. **Store.** World Skins (re-style the whole playfield) and UI themes.

---

## 4. Levels: 10,000

### 4.1 Structure
There are 20 districts of 500 levels each. Every district has its own ground textures, props, palette, ambient sound and a completion reward. Difficulty depends only on the level number (§4.3), so a district changes the look and which mechanic shows up most, not the difficulty curve.

| # | District | Levels | Signature |
|---|---|---|---|
| 1 | Sunny Suburbs | 1–500 | basics, cones |
| 2 | Downtown Grid | 501–1000 | parallel parking, traffic |
| 3 | Harbor Docks | 1001–1500 | lorries, containers, forklifts |
| 4 | Mall Underground | 1501–2000 | pillars, epoxy floor, dim light |
| 5 | Night Market | 2001–2500 | night, carts |
| 6 | Rainy Boulevard | 2501–3000 | rain, puddles |
| 7 | Airport Valet | 3001–3500 | luggage trains, buses |
| 8 | Desert Motel | 3501–4000 | sand drift patches |
| 9 | Snow Peak Resort | 4001–4500 | ice |
| 10 | Stadium Rush | 4501–5000 | dense traffic |
| 11 | Old Town Alleys | 5001–5500 | cobblestone hairpins |
| 12 | Industrial Yard | 5501–6000 | trailers |
| 13 | Skyline Rooftops | 6001–6500 | narrow ramps and bridges |
| 14 | Cyber Grid | 6501–7000 | moving bollards |
| 15 | Beach Boardwalk | 7001–7500 | wooden decks |
| 16 | Construction Zone | 7501–8000 | barriers, gates |
| 17 | Royal Gardens | 8001–8500 | fountains, slaloms |
| 18 | Pit Lane | 8501–9000 | timing |
| 19 | Midnight Tunnels | 9001–9500 | darkness |
| 20 | Legends Plaza | 9501–10000 | everything |

Every 10th level is a **Boss** level (badge on its tile, ×2 coins). Every 100th is a **Master** level (crown, ×3 coins and a chest).

### 4.2 Levels 1–10: handcrafted, impossible to lose
All ten use free rewind, a hand-pointer hint and big bays.

| L | Name | Teaches | Layout |
|---|---|---|---|
| 1 | First Gear | gas / brake | Bay straight ahead 18 m away, 3.6 m wide. |
| 2 | Easy Turn | steering | Open lot. Bay after a wide 90° left turn. |
| 3 | Back It Up | reverse | Bay directly behind the car; shift to R. |
| 4 | Angled Entry | 45° bays | Angled row, mostly empty. |
| 5 | Cone Alley | soft obstacles | 4.5 m cone corridor with a gentle S, then the bay. |
| 6 | Side Street | parallel park | Curb gap 2× the car's length. The ghost demo plays once. |
| 7 | Garage Door | garage | Drive forward into a 3.4 m garage. |
| 8 | Night Shift | night + headlights | L-turn under street lamps. Shows off the lighting. |
| 9 | Crossing | traffic | 2 slow cars (3 m/s) with long gaps. |
| 10 | Rookie Finale (Boss) | combo | Short alley, then reverse into a row bay with 1.0 m clearance per side. Ends with a celebration and the first chest. |

### 4.3 Difficulty: two separate dials so it keeps rising all the way to 10,000

The first dial is **tightness D(n)**, from 0 to 1. It controls clearances, angle tolerance and how tight the star times are.

```
E(n) = 0.22 + 0.78 · ln(1 + (n−10)/150) / ln(1 + 9990/150)          (envelope, n > 10)
D(n) = E(n) · M[(n−1) mod 10]
M = [0.80, 0.84, 0.88, 0.91, 0.94, 0.86, 0.92, 0.95, 0.97, 1.00]   (sawtooth; every 10th = boss)
```

The second dial is **complexity C(n) = ln(1 + n/400) / ln 26**, also 0 to 1. It controls the number of segments, gear changes, traffic density and how many mechanics stack.

How the two dials work together:
- The envelope only ever goes up. Each block of 10 levels starts a little below the previous boss and climbs to a new peak, so players get breathing room without the overall curve going backwards.
- Nothing flattens out early: tightness reaches its physical limit (10 cm per side) only at level 10,000, and complexity keeps growing all the way there.

The two dials map to level parameters like this. Dimensions are for the reference car; the bay gap and corridor are free space per side.

| Level | D | C | Bay gap/side | Corridor/side | Segments | Gear changes | 3★ time × ref |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 11 | 0.18 | 0.01 | 0.70 m | 1.29 m | 1 | 0 | 2.04 |
| 50 | 0.26 | 0.04 | 0.62 m | 1.16 m | 1 | 0 | 1.96 |
| 100 | 0.31 | 0.07 | 0.59 m | 1.10 m | 1 | 1 | 1.92 |
| 500 | 0.49 | 0.25 | 0.45 m | 0.83 m | 2 | 2 | 1.76 |
| 1,000 | 0.60 | 0.39 | 0.37 m | 0.68 m | 2 | 2 | 1.66 |
| 2,000 | 0.71 | 0.55 | 0.29 m | 0.52 m | 3 | 3 | 1.56 |
| 3,000 | 0.78 | 0.66 | 0.24 m | 0.43 m | 3 | 4 | 1.50 |
| 5,000 | 0.87 | 0.80 | 0.18 m | 0.31 m | 4 | 5 | 1.41 |
| 7,500 | 0.95 | 0.92 | 0.13 m | 0.21 m | 4 | 5 | 1.35 |
| 10,000 | 1.00 | 1.00 | 0.10 m | 0.14 m | 5 | 6 | 1.30 |

Other parameters that scale with the dials:
- Heading tolerance: 12° → 3°.
- Traffic speed: 2.5 → 7 m/s.
- Obstacle density: 0.2 → 1.0.
- Mechanic penalty: each harder mechanic (night 0.05, rain 0.05, ice 0.12, trailer 0.25) is subtracted from D for that level. This stops difficulty spiking when mechanics stack.

### 4.4 When each mechanic first appears
Each new mechanic arrives on an easy intro level with an "NEW!" card, then joins the mix.

| Level | Mechanic introduced |
|---|---|
| 11–30 | Forward, reverse and angled bays |
| 31 | Parallel parking for real |
| 41 | Reverse into garage |
| 51 | Traffic |
| 61 | Night |
| 71 | 3-point turn in a dead end |
| 81 | Lorry docks |
| 101 | Rain |
| 151 | Gates and bollards |
| 201 | Turntable |
| 301 | 3-segment courses |
| 401 | Pillar garages |
| 501 | Ice |
| 751 | Trailer towing |
| 1001+ | Everything mixed, weighted by district |

### 4.5 Generator: "path-first", so every level is solvable by construction
1. **Seed.** `seed = hash(level, generatorVersion)` using a PCG32 random number generator. All trig uses `StrictMath`, so the device and the JVM test produce identical levels.
2. **Archetype.** Choose a final parking archetype: forward bay, reverse bay, angled, parallel, garage, dock, turntable or trailer bay. Then add `segments(C)−1` lead-in segments: S-bend alley, hairpin, slalom, traffic crossing, timed gate, dead-end 3-point turn or ice patch. Segments are laid out end to end with an overlap check.
3. **Reference solution.** Build it first, as a sequence of motion primitives (gear, steer, distance) for the **reference car**. The reference car is the worst case of every vehicle in the game: the largest footprint and the widest turning circle. Every purchasable car is the same or better on every stat, so **every car can solve every level**. Bus, truck and trailer "job" levels use their own fixed vehicle.
4. **Swept area.** Simulate the reference car along the solution every 2 cm and mark the area it sweeps on a 5 cm grid, with a distance field.
5. **Obstacle placement.** Put neighbour cars, walls, curbs, cones and pillars on their template lines, then slide each one toward the swept area until it is exactly the clearance c(D) away (± a small jitter). This is how "inches to spare" levels get made deliberately instead of by luck.
6. **Decoration and coins.** Decorations go outside the swept area plus a margin. Collectible coins go *on* the solution path, so they can always be reached.
7. **Timing.** Traffic and gates are periodic. The level is accepted only if every crossing has a gap of at least 1.5× the time needed to cross.
8. **Validation.** Any failure re-rolls with `seed + attempt` (deterministic, up to 64 tries). After that it falls back to a simpler archetype, and the test suite reports how often that happens; the target is 0.
9. **Speed.** Generation takes under 50 ms on the device. There are no 10,000 level files; the APK stays tiny.

### 4.6 What "all levels perfect" means, and how it is proven
The JVM test suite generates **all 10,000 levels** and checks each one:
- (a) The reference path is collision-free with clearance ≥ c(D) − 1 cm.
- (b) The start pose and the bay are valid; nothing overlaps; everything is inside the world bounds.
- (c) **An autopilot** (pure-pursuit steering plus a speed profile, waiting for traffic gaps) **drives the level with the real game physics and must park it.** That run also produces the 3★ reference time.
- (d) D(n) stays within ±0.02 of the curve.
- (e) Golden hashes freeze the levels, so an app update can never silently change a level a player has already beaten.

Each other car is also tested on 500 random levels. In debug builds the same autopilot powers an **"Auto-solve" button** for on-device checks. Players get it as the Ghost Guide booster.

---

## 5. Art direction and rendering (the largest share of the effort)

**Style:** *"Premium diorama."* Top-down 2.5D with soft directional shadows set by time of day, extruded walls and buildings with parallax, rich tileable textures, warm and cool lighting, particles and smooth 60 fps.

**Procedural assets** are drawn by code at device resolution (HD on any screen) and cached:
- **Textures (512² tileable, theme-specific):**
  - asphalt (multi-octave noise, aggregate specks, tar seams)
  - concrete panels
  - cobblestone (Voronoi)
  - pavers
  - grass
  - sand
  - snow
  - diamond-plate metal
  - glossy garage epoxy
  - wooden boardwalk
  - plus a low-frequency macro overlay so the tiling never shows a repeat
- **Decals:** lane lines, bay numbers, arrows, crosswalks, cracks, oil stains, manholes, drains, puddles (with reflections in the rain) and skid marks that stay for the whole level.
- **Cars:** 12 models, drawn at 2× resolution. Each has:
  - a body with a light gradient
  - hood and roof seams
  - glass with a reflection streak
  - mirrors and roof variants (taxi sign, rack, sunroof, stripes)
  - head- and tail-light shapes
  - visibly steering front wheels
  - the 5 paint finishes and decals
  - a pre-blurred soft shadow

  Parked traffic is built from randomised variants of the same models.
- **Props:**
  - cones (which tumble)
  - barriers, bollards
  - trees with layered canopies drawn above the car, planters, palms, benches
  - lamp posts with light pools at night
  - dumpsters, carts, luggage trains, forklifts
  - corrugated containers, trucks with ribbed trailers, buses with roof AC units
  - pillars with hazard bases
  - building roofs with HVAC units and skylights
  - harbour water with shimmer
  - snow piles, crates

**Render pipeline per frame:**
1. Ground shader
2. Decals and skid marks
3. Shadows
4. Ground-level props
5. Bay: animated dashed glow, arrow, fill ring
6. Coins, traffic, gates, turntable
7. Player car with lights
8. Particles: smoke, dust, sparks, splashes, confetti
9. Extruded tall objects and canopies
10. Night light-map: a darkness layer with light holes cut by headlights, lamps and underglow
11. Weather: rain streaks, snow
12. Vignette and grading (crash flash, slow-mo desaturation)
13. Sensor arcs, off-screen arrow, ghost car
14. Controls

**World Skins** (Store) re-style the whole playfield: **Classic**, **Blueprint** (white technical-drawing lines on blue), **Autumn Leaves**, **Toy Town** (glossy plastic), **Pixel Retro** (low-res render scaled up) and **Golden Hour**.

**Juice:**
- Spring-animated chunky buttons with a 3D lip and gloss.
- Coins fly to the counter.
- Stars burst one by one.
- Numbers count up.
- The camera kicks on crash.
- Brake dive (the shadow shifts).
- Dust when pulling away.
- The bay pulses brighter as you get closer.
- Confetti and fireworks on boss levels.

---

## 6. Audio (all synthesised in code; no files to license)
- **Engine.** A live synth on `AudioTrack`. Firing frequency follows RPM, with 2 detuned oscillators plus filtered noise; throttle opens up harmonics. Each car has its own profile: V8 rumble (Bruiser), EV whine (Ion), and so on.
- **Sound effects** (rendered to cache at first launch, played with `SoundPool`):
  - gear clunk, reverse beeper
  - sensor beeps that speed up as you get closer
  - brake squeak, tyre screech
  - crash plus glass, cone thud
  - park "click", star dings (3 pitches)
  - coin chime, chest open, purchase
  - UI tap and whoosh, spin-wheel ticks
  - horn variants
  - rain, city, harbour and wind ambience
- **Music.** 4 loops generated by our own sequencer: menu (synthwave, 96 bpm), gameplay (chill lo-fi, 84 bpm), night (darker, 100 bpm) and a victory stinger. Music ducks under sound effects.
- **APK cost:** about 0 MB, because the sounds are rendered on the device on first launch.
- **Honest note:** I cannot listen to audio. I will check levels, clipping and spectra with tools, but **you need to judge the sound by ear.** Licensed packs can replace any slot later.

---

## 7. Screens and UX (the whole app runs in landscape)

**Splash.**
1. The Android 12+ system splash shows the themed icon.
2. Then a custom animated splash of about 2 s: a night lot, a glowing "P" sign, a car that sweeps its headlights and reverses into a glowing bay, and the **INCH PERFECT** logo stamped in with animated ruler ticks.
3. The loading bar tracks real asset warm-up (textures, sprites, audio render).

**Onboarding.** First launch only, with **no skip button**. There are 4 glass cards over an animated city: Next button, swipe, a progress indicator, and Back to return to the previous card.
1. "Take the wheel": live controls demo, with the choice of Wheel / Arrows / Tilt.
2. "Every inch counts": sensors and the precision meter.
3. "Earn, collect, customise": coins flying into the garage, paint changing.
4. "10,000 levels · 20 districts": a fly over the city map, then the **START ENGINE** button.

**Home, with a bottom bar of 5 items:** `Garage · Store · ▶ PLAY (raised, centre) · Levels · Rewards`.
- Top bar: rank badge with an XP ring ("Rookie Valet" → "Legend"), stars, coins (+), settings gear.
- Centre: **Showroom** with the current car on a spotlit turntable, reflections and floating particles; a district banner ("Sunny Suburbs · 37/500"); a big **LEVEL 38** continue button.
- Side cards: Daily Challenge (countdown), Lucky Spin (FREE badge), Missions 2/3, Star Chest progress bar.
- Background: a parallax dusk skyline with moving traffic lights.

**Levels.** A horizontally scrolling **City Map** of 20 illustrated district cards with progress and locks. Tapping a district opens its 500-level grid with 10 tiles per row. Each tile shows its number, stars and a boss or crown badge. The current level pulses, and a "Jump to current" button is always available.

**Garage.**
- A car carousel with stat bars (Size, Turning, Acceleration, Top speed, Braking) and Buy / Select buttons.
- Customise tabs: Paint, Finish, Decals, Underglow, Tyre trail, Plate, Horn.
- A live preview, plus a **Test Lot** button for free driving.

**Store.** Tabs for World Skins, UI Themes (Midnight, Sunset Drive, Mint, Royal Velvet, Carbon Gold), Boosters, Chests, and Free Coins (simulated ad, debug only for now).

**Rewards.** 7-day login calendar, Lucky Spin (8 segments, physics spin), 3 daily missions, 40 achievements and Statistics: parks, Inch Perfects, distance driven, distance in reverse, crashes.

**Settings:**
- **Audio:** music, SFX and engine volumes.
- **Controls:** scheme, sensitivity, auto-centre, left-handed, HUD editor.
- **Graphics:** Auto / Low / Medium / High / Ultra; 60 / 90 fps.
- **Gameplay:** minimap, sensors, colour-blind sensor colours, haptics.
- **About:** **Terms of Use** and **Privacy Policy**, each opening its own screen with a themed `assets/legal/*.html` file in a WebView (JavaScript off, local file only). Also Rate, Share, Version, and Reset progress (with confirmation).

**Game HUD.**
- Pause (top-left).
- Centre capsule: "LEVEL 38 · Sunny Suburbs", the timer and a star-threshold bar.
- Restart and Peek (top-right), minimap below them.
- Booster column: Rewind ×n, Ghost ×n.
- Controls along the bottom.

**Dialogs:**
- **Pause:** Resume, Restart, quick sound and haptics toggles, Levels, Home.
- **Crashed:** Rewind, Retry, Menu.
- **Parked:** the precision meter animates, stars pop in, time is shown against the target, coins count up, plus a debug-only "×2 coins (Ad)". Buttons: Next, Watch Replay, Retry, Menu.
- **Celebrations:** New Mechanic card, District Complete, Chest opening.

---

## 8. Economy (coins and stars)

**Coins earned per level:**
- Base = `20 + n/50`, capped at 120.
- Star multiplier: 1★ ×1, 2★ ×1.5, 3★ ×2.
- Inch Perfect: +25%.
- Boss ×2, Master ×3.
- Replays pay 20%.
- On-map coins: 5 each.

**Cars:**

| Car | Price |
|---|---|
| Rookie (starter) | free |
| Minnow | 2,500 |
| Kestrel | 6,000 |
| Grizzly | 12,000 |
| Ion | 20,000 |
| Hailer | 30,000 |
| Bruiser | 45,000 |
| Packmule | 60,000 |
| Midnight | 85,000 |
| Arrowhead | 120,000 |
| Nimbus | 170,000 |
| Gilded Legend | 20,000 ★ (prestige) |

**Other prices:**
- **Paints:** 300–1,500 coins. **Finishes:** paid in stars.
- **World skins:** 5,000–25,000. **UI themes:** 3,000.
- **Boosters:** Rewind 150, Ghost 250, Slow-mo 100.
- **Daily login:** 100 / 150 / 200 / 300 + booster / 400 / 500 / chest (1,000 + paint).
- **Star chest:** every 50 ★.

At this pace a player can buy the second car around level 60 and something new every 10–20 minutes of play. A unit test simulates the economy to confirm this.

---

## 9. Ads placeholder (exactly as you asked)
- **All builds:** only level 1 starts unlocked. Finishing a level unlocks the next one.
- **Debug:** tapping **any** locked level opens a themed "Watch Ad to unlock Level N" dialog. **Watch Ad** shows a 1.5 s "loading ad…" stub, then unlocks the level and offers Play. Other rewarded slots (×2 coins, free spin, free booster, rewind) are simulated the same way.
- **Release:** tapping a locked level shakes the tile and shows "Finish level N−1 first". All ad buttons are hidden.
- **Code:** a `RewardedAds` interface with two implementations, `SimulatedRewardedAds` (debug) and `DisabledRewardedAds` (release), chosen by `BuildConfig` flags. AdMob later is a drop-in third implementation.

---

## 10. Technical architecture
- **Stack:** Kotlin, Jetpack Compose (menus and HUD) and a custom 2D engine. The engine renders on a `SurfaceView` with `lockHardwareCanvas` on its own Choreographer-synced thread: 120 Hz fixed physics with interpolated rendering. Touch for the driving controls is handled by the engine (true multi-touch, no frame lag).
- **No third-party game engine.** The dependencies we add are all AndroidX: navigation-compose, lifecycle-viewmodel-compose, datastore-preferences and core-splashscreen.
- **Saves:** settings and wallet in DataStore. Level stars and best times in a compact atomic binary file (about 30 KB for 10,000 levels).
- **Packages:** `core/` (prefs, progress, wallet, haptics), `ads/`, `audio/` (dsp, engine synth, sfx, music), `game/{math,physics,level,world,render,input}`, `ui/{theme,components,splash,onboarding,home,levels,garage,store,rewards,settings,legal,game}`, `nav/`, `debug/`.
- **Pure-Kotlin game logic:** physics, level generation and autopilot have no Android dependencies, so the full 10,000-level suite runs on the JVM.
- **Debug menu** (debug builds only): jump to level, Auto-solve, show colliders, FPS overlay, add coins, reset onboarding.
- **Manifest:** landscape (`sensorLandscape`), `appCategory="game"`, immersive full-screen, display cutout handled, predictive back, 16 KB page-size compliant.

## 11. Performance budget (Realme UMS9230E / Mali-class GPU)
- **Frame rate:** steady 60 fps on High; frame time p95 < 14 ms. 90 fps optional on Ultra.
- **Rendering:** at most ~400 draw calls per frame, at most 1 full-screen layer (only for night).
- **Memory:** under 250 MB PSS; sprite cache capped at 64 MB.
- **Speed:** cold start to Home < 2.5 s; level load < 150 ms.
- **Size:** AAB < 30 MB.
- **Auto quality:** a short benchmark during the first level picks the graphics preset.

---

## 12. Build steps (one phase, executed in this order)
1. **Setup.**
   - Rename the app to "Inch Perfect", the package to `com.ngstudio.inchperfect` and the Gradle project name.
   - Add the dependencies and build flags (`ADS_SIMULATED`).
   - Manifest changes: landscape, game category, immersive.
   - Add the Bungee and Exo 2 fonts. 🔒 download
   - Release build signed with the debug key for local testing only.
2. **Engine core:** math, PCG32, collision (SAT), car physics, fixed-step loop. Plus unit tests.
3. **Levels:**
   - data model
   - handcrafted 1–10
   - difficulty curves
   - 16 archetype and segment generators
   - swept-area obstacle placement
   - validator
   - autopilot
   - the 10,000-level JVM suite with golden hashes
4. **Renderer:**
   - texture and sprite factories
   - camera (fly-over, framing, peek)
   - 2.5D extrusion
   - lighting
   - particles
   - weather
   - sensors, ghost, minimap
   - controls (3 schemes + HUD editor)
   - quality presets
5. **Audio:** DSP library, engine synth, SFX and music rendered to cache, mixer with ducking.
6. **Game session:**
   - states: intro, playing, paused, crashed, parked
   - scoring, stars, coins, cones
   - free rewind for levels 1–10
   - boosters: rewind, ghost, slow-mo
   - replay recorder and player
7. **App UI:**
   - theme and components (chunky buttons, glass panels, counters, animated background)
   - splash, onboarding
   - Home and bottom bar
   - Levels map and grid
   - Garage, Store, Rewards (calendar, spin, missions, achievements, stats)
   - Settings, Legal screens with HTML
   - game HUD and all dialogs
8. **Economy and progression:** wallet, unlocks, district rewards, chests, the ads placeholder from §9.
9. **App icon and store art:**
   - adaptive icon with foreground, background and monochrome layers: an orange top-down car sliding into a glowing cyan dashed bay with ruler ticks, on midnight navy
   - Play icon 512 PNG and a 1024×500 feature graphic
10. **Polish pass:** juice, transitions, haptics, empty and error states, string resources ready for translation.
11. **Verification on the Realme (§13), then fix and repeat until every gate passes.**

## 13. Testing on the Realme (quality gates)
- **On the computer:**
  - `./gradlew testDebugUnitTest`: physics, economy and **all 10,000 levels (static checks + autopilot)** pass, with 0 fallbacks.
  - `lint` clean.
  - `assembleRelease` with R8 builds and runs.
- **Install and play on the device.** I drive it with my multi-touch injector (built during this research):
  - **I play levels 1–10 by hand** and record a screenshot of every step.
  - Then spot levels 11, 25, 50, 100, 250, 500, 1,000, 2,500, 5,000, 7,500 and 10,000 via the debug unlock, by hand and with Auto-solve.
- **Every screen and every dialog** gets a screenshot pass with layout, cutout and text-clipping checks.
- **Performance:** `dumpsys gfxinfo` frame stats on the heaviest scenes (night + rain + traffic), `meminfo` and cold-start time.
- **Robustness:**
  - monkey test: 10,000 events, no crash or ANR
  - back, home and resume, process death, mute switch, low battery
  - the **release APK** smoke-tested on the device
- **Bug handling:** fix every issue found and re-run the gates. You get a final report with screenshots.

## 14. Approval checklist (defaults in **bold**; tell me if you want something different)
1. **Name:** **"Inch Perfect: Car Parking"**, or your own name. I do *not* recommend "Parking Master".
2. **Package:** **change to `com.ngstudio.inchperfect`**, or keep `com.ngstudio.parkingmaster`.
3. 🔒 **Downloads needed:** Bungee-Regular.ttf and Exo2 variable .ttf from `github.com/google/fonts` (OFL licence, about 0.3 MB in total). Nothing else is downloaded.
4. **Legal page details:** the developer name and support email to show in Terms and Privacy. I'll use the placeholder **"NG Studio" / support@example.com** until you give real ones.
5. **Release signing:** **use the debug key for local release testing**, until you supply your upload keystore.
6. **Device time:** the Realme is also being used by another session (`com.ngstudio.lanternleap` came to the foreground while I played). I need the phone free during the §13 test runs.
7. **Version control:** the folder is not a git repo. **Run `git init` and keep it uncommitted**, or I can commit at each milestone if you prefer.

## 15. Risks and honest notes
- **Scope.** This is a very large build. Procedural art quality comes from iterating against device screenshots, and I will budget most of that time on the playfield, as you asked.
- **What "all 10,000 levels perfect" means:** every level is *proven* solvable by an automated driver using the real physics, and *proven* to follow the difficulty curve. It does not mean every level is individually hand-designed (only levels 1–10 are).
- **Audio** needs your ears (see §6).
- **Play publishing** will additionally need a hosted Privacy Policy URL. In-app HTML alone isn't enough for the Play Console.
- **The reference app** was used only for study. None of its assets, levels or text is reused.
