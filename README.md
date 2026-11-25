# BioBuzz simulator for Radical (FTC 7196)

A 3D BIOBUZZ field on your laptop that runs Radical's real Java OpModes. Change the robot
code, press **Reload code**, and watch what the robot would do: drive the autos, launch
POLLEN, tip the HIVE, score a match. No robot needed.

The simulator compiles the `TeamCode` folder of the robot repository exactly as it is on
your disk (whatever branch is checked out), runs the OpModes against a simulated robot, and
draws the result in the browser. It never writes into the robot repository.

Right now it knows one robot: Radical's. Other teams, see
[Using it for another robot](#using-it-for-another-robot).

## What you need

- A Mac, Windows or Linux laptop. A Chromebook will not work.
- A JDK, version 17 or newer. It has to be a full JDK, not only a JRE, because the simulator
  compiles the robot code itself. [Temurin 21](https://adoptium.net/) works. Check with
  `java -version`.
- Git.
- Chrome.
- Internet for the first run. It downloads Gradle and a few libraries, which takes a few
  minutes. After that it starts in seconds.

## Get it running

Put the robot code and the simulator side by side in one folder:

```sh
git clone -b limlitConfig https://github.com/Pleasant-Valley-Robotics/26-27_BioBuzz_7196.git
git clone https://github.com/Pleasant-Valley-Robotics/26-27_BioBuzz_Simulator.git
cd 26-27_BioBuzz_Simulator
./sim
```

`-b limlitConfig` checks out the team's working branch. The robot repository's default
branch (`competition`) is older and does not compile in the simulator, because it uses
camera classes the simulator does not have.

On Windows the launcher is `sim.bat`: type `sim` in Command Prompt or `.\sim` in PowerShell,
with the same arguments as `./sim` everywhere below. The Windows launcher has not been tried
on a real Windows machine yet. If it fails, show a mentor what it printed.

`./sim` builds the engine, starts it, and opens http://localhost:8790/ in the browser.
Stop it with Ctrl+C in the terminal.

Options: `./sim --no-open` (do not open the browser), `./sim --port 9000`,
`./sim --clone /path/to/the/robot/repo` (when the robot code is not in the folder next to
the simulator).

## The edit and test loop

1. In the robot repository, check out the branch you want to work on and edit the code
   (Android Studio is fine). The simulator reads the files on disk, so there is nothing to
   commit or push first.
2. In the page press **Reload code**. Compile errors show up in the Driver Station panel.
   An error like "cannot find symbol" or "package ... does not exist" for an FTC class
   (VisionPortal or AprilTag, for example) means that branch uses a part of the FTC SDK the
   simulator has no stand-in for yet.
3. Pick an OpMode, press **INIT**, then **START**. **STOP** works at any time.
4. For an auto, a report appears when the 30 s are over: score, LEAVE, PARK, every shot
   with the launcher speed at the moment it was fed, and how far the robot ended from the
   last path target.

INIT on an auto starts a fresh match: the field is reset and the robot is placed on the
start pose written in that OpMode (the first `new Pose2d(...)`). **Run full match** runs
AUTO (0:30), the 0:08 transition, then TELEOP (2:00) with the two OpModes you picked.

## Controls

Gamepad 1 (drive) is the first controller Chrome sees, plugged in over USB. It was tested
with a Stadia controller. Press any button on it once so Chrome can see it.
If Chrome does not report a standard mapping, a **Calibrate** button appears in the
Controllers panel. With no controller, gamepad 1 is on the
keyboard: T/G forward/back, F/H strafe, R/Y turn.

Gamepad 2 is always the keyboard:

| Key | Gamepad 2 | compTeleOp |
| --- | --- | --- |
| Q | left bumper | launcher on |
| B | B | launcher off |
| Arrow Up / Down | dpad up / down | launcher speed nudge |
| W / S | left stick Y | launcher feeder (S, stick pulled back, feeds) |
| I / K | right stick Y | I intake, K outtake |

Cameras: keys 1 to 4 (orbit, follow, top, driver station). Drag to orbit, wheel to zoom.

## Headless test runs

No browser, much faster than real time (30 runs of an auto take a few seconds).

```sh
./sim test list                                      # OpModes found in the code
./sim test BlueHiveFacingAUTO --runs 30              # the checked-out working tree
./sim test BlueHiveFacingAUTO --runs 30 --code origin/limlitConfig   # another branch, read only
./sim test RedHiveFacingAUTO --runs 1 --trace 0.5    # print a state line every 0.5 s
./sim test BlueHiveFacingAUTO --teleop compTeleOp    # full match
```

`--code` takes any branch or commit, so you can compare your edits with the team's branch
without switching branches. It reads a snapshot with `git archive` into `.cache/`, so nothing
is checked out or changed in the robot repository. It needs `git` and `tar` on the PATH. `--seed N` sets the first seed (run 1
uses N + 1). Two runs with the same seed can still differ a little, so compare batches of
runs, not single runs. Every run also writes a JSON file to `reports/`.

## Shot lab

Where should the robot stand, and how fast should the launcher spin? `./sim shots` parks the
robot, fires single POLLEN at the blue back CELL and prints the hit rate for each launcher
speed. No OpMode is involved.

```sh
./sim shots                                   # distances 22 to 50 in, straight behind the CELL
./sim shots --from 30 --to 40 --step 1 --tps 1200:1300:10 --n 800
./sim shots --pose -51.6,13.2,180 --tps 1200:1280:10   # one robot pose (x, y, heading in degrees)
```

With the current knobs the best spots are 33 to 37 in from the CELL opening at 1225 to 1250
ticks/s (about 80% hits, against about 63% from the BlueHiveFacingAUTO start pose). These
numbers depend on the launch angle knob, so check them on the real robot.

## What is real and what is estimated

Real: the team's code, RoadRunner 1.0.1 (the actual library), the field and HIVE
dimensions and the scoring rules from the BIOBUZZ Competition Manual (version TU10),
the team's hardware names, motor directions and tuned feedforward numbers.

The manual is not in this repository. Get the current one from FIRST:
https://ftc-resources.firstinspires.org/ftc/game/manual. If a later team update changes the
field or the scoring, the simulator has to be updated by hand.

Estimated, and adjustable in the **Knobs** panel (every change is saved to `knobs.json`):

- Launch angle (72 degrees) and exit speed. Exit speed is calibrated so that a shot at
  1325 ticks/s from the BlueHiveFacingAUTO start pose lands in the middle of the CELL opening.
- Shot spread (4.2% speed, 2.0 degrees up/down, 2.2 degrees sideways). Chosen so that about
  40% of shots at a CELL miss, because the real robot's shots "often miss".
- Launcher spin-up time, speed lost per shot (12%), and how fast the feeder moves a ball
  (0.45 s per ball at full power, which is two balls per `shootBall`).
- Battery voltage (12.8 V), drive motor variation, and the speed the hub commands in
  RUN_USING_ENCODER mode.
- Intake reach, ball friction, robot outline (17.9 x 17.6 in).

A simulator result is a reason to look at something on the real robot, not a replacement
for testing on it.

Not modeled: NECTAR play, GARDEN scoring, other robots, the Limelight (the `limelight`
OpMode gets a camera that never sees anything), penalties.

## Coordinates

Same frame as the team's autos: origin at the field center, inches, +x toward the
audience, +y toward the blue wall, heading 0 along +x, counterclockwise positive. The
robot's front is the intake; the launcher throws out of the back.

## Layout

```
sim, sim.bat        launcher scripts (macOS and Linux, Windows)
engine/             Java engine: physics, game rules, OpMode runner, web server, headless runner
  src/main/java/sim/            the simulator
  src/main/java/com, org, androidx/   stand-ins for the FTC SDK and RoadRunner FTC classes
web/                the page (three.js)
docs/               PROTOCOL.md (engine to page messages)
reports/            JSON from headless runs (not committed)
knobs.json          your knob values (written whenever you change a knob in the page)
```

## Using it for another robot

The field, the HIVE, the POLLEN and the scoring are the same for every team. The robot is
not: these parts are written for Radical's robot and would need a version for yours.

| What | Where |
| --- | --- |
| The robot repository folder name (`26-27_BioBuzz_7196`) | `engine/src/main/java/sim/Main.java`, or pass `--clone` |
| Hardware names the code asks for (`launcher`, `intake`, `leftFeeder`, `pinpoint` and so on) | `buildHardware` in `engine/src/main/java/sim/OpRunner.java` |
| Robot size, drive, intake, feeders and launcher behavior | `engine/src/main/java/sim/World.java` |
| The numbers that were estimated for Radical's robot | `engine/src/main/java/sim/Knobs.java` |
| Robot code files that are skipped when compiling | `SKIP` list in `engine/src/main/java/sim/TeamCode.java` |
| The 3D robot model | `web/js/robot.js` |

The FTC SDK and RoadRunner FTC classes under `engine/src/main/java/com`, `org` and `androidx`
are stand-ins: they have the same names and methods as the real ones, but only the ones
Radical's code uses. Code that uses another class will show a compile error in the Driver
Station panel until a stand-in is added. Path following is RoadRunner 1.0.1 only. Pedro
Pathing is not supported yet.

## License and credits

MIT, see `LICENSE`. [three.js](https://threejs.org/) r128 (MIT) is included in `web/vendor`.
RoadRunner and FTC Dashboard are downloaded by Gradle when the engine is built; they are not
part of this repository.

This is a team-made tool. It is not made or endorsed by FIRST.
