# INCH PERFECT: v2 upgrade script ("Showroom" update)

Date: 25 Nov 2026 · Builds on [BUILD_SCRIPT.md](BUILD_SCRIPT.md) · Trigger: playtest feedback on the Realme

## 1. Feedback → actions

| # | Feedback | Action in v2 |
|---|---|---|
| 1 | Cars don't look good | New HD car painter with curved bodies, wheel arches, clear-coat shading, glass with interior and reflections, detailed lights. **52 cars** in 12 categories. |
| 2 | Add fancy steering wheels | **34 steering-wheel designs**: 8 rim shapes, 7 spoke layouts and many materials (carbon, leather, wood, chrome, gold, holo…). Shown live in-game and sold in the Garage. |
| 3 | Debug: unlock everything with a fake ad | Every locked item (cars, wheels, paints, finishes, wraps, underglow, world skins, UI themes, levels) gets a **WATCH AD** unlock in debug builds. Release builds hide these buttons until real ads exist. |
| 4 | Only the first 5 levels easy | Handcrafted tutorial is now **levels 1–5** with free rewind. From **level 6** the generator's difficulty curve starts and rises step by step. Mechanics arrive earlier so the early game stays fresh. |
| 5 | Overall graphics too poor | Playfield overhaul: contact shadows (ambient occlusion) under every wall and object, real jersey barriers, detailed roofs (gravel, parapets, HVAC, solar panels, water tanks), tyre-wear lanes, richer asphalt, district colour grading, better hedges and containers. Plus all UI car art from the new painter. |
| 6 | Market analysis | See §2. Every change is checked against what top parking games do well and what players complain about. |

## 2. Market analysis (Nov 2026)

**Leaders and what they sell:**
- **Car Parking Multiplayer and CPM2**: a big garage (100 cars with interiors), deep visual tuning (vinyls, body parts, paint) and the "earn a better car" loop.
- **Dr. Parking 4**: polished visuals and a multi-level difficulty ladder.
- **Car Parking 3D / Modern Car Parking**: garage items bought with coins *or by watching an ad*, plus quick / classic / challenge modes.
- **Parking Jam puzzles**: thousands of bite-size levels.
- **Parking Master (reference)**: 6 cars, 100 levels, paint for stars.

**What players reward:**
- Many cars, and keeping them all in one garage.
- Deep visual customisation.
- A satisfying upgrade loop.
- Realistic-looking cars.
- Responsive controls.
- Fair, steadily rising difficulty.

**What players complain about:**
- Too many forced ads.
- Too few car models ("selection is not so great").
- Limited upgrades.
- Losing progress.
- Controls that feel weird.
- Difficulty that is either flat or spiky.

**Our response:**
- **Variety:** 52 cars (more than any top-down rival) and 34 steering wheels; the steering wheel is a cosmetic none of them offer.
- **Customisation:** paint, 5 finishes, 7 wraps, accent colour and underglow per car.
- **Fair monetisation:** rewarded-only ads, always optional.
- **Difficulty:** one consistent curve.
- **Progress:** offline saves.
- **Presentation:** a showroom-quality garage.

## 3. Car line-up (52)
Every car fits inside the reference envelope (≤ 4.5 m long, ≤ 1.9 m wide, turning ≥ reference), so every car can still finish all 10,000 verified levels. Bigger look ≠ bigger hitbox.

Categories:
- City
- Hatch
- Sedan
- Sport
- Muscle
- SUV
- Off-road
- Pickup
- Van
- EV
- Luxury
- Classic
- Roadster
- Supercar
- Concept
- Legend (bought with stars)

Prices run from free up to 250,000 coins. Legends cost stars.

## 4. Steering wheels (34)
- **Rim shapes:** round, flat-bottom D, yoke, F1 rectangle, butterfly, deep-dish rally, octagon, hexagon.
- **Spokes:** 2, 3, Y, 4, X, 6-hole dish, banjo.
- **Materials:** carbon, black leather, tan leather, wood, chrome, gold, alcantara, holographic, camo, galaxy, lava, ice and more.

Each wheel has its own top marker and hub badge. Players equip it from the Garage → Wheels tab, and it shows in-game instantly.

## 5. Difficulty v2
- **Levels 1–5 (handcrafted, crash-free rewind):** First Gear → Easy Turn → Back It Up → Cone Alley → Rookie Finale.
- **Level 6+:**

  ```
  E(n) = 0.20 + 0.80 · ln(1 + (n−5)/140) / ln(1 + 9995/140)
  ```

  This uses the same 10-level sawtooth as v1, plus the complexity dial.
- **Mechanic introductions:**

  | Level | Mechanic |
  |---|---|
  | 6 | Forward bay |
  | 7 | Reverse bay |
  | 9 | Angled |
  | 11 | Cones |
  | 14 | Slalom |
  | 17 | Parallel |
  | 22 | Garage |
  | 27 | Traffic |
  | 33 | Night |
  | 41 | Tight turns |
  | 51 | Docks |
  | 61 | Hairpins |
  | 81 | Rain |
  | 121 | Bollards |
  | 161 | Turntables |
  | 251 | Pillar garages |
  | 401 | Ice |
  | 601 | Trailers |

- **Re-proof:** all 10,000 levels are re-verified by the autopilot suite.

## 6. Playfield graphics v2
1. **Ambient occlusion:** soft contact shadow on the ground around every solid object.
2. **Jersey barriers:** concrete gradient, top ridge, dark base and reflective strips. Construction district gets orange/white plastic.
3. **Roofs:** gravel texture tinted by roof colour, a raised parapet with highlight, and random HVAC, solar arrays, water tanks, skylights and vents. Lit rooftop beacons at night.
4. **Ground:** tyre-wear lanes along the main driving line, oil drips in bays, stronger asphalt macro variation.
5. **Hedges:** leafy texture with a rounded highlight edge.
6. **Containers:** door ends, ribbing and a stencil band.
7. **Colour grading:** per-district tint layer (warm suburbs, cool tunnels, warm night market).

## 7. Done when
- Unit suite: 10,000 levels with 0 failures; all 52 cars within the envelope; economy sanity checks pass.
- On the Realme: 60 fps on High in heavy scenes; every new screen is screenshot-checked; levels 1–10 replayed.
