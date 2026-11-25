# BioBuzz sim: engine/browser contract

The Java engine owns time, physics, the game and the team's OpModes. The browser draws
and sends input. This file is the single source of truth for both sides.

## Transport

- Static files: `http://localhost:8790/` serves `web/`.
- WebSocket: `ws://<host>:<http port + 1>` (default `ws://localhost:8791`). JSON text
  frames, one object per frame, every object has a `type`.
- The page must reconnect automatically (retry every 1 s) and show a "disconnected"
  badge while the socket is down.

## Units and frames

- Inches, seconds, radians (heading), degrees only where a field name ends in `Deg`.
- Field frame (Radical's own): origin at field center, `+x` toward the AUDIENCE,
  `+y` toward the BLUE alliance wall, `z` up. Heading 0 looks along `+x`,
  counterclockwise positive (seen from above).
- Robot frame: `+x` forward (intake end), `+y` left, `z` up. The launcher throws out of
  the BACK (`-x`).
- three.js mapping (y up): `three = (X: field.y, Y: field.z, Z: field.x)`. A robot model
  built with forward = +Z, left = +X, up = +Y is placed with `rotation.y = heading`.
  Default camera sits on the audience side (+Z) looking at the field: red alliance on
  the left, blue on the right, as in the manual figures.

## Server -> browser

### `hello` (on connect, and again after every code reload)

```json
{
  "type": "hello",
  "code": {"path": "/abs/path/to/clone", "branch": "my-branch", "commit": "abd004c",
           "dirty": false, "compiledAt": "16:20:01", "ok": true,
           "errors": [{"file": "utility/Robot.java", "line": 12, "msg": "..."}]},
  "opModes": [{"name": "BlueHiveFacingAUTO", "group": "Autonomous", "flavor": "AUTO"},
              {"name": "compTeleOp", "group": "Robot", "flavor": "TELEOP"}],
  "knobs": {"launchAngleDeg": 72.0},
  "knobMeta": [{"key": "launchAngleDeg", "label": "Launch angle (deg)", "group": "Launcher",
                "min": 30, "max": 89, "step": 0.5}],
  "field": { "...": "see Field constants" },
  "robot": {"length": 17.6, "width": 17.6, "height": 17.0, "wheelRadius": 2.05,
            "launchExitX": -2.0, "launchExitZ": 15.0}
}
```

`code.ok = false` means the team code did not compile: show `errors` prominently and
disable INIT. `opModes` is then empty.

### `state` (about 30 per second)

```json
{
  "type": "state", "t": 12.345, "speed": 1.0,
  "robot": {"x": -63.0, "y": 13.6, "h": 3.13,
            "est": [-63.0, 13.6, 3.13],
            "wheels": [0.0, 0.0, 0.0, 0.0],
            "intake": 0.0, "feedL": 0.0, "feedR": 0.0, "feedLaunch": 0.0, "flywheel": 0.0,
            "flyVel": 1320.5, "flyTarget": 1325.0, "held": 4},
  "balls": [[17, 0, -70.6, 70.6, 1.4]],
  "hives": {"blue": {"phi": 30.0, "pollen": 0, "nectar": 3, "tips": 0},
            "red":  {"phi": -30.0, "pollen": 0, "nectar": 3, "tips": 0}},
  "match": {"phase": "AUTO", "clock": 17.3, "alliance": "BLUE",
            "blue": {"total": 23, "leave": 3, "park": 0, "tips": 1, "tipPts": 20, "cell": 0},
            "red":  {"total": 0, "leave": 0, "park": 0, "tips": 0, "tipPts": 0, "cell": 0}},
  "op": {"name": "BlueHiveFacingAUTO", "status": "RUNNING"}
}
```

- `robot.x/y/h`: TRUE pose. `robot.est`: the team's localizer estimate, or `null` when
  the running OpMode has no localizer (e.g. compTeleOp).
- `robot.wheels`: accumulated wheel angles in radians, order `[frontLeft, frontRight,
  backLeft, backRight]`, positive = rolling forward. `intake`, `feedL`, `feedR`,
  `feedLaunch`, `flywheel` are accumulated mechanism angles in radians (spin the model
  parts by these). `flyVel` and `flyTarget` are launcher ticks per second. `held` is the
  number of balls inside the robot (0 to 4); draw that many balls in the hopper.
- `balls`: every ball that is NOT inside the robot: `[id, kind, x, y, z]`, z = center
  height. `kind`: 0 POLLEN (yellow, radius 1.4), 1 red NECTAR (radius 1.8), 2 blue NECTAR
  (radius 1.8). Includes balls on the floor, flying, stacked in FLOWERS and resting in
  CELLs (the engine computes their positions, also while a HIVE tips). Ids are stable.
- `hives.*.phi`: seesaw angle in degrees. `+30` = the BACK (`-x`) end is up, `-30` = the
  AUDIENCE (`+x`) end is up; animates between the two during a TIP.
- `match.phase`: `IDLE`, `AUTO`, `TRANSITION`, `TELEOP`, `DONE`. `clock` = seconds left
  in the phase (0 in IDLE and DONE).
- `op.status`: `NONE`, `INIT`, `RUNNING`, `STOPPED`, `ERROR`.

### `telemetry`

`{"type": "telemetry", "lines": ["Velocity : 1320.0", "..."]}`: what the OpMode's
`telemetry.update()` last published (Driver Station screen). Replace the panel content.

### `overlay`

`{"type": "overlay", "kind": "preview" | "live", "ops": [...]}`: field drawings made by
the team's own RoadRunner code (FTC Dashboard canvas operations, field frame, inches).
`preview` = planned path of the whole action sequence, sent when it starts; keep it
until the next `preview` or a `clear`. `live` = redrawn every loop (target pose, pose
history, current segment); replace the previous `live` set. `{"type":"overlay","kind":"clear"}`
wipes both. Ops, in drawing order (stroke and width apply to the ops after them):

```json
{"op": "stroke", "color": "#4CAF50"}
{"op": "fill", "color": "#7C4DFF"}
{"op": "width", "w": 1}
{"op": "polyline", "x": [0, 1], "y": [0, 1]}
{"op": "polygon", "x": [..], "y": [..], "fill": false}
{"op": "circle", "x": 0, "y": 0, "r": 9, "fill": false}
```

Colors may carry alpha (`#RRGGBBAA`). Draw them flat on the floor (about 0.3 in up).
The browser draws its own trail of the TRUE pose from `state` (clear it on `reset` and
when an OpMode starts).

### `event`

`{"type": "event", "t": 12.3, "kind": "...", "msg": "human readable", ...}`. Kinds:
`shot` (`flyVel`, `exitSpeed`), `score` (`hive`), `miss` (`why`: short, long, wide,
underside, wrongside), `tip` (`hive`, `points`, `period`), `intake`, `leave`, `park`,
`opmode` (status changes), `log`, `error` (`stack` optional). Append to an event log
panel, newest on top; flash `tip` and `error`.

### `report`

`{"type": "report", "text": "...multi-line summary...", "data": {...}}` at the end of a
run. Show `text` in a dismissible panel.
`{"type": "report", "clear": true}` is sent when a new run starts: hide the previous report.

## Browser -> server

- `{"type": "select", "opMode": "BlueHiveFacingAUTO"}`
- `{"type": "init"}`, `{"type": "start"}`, `{"type": "stop"}` (Driver Station buttons)
- `{"type": "gamepad", "id": 1, "lx": 0, "ly": 0, "rx": 0, "ry": 0, "lt": 0, "rt": 0,
   "b": ["a", "left_bumper"]}`: RAW values (no deadzone; the engine applies the SDK's),
  stick y is negative when pushed away (browser and FTC agree). `b` lists the buttons
  currently down, using FTC SDK names: `a b x y dpad_up dpad_down dpad_left dpad_right
  left_bumper right_bumper left_stick_button right_stick_button start back guide`.
  Send on change and at least every 100 ms while anything is non-zero; send one all-zero
  message when a controller disconnects or the window loses focus.
- `{"type": "reset"}`: reset field, balls, score, HIVEs and put the robot on its start pose.
- `{"type": "place", "x": 0, "y": 0, "h": 0}`: put the robot somewhere (only while no
  OpMode is RUNNING).
- `{"type": "alliance", "value": "BLUE"}` or `"RED"`.
- `{"type": "knobs", "values": {"launchAngleDeg": 70}}`: change tuning knobs (persisted).
- `{"type": "reload"}`: recompile the team's code; a new `hello` follows.
- `{"type": "speed", "value": 1.0}`: sim time scale (0 pauses; 0.25, 0.5, 1, 2, 4).
- `{"type": "match", "action": "full", "auto": "BlueHiveFacingAUTO", "teleop": "compTeleOp"}`:
  run AUTO (30 s), TRANSITION (8 s), TELEOP (2:00) back to back.

## Input mapping (browser)

- gamepad 1 = first connected controller (tested with a Stadia controller over USB,
  vendor 18d1 product 9400).
  Use the Gamepad API `standard` mapping when the browser reports it: axes 0-3 = lx, ly,
  rx, ry; buttons 0 a, 1 b, 2 x, 3 y, 4 left_bumper, 5 right_bumper, 6 lt, 7 rt, 8 back,
  9 start, 10 left_stick_button, 11 right_stick_button, 12-15 dpad up/down/left/right,
  16 guide. If `mapping` is not `standard`, offer a calibration dialog (press each
  control once) and store the result in localStorage.
- If no controller is connected, keyboard fallback for gamepad 1: T/G = left stick
  forward/back, F/H = strafe left/right, R/Y = turn left/right.
- gamepad 2 = keyboard: `Q` left_bumper, `B` b, `ArrowUp`/`ArrowDown` dpad_up/dpad_down,
  `W`/`S` left stick y (-1/+1), `I`/`K` right stick y (-1/+1). Show a legend. Radical's
  compTeleOp reads: left_bumper = launcher on, b = launcher off, dpad = speed nudge,
  left stick y = launcher feeder (pull back to feed), right stick up = intake.

## Field constants (also sent in `hello.field`)

```json
{
  "half": 72, "tile": 24, "wallHeight": 12,
  "lz": {"blue": {"x0": 24.5, "x1": 47.5, "y0": 61, "y1": 72},
         "red":  {"x0": -47.5, "x1": -24.5, "y0": -72, "y1": -61}},
  "garden": {"blue": {"x0": -72, "x1": -70, "y0": 49, "y1": 72},
             "red":  {"x0": 70, "x1": 72, "y0": -72, "y1": -49}},
  "flowers": [{"x": -68.6, "y": -24, "wall": "back"}, {"x": -24, "y": 68.6, "wall": "blue"},
              {"x": 24, "y": -68.6, "wall": "red"}, {"x": 68.6, "y": 24, "wall": "audience"}],
  "hive": {"pivotZ": 43.95, "blueY": 12.75, "redY": -12.75,
           "frameHalfW": 24.73, "frameHalfD": 19.48,
           "cellInner": 9.42, "cellOuter": 21.46, "cellHalfW": 10.0,
           "cellFloorW": -1.36, "cellSide": 7.61, "cellPeak": 14.0, "tiltDeg": 30}
}
```

Geometry notes (manual TU10, section 9):

- Walls at x = +-72 and y = +-72. Audience wall x = +72, back wall x = -72, blue wall
  y = +72, red wall y = -72. Red ALLIANCE AREA is outside the red wall, blue outside the
  blue wall.
- LOADING ZONE: taped box 23 x 11 in on the own alliance wall. GARDEN: 23 x 2 in strip.
- FLOWER: wall mounted, top ring 21.5 in up with a 4 in opening, green pipes, lower ring
  on the floor; POLLEN stack inside (the engine sends their positions).
- HIVE Structure: two A-frame triangles in the planes y = +-frameHalfW, feet at
  x = +-frameHalfD, apex at (x 0, z pivotZ), joined by a crossbar along y at the apex.
  Blue HIVE pivots at (0, blueY, pivotZ), red at (0, redY, pivotZ). Each HIVE is a rigid
  seesaw along x. In seesaw-local coordinates (u = distance from the pivot along the
  arm, w = perpendicular "up" of the cell, v = along y from the HIVE center) each CELL
  occupies u in [cellInner, cellOuter], |v| <= cellHalfW, and its cross-section is a
  pentagon: floor at w = cellFloorW, straight side walls cellSide tall, then a peak at
  w = cellFloorW + cellPeak over v = 0. The OPENING is the pentagon at u = cellOuter (the
  outer end); the other faces are clear panels. With phi in degrees (+30 = back end up):
  back cell arm direction = (-cos phi, 0, sin phi), audience cell arm direction =
  (cos phi, 0, -sin phi), cell "up" for both = (sin phi, 0, cos phi), in field (x, y, z).
- Start of match: blue phi = +30 (back cell up), red phi = -30 (audience cell up); each
  upward CELL holds 3 NECTAR of its color.
