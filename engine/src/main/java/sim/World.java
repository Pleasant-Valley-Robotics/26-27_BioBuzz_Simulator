package sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.google.gson.JsonObject;
import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver;
import com.qualcomm.robotcore.hardware.DcMotor;

import sim.hw.MotorSim;
import sim.hw.ServoSim;

/**
 * The simulated field: Radical's robot (drive, launcher, intake, feeders), the POLLEN and NECTAR,
 * the two HIVE seesaws with their CELLs, and the scoring facts. Units: inches, seconds, radians.
 * Field frame: origin at field center, +x toward the audience, +y toward the blue wall, z up.
 */
public final class World {
    /** Guards every read and write of the world from the OpMode thread and the server threads. */
    public static final Object LOCK = new Object();

    // ---------- field geometry (Competition Manual TU10, section 9) ----------
    public static final double HALF = 72.0, WALL_H = 12.0;
    public static final double PIVOT_Z = 43.95, BLUE_Y = 12.75, RED_Y = -12.75;
    public static final double FRAME_HALF_W = 24.73, FRAME_HALF_D = 19.48;
    public static final double CELL_IN = 9.42, CELL_OUT = 21.46, CELL_HALF_W = 10.0;
    public static final double CELL_FLOOR = -1.36, CELL_SIDE = 7.61, CELL_PEAK = 14.0, TILT = 30.0;
    private static final double ROOF_PEAK = CELL_FLOOR + CELL_PEAK;
    private static final double ROOF_SLOPE = (CELL_PEAK - CELL_SIDE) / CELL_HALF_W;
    private static final double ROOF_NORM = Math.sqrt(1 + ROOF_SLOPE * ROOF_SLOPE);
    public static final double[][] FLOWERS = {{-68.6, -24}, {-24, 68.6}, {24, -68.6}, {68.6, 24}};
    public static final double[] LZ_BLUE = {24.5, 47.5, 61, 72};
    public static final double[] LZ_RED = {-47.5, -24.5, -72, -61};
    /** Things on the floor the robot cannot drive through: HIVE frame base bars and the FLOWERS. */
    private static final double[][] BOXES = {
        {-FRAME_HALF_D, FRAME_HALF_D, FRAME_HALF_W - 0.75, FRAME_HALF_W + 0.75},
        {-FRAME_HALF_D, FRAME_HALF_D, -FRAME_HALF_W - 0.75, -FRAME_HALF_W + 0.75},
        {-70.6, -66.6, -26, -22}, {-26, -22, 66.6, 70.6}, {22, 26, -70.6, -66.6}, {66.6, 70.6, 22, 26}
    };

    // ---------- robot geometry (18 in cube; start pose touches the wall at 8.98 in) ----------
    public static final double HL = 8.95, HW = 8.8, ROBOT_H = 17.0;
    public static final double WHEEL_R = 2.047, WHEEL_CIRC = 2 * Math.PI * WHEEL_R;
    public static final double DRIVE_TPR = 537.7;
    public static final double WHEEL_FREE = MotorSim.FREE_TPS / DRIVE_TPR * WHEEL_CIRC;
    public static final double EXIT_X = -2.0, EXIT_Z = 15.0;
    public static final double G = 386.09;
    public static final double[] RADIUS = {1.4, 1.8, 1.8};
    public static final double NECTAR_WEIGHT = 5.0 / 3.0;

    public static final int FLOOR = 0, FLY = 1, CELL = 2, FLOWER = 3;

    public static final class Ball {
        public final int id, kind;
        public final double r;
        public double x, y, z, vx, vy, vz;
        public int state, a, b;
        public boolean out;
        public Shot shot;
        Ball(int id, int kind) { this.id = id; this.kind = kind; this.r = RADIUS[kind]; }
    }

    public static final class Shot {
        public int index;
        public double t, flyTps, flyTarget, exitSpeed;
        public String result = "pending", why = "", hive = "";
        public double x, y, h;
        transient double tx, ty, dirx, diry;
        transient boolean wrongSide;
        transient Hive target;
        transient int tipsAtFire;
    }

    public static final class Hive {
        public final String name;
        public final int index;
        public final double y;
        public double phi, target;
        public int tips, tipsAuto;
        public boolean animating;
        @SuppressWarnings("unchecked")
        public final List<Ball>[] cells = new List[] {new ArrayList<Ball>(), new ArrayList<Ball>()};
        final List<Ball> dump = new ArrayList<>();
        int dumpCell;
        double dumpTimer;
        Hive(String name, int index, double y) { this.name = name; this.index = index; this.y = y; }
        public int upCell() { return (animating ? target : phi) > 0 ? 0 : 1; }
        public double load(int cell) {
            double s = 0;
            for (Ball b : cells[cell]) s += b.kind == 0 ? 1.0 : NECTAR_WEIGHT;
            return s;
        }
    }

    public final Knobs knobs;
    public final Random rng = new Random();
    public double t = 0;

    // robot truth
    public double rx = -63.0198, ry = 13.645, rh = Math.toRadians(-179.4131);
    public double startX = rx, startY = ry, startH = rh;
    public double bvx, bvy, bw;
    public final double[] wheel = new double[4];
    public final double[] wheelAngle = new double[4];
    private final double[] wheelGain = {1, 1, 1, 1};
    /** Order: frontLeft, frontRight, backLeft, backRight. */
    public final MotorSim[] drive = {
        new MotorSim("frontLeftDrive", MotorSim.Kind.DRIVE, -1, DRIVE_TPR),
        new MotorSim("frontRightDrive", MotorSim.Kind.DRIVE, 1, DRIVE_TPR),
        new MotorSim("backLeftDrive", MotorSim.Kind.DRIVE, -1, DRIVE_TPR),
        new MotorSim("backRightDrive", MotorSim.Kind.DRIVE, 1, DRIVE_TPR)
    };
    public final MotorSim launcher = new MotorSim("launcher", MotorSim.Kind.FLYWHEEL, 1, 28);
    public final MotorSim intake = new MotorSim("intake", MotorSim.Kind.ROLLER, 1, 145.1);
    public final ServoSim leftFeeder = new ServoSim("leftFeeder", 1);
    public final ServoSim rightFeeder = new ServoSim("rightFeeder", -1);
    public final ServoSim launcherFeeder = new ServoSim("launcherFeeder", -1);
    public int held = 4;
    private double feedProgress, outtakeTimer, intakeCooldown;
    public volatile double[] est = null;
    public String alliance = "BLUE";
    /** Set by the engine: true while tips count as AUTO tips (before TELEOP starts). */
    public boolean autoPeriod = true;

    public final List<Ball> balls = new ArrayList<>();
    private int nextId = 1;
    public final Hive blue = new Hive("blue", 0, BLUE_Y);
    public final Hive red = new Hive("red", 1, RED_Y);
    public final Hive[] hives = {blue, red};

    public boolean leaveAuto, parkAuto, parkEnd;
    public double leaveClearance;
    public final List<Shot> shots = new ArrayList<>();
    public int intakeCount = 0;
    private final List<JsonObject> pendingEvents = new ArrayList<>();
    private double exitK = 0.16;
    public double distanceDriven = 0;

    public World(Knobs knobs) {
        this.knobs = knobs;
        reset(0);
    }

    // =====================================================================================
    // setup
    // =====================================================================================

    /** Reset the field. seed 0 means "use the seed knob, or a fresh random seed". */
    public void reset(long seed) {
        synchronized (LOCK) {
            long s = seed != 0 ? seed : (long) knobs.seed;
            rng.setSeed(s != 0 ? s : System.nanoTime());
            t = 0;
            balls.clear();
            shots.clear();
            pendingEvents.clear();
            nextId = 1;
            intakeCount = 0;
            distanceDriven = 0;
            for (Hive h : hives) {
                h.cells[0].clear();
                h.cells[1].clear();
                h.dump.clear();
                h.tips = 0;
                h.tipsAuto = 0;
                h.animating = false;
            }
            blue.phi = TILT;   // blue: the back CELL starts up
            red.phi = -TILT;   // red: the audience CELL starts up
            blue.target = blue.phi;
            red.target = red.phi;
            for (int i = 0; i < 3; i++) {
                addToCell(new Ball(nextId++, 2), blue, 0);
                addToCell(new Ball(nextId++, 1), red, 1);
            }
            for (int f = 0; f < FLOWERS.length; f++) {
                for (int k = 0; k < 4; k++) {
                    Ball b = new Ball(nextId++, 0);
                    b.state = FLOWER; b.a = f; b.b = k;
                    b.x = FLOWERS[f][0]; b.y = FLOWERS[f][1]; b.z = b.r + 2.8 * k;
                    balls.add(b);
                }
            }
            for (int k = 0; k < 4; k++) {
                Ball b = new Ball(nextId++, 0);
                b.x = -70.6; b.y = 70.6 - 2.8 * k; b.z = b.r;
                balls.add(b);
                Ball c = new Ball(nextId++, 0);
                c.x = 70.6; c.y = -70.6 + 2.8 * k; c.z = c.r;
                balls.add(c);
            }
            held = (int) Math.round(knobs.preload);
            feedProgress = 0;
            outtakeTimer = 0;
            intakeCooldown = 0;
            leaveAuto = false; parkAuto = false; parkEnd = false;
            autoPeriod = true;
            est = null;
            rx = startX; ry = startY; rh = startH;
            bvx = 0; bvy = 0; bw = 0;
            for (int i = 0; i < 4; i++) {
                wheel[i] = 0;
                wheelGain[i] = 1 + rng.nextGaussian() * knobs.wheelVarPct / 100.0;
                drive[i].resetPhysical();
                drive[i].stopCommands();
            }
            launcher.resetPhysical(); launcher.stopCommands();
            intake.resetPhysical(); intake.stopCommands();
            leftFeeder.power = 0; rightFeeder.power = 0; launcherFeeder.power = 0;
            calibrate();
            positionCellBalls();
        }
    }

    public void place(double x, double y, double h) {
        synchronized (LOCK) {
            startX = x; startY = y; startH = h;
            rx = x; ry = y; rh = h;
            bvx = 0; bvy = 0; bw = 0;
            for (int i = 0; i < 4; i++) wheel[i] = 0;
            collideRobot();
        }
    }

    /** Shot lab: fire one ball right now with the flywheel turning at the given speed. */
    public Shot testShot(double flyTps) {
        synchronized (LOCK) {
            launcher.tps = flyTps;
            held = Math.max(held, 1);
            fire();
            return shots.get(shots.size() - 1);
        }
    }

    /** OpMode start: motors get the SDK's default configuration. */
    public void resetDeviceConfig() {
        synchronized (LOCK) {
            for (MotorSim m : drive) m.resetConfig();
            launcher.resetConfig();
            intake.resetConfig();
            leftFeeder.resetConfig(); rightFeeder.resetConfig(); launcherFeeder.resetConfig();
        }
    }

    /** OpMode stop: the SDK zeroes every actuator. */
    public void stopAllCommands() {
        synchronized (LOCK) {
            for (MotorSim m : drive) m.stopCommands();
            launcher.stopCommands();
            intake.stopCommands();
            leftFeeder.power = 0; rightFeeder.power = 0; launcherFeeder.power = 0;
        }
    }

    public GoBildaPinpointDriver.Truth truth(sim.hw.RunContext ctx) {
        return new GoBildaPinpointDriver.Truth() {
            @Override public double[] pose() { synchronized (LOCK) { return new double[] {rx, ry, rh}; } }
            @Override public double[] bodyVel() { synchronized (LOCK) { return new double[] {bvx, bvy, bw}; } }
            @Override public double scale() { return 1.0 + knobs.odoScaleErrPct / 100.0; }
            @Override public boolean live() { return ctx.live; }
        };
    }

    /**
     * The launcher was tuned by the students so that a shot at exactly the target speed
     * (1325 ticks/s) goes in from their shooting distance. Solve for the exit speed that does
     * that with the current launch angle and drag, so the unknowns stay consistent.
     */
    public void calibrate() {
        double theta = Math.toRadians(knobs.launchAngleDeg);
        double dist = knobs.calibDistance - Math.abs(EXIT_X);
        double targetZ = PIVOT_Z + CELL_OUT * Math.sin(Math.toRadians(TILT))
                + (CELL_FLOOR + CELL_PEAK / 2) * Math.cos(Math.toRadians(TILT)) - EXIT_Z;
        double lo = 60, hi = 900;
        for (int i = 0; i < 50; i++) {
            double mid = (lo + hi) / 2;
            if (heightAt(mid, theta, dist) < targetZ) lo = mid; else hi = mid;
        }
        exitK = (lo + hi) / 2 * knobs.exitSpeedScale / 1325.0;
    }

    private double heightAt(double v, double theta, double dist) {
        double x = 0, z = 0, vx = v * Math.cos(theta), vz = v * Math.sin(theta), dt = 0.002;
        double k = knobs.dragPerInch;
        while (x < dist) {
            double sp = Math.hypot(vx, vz);
            vx -= k * sp * vx * dt;
            vz -= (G + k * sp * vz) * dt;
            x += vx * dt;
            z += vz * dt;
            if (z < -EXIT_Z || vx < 1) return -1e9;
        }
        return z;
    }

    public double nominalExitSpeed() { return exitK * 1325.0; }

    // =====================================================================================
    // stepping
    // =====================================================================================

    public void step(double dt) {
        synchronized (LOCK) {
            t += dt;
            stepDrive(dt);
            stepMechanisms(dt);
            stepBalls(dt);
            stepHives(dt);
        }
    }

    private void stepDrive(double dt) {
        double vb = knobs.batteryVolts, kS = knobs.driveKS, kV = knobs.driveKV, kA = knobs.driveKA;
        for (int i = 0; i < 4; i++) {
            MotorSim m = drive[i];
            double w = wheel[i];
            double u = m.physPower();
            double volts = 0;
            boolean coast = false;
            switch (m.mode) {
                case RUN_USING_ENCODER:
                case RUN_TO_POSITION: {
                    // The hub closes a velocity loop: power means a fraction of top speed.
                    double vt = m.velCmd ? m.physVelTarget() / m.ticksPerRev * WHEEL_CIRC
                            : u * knobs.rueMaxFraction * WHEEL_FREE;
                    if (!m.enabled) {
                        coast = true;
                    } else if (vt == 0 && !m.velCmd) {
                        coast = m.zpb == DcMotor.ZeroPowerBehavior.FLOAT;
                    } else {
                        volts = kS * Math.signum(vt) + kV * vt + 1.0 * (vt - w);
                        volts = Math.max(-vb, Math.min(vb, volts));
                    }
                    break;
                }
                case RUN_WITHOUT_ENCODER:
                    volts = u * vb;
                    coast = u == 0 && m.zpb == DcMotor.ZeroPowerBehavior.FLOAT;
                    break;
                default:
                    break;
            }
            volts *= wheelGain[i];
            if (coast) {
                double dec = kS / kA * dt;
                w = Math.abs(w) <= dec ? 0 : w - Math.signum(w) * dec;
            } else if (Math.abs(w) <= 0.02 && Math.abs(volts) <= kS) {
                w = 0;
            } else {
                double s = Math.abs(w) > 0.02 ? Math.signum(w) : Math.signum(volts);
                double w2 = w + (volts - kS * s - kV * w) / kA * dt;
                if (w != 0 && Math.signum(w2) != Math.signum(w) && Math.abs(volts) <= kS) w2 = 0;
                w = w2;
            }
            wheel[i] = w;
            wheelAngle[i] += w / WHEEL_R * dt;
            m.tps = w / WHEEL_CIRC * m.ticksPerRev;
            m.integrate(dt);
        }
        double lf = wheel[0], rf = wheel[1], lb = wheel[2], rb = wheel[3];
        double vx = (lf + lb + rb + rf) * 0.25;
        double vy = (-lf + lb - rb + rf) * 0.25 / knobs.lateralMult;
        double om = (-lf - lb + rb + rf) * 0.25 / knobs.trackWidth;
        double ox = rx, oy = ry, oh = rh;
        double hm = rh + om * dt * 0.5;
        rx += (vx * Math.cos(hm) - vy * Math.sin(hm)) * dt;
        ry += (vx * Math.sin(hm) + vy * Math.cos(hm)) * dt;
        rh += om * dt;
        collideRobot();
        double dxw = (rx - ox) / dt, dyw = (ry - oy) / dt;
        double c = Math.cos(hm), s = Math.sin(hm);
        bvx = dxw * c + dyw * s;
        bvy = -dxw * s + dyw * c;
        bw = (rh - oh) / dt;
        distanceDriven += Math.hypot(rx - ox, ry - oy);
    }

    private void collideRobot() {
        double c = Math.cos(rh), s = Math.sin(rh);
        double ex = Math.abs(c) * HL + Math.abs(s) * HW, ey = Math.abs(s) * HL + Math.abs(c) * HW;
        if (rx + ex > HALF) rx = HALF - ex;
        if (rx - ex < -HALF) rx = -HALF + ex;
        if (ry + ey > HALF) ry = HALF - ey;
        if (ry - ey < -HALF) ry = -HALF + ey;
        for (double[] b : BOXES) {
            double bcx = (b[0] + b[1]) / 2, bcy = (b[2] + b[3]) / 2, bhx = (b[1] - b[0]) / 2, bhy = (b[3] - b[2]) / 2;
            double dx = rx - bcx, dy = ry - bcy;
            double ovx = ex + bhx - Math.abs(dx);
            if (ovx <= 0) continue;
            double ovy = ey + bhy - Math.abs(dy);
            if (ovy <= 0) continue;
            double pf = dx * c + dy * s;
            double ovf = HL + Math.abs(c) * bhx + Math.abs(s) * bhy - Math.abs(pf);
            if (ovf <= 0) continue;
            double pl = -dx * s + dy * c;
            double ovl = HW + Math.abs(s) * bhx + Math.abs(c) * bhy - Math.abs(pl);
            if (ovl <= 0) continue;
            double min = Math.min(Math.min(ovx, ovy), Math.min(ovf, ovl));
            if (min == ovx) rx += Math.signum(dx) * ovx;
            else if (min == ovy) ry += Math.signum(dy) * ovy;
            else if (min == ovf) { rx += c * Math.signum(pf) * ovf; ry += s * Math.signum(pf) * ovf; }
            else { rx += -s * Math.signum(pl) * ovl; ry += c * Math.signum(pl) * ovl; }
        }
    }

    private void stepMechanisms(double dt) {
        // launcher flywheel
        MotorSim m = launcher;
        double top = knobs.flyMaxTps * knobs.batteryVolts / 12.5;
        double u = 0;
        if (m.enabled) {
            if (m.mode == DcMotor.RunMode.RUN_USING_ENCODER || m.mode == DcMotor.RunMode.RUN_TO_POSITION) {
                if (m.velCmd) u = m.velocityLoop(m.physVelTarget());
                else if (m.power != 0) u = m.velocityLoop(m.physPower() * MotorSim.FREE_TPS * 0.85);
                else { u = 0; m.newSample = false; }
            } else if (m.mode == DcMotor.RunMode.RUN_WITHOUT_ENCODER) {
                u = m.physPower();
            }
        }
        m.tps += (top * u - m.tps) / knobs.flyTau * dt;
        m.integrate(dt);

        intake.tps = intake.physPower() * MotorSim.FREE_TPS;
        intake.integrate(dt);
        leftFeeder.angle += leftFeeder.physPower() * 14 * dt;
        rightFeeder.angle += rightFeeder.physPower() * 14 * dt;
        launcherFeeder.angle += launcherFeeder.physPower() * 14 * dt;

        // launcher feeder pushes the next ball into the flywheel
        double fp = launcherFeeder.physPower();
        if (held <= 0) {
            feedProgress = 0;
        } else if (fp > 0.02) {
            feedProgress += fp * dt / knobs.feedSecPerBall;
            if (feedProgress >= 1) {
                feedProgress -= 1;
                fire();
            }
        } else if (fp < -0.02) {
            feedProgress = Math.max(0, feedProgress + fp * dt / knobs.feedSecPerBall);
        }

        // intake: balls on the floor at the front mouth
        double ip = intake.physPower();
        intakeCooldown -= dt;
        double c = Math.cos(rh), s = Math.sin(rh);
        if (ip > 0.3 && held < 4 && intakeCooldown <= 0) {
            // The mouth is the front face of the robot. The roller alone takes balls near the middle;
            // with the two corner feeder wheels turning, anything in front of the frame is swept in.
            // A ball beside the robot (outside the frame width) is not in the mouth.
            boolean feeders = leftFeeder.physPower() > 0.3 || rightFeeder.physPower() > 0.3;
            double halfMouth = feeders ? HW - 0.4 : 5.5;
            double reachBack = 2.0;
            for (int i = 0; i < balls.size(); i++) {
                Ball b = balls.get(i);
                if (b.kind != 0 || b.out) continue;
                if (!(b.state == FLOOR || (b.state == FLOWER && b.b == 0))) continue;
                double dx = b.x - rx, dy = b.y - ry;
                double lx = dx * c + dy * s, ly = -dx * s + dy * c;
                if (lx > HL - reachBack && lx < HL + 2.4 && Math.abs(ly) < halfMouth) {
                    if (b.state == FLOWER) {
                        for (Ball o : balls) {
                            if (o.state == FLOWER && o.a == b.a && o != b) { o.b--; o.z = o.r + 2.8 * o.b; }
                        }
                    }
                    balls.remove(i);
                    held++;
                    intakeCount++;
                    intakeCooldown = 0.15;
                    event("intake", "POLLEN collected, holding " + held);
                    break;
                }
            }
        }
        // outtake
        if (ip < -0.3 && held > 0) {
            outtakeTimer += dt;
            if (outtakeTimer >= 0.35) {
                outtakeTimer = 0;
                held--;
                Ball b = new Ball(nextId++, 0);
                double lx = HL + 2.2;
                b.x = rx + lx * c; b.y = ry + lx * s; b.z = b.r;
                double wvx = bvx * c - bvy * s, wvy = bvx * s + bvy * c;
                b.vx = wvx + 35 * c; b.vy = wvy + 35 * s;
                balls.add(b);
            }
        } else {
            outtakeTimer = 0;
        }
    }

    private void fire() {
        held--;
        double c = Math.cos(rh), s = Math.sin(rh);
        double flyTps = launcher.tps;
        double speed = exitK * Math.max(0, flyTps) * (1 + rng.nextGaussian() * knobs.shotSpeedSigmaPct / 100.0);
        double pitch = Math.toRadians(knobs.launchAngleDeg + rng.nextGaussian() * knobs.shotPitchSigmaDeg);
        double yaw = rh + Math.PI + Math.toRadians(rng.nextGaussian() * knobs.shotYawSigmaDeg);
        launcher.tps *= 1 - knobs.shotDropPct / 100.0;

        Ball b = new Ball(nextId++, 0);
        b.state = FLY;
        b.x = rx + EXIT_X * c; b.y = ry + EXIT_X * s; b.z = EXIT_Z;
        double wvx = bvx * c - bvy * s, wvy = bvx * s + bvy * c;
        double horiz = Math.cos(pitch) * speed;
        b.vx = horiz * Math.cos(yaw) + wvx;
        b.vy = horiz * Math.sin(yaw) + wvy;
        b.vz = Math.sin(pitch) * speed;

        Shot sh = new Shot();
        sh.index = shots.size() + 1;
        sh.t = t;
        sh.flyTps = flyTps;
        sh.flyTarget = launcher.velCmd ? launcher.physVelTarget() : 0;
        sh.exitSpeed = speed;
        sh.x = rx; sh.y = ry; sh.h = rh;
        Hive target = alliance.equals("RED") ? red : blue;
        int up = target.upCell();
        double phi = Math.toRadians(target.animating ? target.target : target.phi);
        double arm = up == 0 ? 1 : -1;
        sh.tx = arm * -Math.cos(phi) * CELL_OUT + Math.sin(phi) * (CELL_FLOOR + CELL_PEAK / 2);
        sh.ty = target.y;
        sh.dirx = -c; sh.diry = -s;
        double outx = up == 0 ? -1 : 1;
        sh.wrongSide = sh.dirx * outx > -0.2;
        sh.hive = target.name;
        sh.target = target;
        sh.tipsAtFire = target.tips;
        b.shot = sh;
        shots.add(sh);
        balls.add(b);
        JsonObject e = event("shot", String.format("Shot %d: flywheel %.0f ticks/s, exit %.0f in/s", sh.index, flyTps, speed));
        e.addProperty("flyVel", Math.round(flyTps));
        e.addProperty("exitSpeed", Math.round(speed));
    }

    private void stepBalls(double dt) {
        double c = Math.cos(rh), s = Math.sin(rh);
        for (int i = 0; i < balls.size(); i++) {
            Ball b = balls.get(i);
            if (b.state == FLY) {
                flyStep(b, dt);
            } else if (b.state == FLOOR) {
                double sp = Math.hypot(b.vx, b.vy);
                if (sp > 0) {
                    double ns = Math.max(0, sp - knobs.ballFriction * (b.out ? 4 : 1) * dt);
                    b.vx *= ns / sp; b.vy *= ns / sp;
                    b.x += b.vx * dt; b.y += b.vy * dt;
                }
                if (b.out) continue;
                // robot pushes balls it drives into
                double dx = b.x - rx, dy = b.y - ry;
                double lx = dx * c + dy * s, ly = -dx * s + dy * c;
                double px = HL + b.r - Math.abs(lx), py = HW + b.r - Math.abs(ly);
                if (px > 0 && py > 0) {
                    double ox = b.x, oy = b.y;
                    double nx, ny;
                    if (px < py) { nx = Math.signum(lx); ny = 0; lx = nx * (HL + b.r); }
                    else { nx = 0; ny = Math.signum(ly); ly = ny * (HW + b.r); }
                    double pvx = bvx - bw * ly, pvy = bvy + bw * lx;
                    double bvlx = b.vx * c + b.vy * s, bvly = -b.vx * s + b.vy * c;
                    double vn = bvlx * nx + bvly * ny, rn = pvx * nx + pvy * ny;
                    if (vn < rn + 1.0) {
                        double add = rn + 1.0 - vn;
                        bvlx += add * nx; bvly += add * ny;
                    }
                    b.x = rx + lx * c - ly * s; b.y = ry + lx * s + ly * c;
                    b.vx = bvlx * c - bvly * s; b.vy = bvlx * s + bvly * c;
                    // A ball pinned between the robot and a perimeter wall is gripped, it does not
                    // squirt along the wall: keep its position along the wall and stop it.
                    // (Only when the robot is pushing it into the wall, not along it.)
                    double lim = HALF - b.r;
                    double wnx = nx * c - ny * s, wny = nx * s + ny * c;
                    if (Math.abs(b.x) > lim && Math.signum(b.x) * wnx > 0.7) { b.y = oy; b.vx = 0; b.vy = 0; }
                    if (Math.abs(b.y) > lim && Math.signum(b.y) * wny > 0.7) { b.x = ox; b.vx = 0; b.vy = 0; }
                }
                floorBounds(b);
            }
        }
        // balls do not overlap on the floor
        for (int i = 0; i < balls.size(); i++) {
            Ball a = balls.get(i);
            if (a.state != FLOOR || a.out) continue;
            for (int j = i + 1; j < balls.size(); j++) {
                Ball b = balls.get(j);
                if (b.state != FLOOR || b.out) continue;
                double dx = b.x - a.x, dy = b.y - a.y, min = a.r + b.r;
                double d2 = dx * dx + dy * dy;
                if (d2 >= min * min || d2 < 1e-9) continue;
                double d = Math.sqrt(d2), push = (min - d) / 2, nx = dx / d, ny = dy / d;
                a.x -= nx * push; a.y -= ny * push; b.x += nx * push; b.y += ny * push;
                double rel = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny;
                if (rel < 0) {
                    a.vx += rel * nx * 0.5; a.vy += rel * ny * 0.5;
                    b.vx -= rel * nx * 0.5; b.vy -= rel * ny * 0.5;
                }
            }
        }
    }

    private void floorBounds(Ball b) {
        double lim = HALF - b.r;
        if (b.x > lim) { b.x = lim; if (b.vx > 0) b.vx = -b.vx * 0.4; }
        if (b.x < -lim) { b.x = -lim; if (b.vx < 0) b.vx = -b.vx * 0.4; }
        if (b.y > lim) { b.y = lim; if (b.vy > 0) b.vy = -b.vy * 0.4; }
        if (b.y < -lim) { b.y = -lim; if (b.vy < 0) b.vy = -b.vy * 0.4; }
        for (int k = 0; k < 2; k++) {
            double[] box = BOXES[k];
            double qx = Math.max(box[0], Math.min(box[1], b.x)), qy = Math.max(box[2], Math.min(box[3], b.y));
            double dx = b.x - qx, dy = b.y - qy, d2 = dx * dx + dy * dy;
            if (d2 >= b.r * b.r) continue;
            double nx, ny, d = Math.sqrt(d2);
            if (d < 1e-6) { nx = 0; ny = b.y >= (box[2] + box[3]) / 2 ? 1 : -1; d = 0; }
            else { nx = dx / d; ny = dy / d; }
            b.x = qx + nx * b.r; b.y = qy + ny * b.r;
            double vn = b.vx * nx + b.vy * ny;
            if (vn < 0) { b.vx -= 1.4 * vn * nx; b.vy -= 1.4 * vn * ny; }
        }
    }

    private void flyStep(Ball b, double dt) {
        double sp = Math.sqrt(b.vx * b.vx + b.vy * b.vy + b.vz * b.vz);
        double k = knobs.dragPerInch * sp * dt;
        b.vx -= k * b.vx; b.vy -= k * b.vy; b.vz -= k * b.vz + G * dt;
        b.x += b.vx * dt; b.y += b.vy * dt; b.z += b.vz * dt;

        // perimeter wall: 12 in high; a ball that clears it leaves the field
        if (!b.out) {
            double lim = HALF - b.r;
            if (Math.abs(b.x) > lim || Math.abs(b.y) > lim) {
                if (b.z < WALL_H + b.r) {
                    if (b.x > lim) { b.x = lim; b.vx = -Math.abs(b.vx) * 0.45; }
                    if (b.x < -lim) { b.x = -lim; b.vx = Math.abs(b.vx) * 0.45; }
                    if (b.y > lim) { b.y = lim; b.vy = -Math.abs(b.vy) * 0.45; }
                    if (b.y < -lim) { b.y = -lim; b.vy = Math.abs(b.vy) * 0.45; }
                    firstImpact(b, "wall");
                } else if (Math.abs(b.x) > HALF + b.r || Math.abs(b.y) > HALF + b.r) {
                    b.out = true;
                    firstImpact(b, "out");
                }
            }
        }
        if (!b.out) {
            for (Hive h : hives) {
                if (Math.abs(b.y - h.y) > CELL_HALF_W + b.r + 1) continue;
                if (cellContact(b, h)) break;
            }
        }
        if (b.state != FLY) return;
        if (b.z <= b.r) {
            b.z = b.r;
            firstImpact(b, "floor");
            if (b.vz < -45) {
                b.vz = -b.vz * 0.42; b.vx *= 0.75; b.vy *= 0.75;
            } else {
                b.vz = 0;
                b.state = FLOOR;
            }
        }
    }

    /** Ball against the two CELLs of one HIVE. Returns true if the ball was captured. */
    private boolean cellContact(Ball b, Hive h) {
        double phi = Math.toRadians(h.phi);
        double ax = -Math.cos(phi), az = Math.sin(phi);
        double nx = Math.sin(phi), nz = Math.cos(phi);
        double relx = b.x, relz = b.z - PIVOT_Z;
        double sAlong = relx * ax + relz * az;
        double w = relx * nx + relz * nz;
        double v = b.y - h.y;
        for (int cell = 0; cell < 2; cell++) {
            double dir = cell == 0 ? 1 : -1;
            double u = sAlong * dir;
            double dFloor = CELL_FLOOR - w;
            double dSide = Math.abs(v) - CELL_HALF_W;
            double dRoof = (w - (ROOF_PEAK - ROOF_SLOPE * Math.abs(v))) / ROOF_NORM;
            double dIn = CELL_IN - u;
            double dOut = u - CELL_OUT;
            double pent = Math.max(dFloor, Math.max(dSide, dRoof));
            double d = Math.max(pent, Math.max(dIn, dOut));
            if (d >= b.r) continue;
            boolean up = !h.animating && h.dump.isEmpty() && (cell == 0 ? h.phi > 0 : h.phi < 0);
            double fx, fy, fz;
            String face;
            if (dOut >= pent && dOut >= dIn) {
                if (up && pent <= -knobs.rimMargin) {
                    if (dOut <= 0) {
                        capture(b, h, cell);
                        return true;
                    }
                    continue;   // on its way in through the opening
                }
                fx = ax * dir; fy = 0; fz = az * dir; face = "rim";
            } else if (dIn >= pent) {
                fx = -ax * dir; fy = 0; fz = -az * dir; face = "back";
            } else if (dFloor >= dSide && dFloor >= dRoof) {
                fx = -nx; fy = 0; fz = -nz; face = "underside";
            } else if (dSide >= dRoof) {
                fx = 0; fy = Math.signum(v); fz = 0; face = "side";
            } else {
                fx = nx / ROOF_NORM; fy = Math.signum(v) * ROOF_SLOPE / ROOF_NORM; fz = nz / ROOF_NORM; face = "roof";
            }
            double push = b.r - d;
            b.x += fx * push; b.y += fy * push; b.z += fz * push;
            double vn = b.vx * fx + b.vy * fy + b.vz * fz;
            if (vn < 0) {
                b.vx -= 1.35 * vn * fx; b.vy -= 1.35 * vn * fy; b.vz -= 1.35 * vn * fz;
                b.vx *= 0.92; b.vy *= 0.92; b.vz *= 0.92;
            }
            firstImpact(b, face);
            return false;
        }
        return false;
    }

    private void addToCell(Ball b, Hive h, int cell) {
        b.state = CELL; b.a = h.index; b.b = cell;
        b.vx = 0; b.vy = 0; b.vz = 0;
        h.cells[cell].add(b);
        if (!balls.contains(b)) balls.add(b);
    }

    private void capture(Ball b, Hive h, int cell) {
        addToCell(b, h, cell);
        Shot sh = b.shot;
        if (sh != null) {
            sh.result = "score";
            sh.why = "";
            sh.hive = h.name;
        }
        JsonObject e = event("score", String.format("POLLEN in the %s HIVE (load %.1f of %.1f)",
                h.name.toUpperCase(), h.load(cell), knobs.tipThreshold));
        e.addProperty("hive", h.name);
    }

    private void firstImpact(Ball b, String surface) {
        Shot sh = b.shot;
        if (sh == null || !sh.result.equals("pending")) return;
        String why;
        double dx = b.x - sh.tx, dy = b.y - sh.ty;
        double along = dx * sh.dirx + dy * sh.diry, lat = -dx * sh.diry + dy * sh.dirx;
        boolean tippedInFlight = sh.target != null && (sh.target.animating || sh.target.tips != sh.tipsAtFire);
        if (sh.wrongSide) why = "wrongside";
        else if (tippedInFlight) why = "tipped";
        else if (surface.equals("underside")) why = "underside";
        else if (surface.equals("side")) why = "wide";
        else if (surface.equals("roof") || surface.equals("back")) why = "long";
        else if (surface.equals("rim")) why = along < 0 ? "short" : "rim";
        else if (Math.abs(lat) > CELL_HALF_W + 1) why = "wide";
        else why = along < 0 ? "short" : "long";
        sh.result = "miss";
        sh.why = why;
        String where;
        switch (surface) {
            case "floor": where = String.format("landed on the floor %.0f in %s the opening", Math.abs(along), along < 0 ? "short of" : "beyond"); break;
            case "wall": where = "hit the perimeter wall"; break;
            case "out": where = "left the field"; break;
            default: where = "hit the CELL " + surface;
        }
        String note;
        switch (why) {
            case "wrongside": note = "the upward CELL opens away from the robot"; break;
            case "tipped": note = "the HIVE tipped while the ball was in the air"; break;
            default: note = why;
        }
        JsonObject e = event("miss", String.format("Shot %d missed: %s (%s)", sh.index, note, where));
        e.addProperty("why", why);
    }

    private void stepHives(double dt) {
        for (Hive h : hives) {
            if (h.animating) {
                double stepDeg = 80 * dt;
                if (Math.abs(h.target - h.phi) <= stepDeg) {
                    h.phi = h.target;
                    h.animating = false;
                    int down = h.phi > 0 ? 1 : 0;
                    h.dump.addAll(h.cells[down]);
                    h.cells[down].clear();
                    h.dumpCell = down;
                    h.dumpTimer = 0.05;
                } else {
                    h.phi += Math.signum(h.target - h.phi) * stepDeg;
                }
            } else if (h.dump.isEmpty()) {
                int up = h.phi > 0 ? 0 : 1;
                if (h.load(up) >= knobs.tipThreshold) {
                    h.target = -h.phi;
                    h.animating = true;
                    h.tips++;
                    if (autoPeriod) h.tipsAuto++;
                    JsonObject e = event("tip", h.name.toUpperCase() + " HIVE TIP, +20");
                    e.addProperty("hive", h.name);
                    e.addProperty("points", 20);
                    e.addProperty("period", autoPeriod ? "AUTO" : "TELEOP");
                }
            }
            if (!h.dump.isEmpty()) {
                h.dumpTimer -= dt;
                if (h.dumpTimer <= 0) {
                    h.dumpTimer = 0.07;
                    Ball b = h.dump.remove(0);
                    double phi = Math.toRadians(h.phi);
                    double dir = h.dumpCell == 0 ? 1 : -1;
                    double ax = -Math.cos(phi) * dir, az = Math.sin(phi) * dir;
                    double nx = Math.sin(phi), nz = Math.cos(phi);
                    double u = CELL_OUT + b.r + 0.6, w = CELL_FLOOR + b.r + 0.4;
                    double v = (rng.nextDouble() * 2 - 1) * 7;
                    b.x = ax * u + nx * w; b.y = h.y + v; b.z = PIVOT_Z + az * u + nz * w;
                    b.vx = ax * 28 + rng.nextGaussian() * 3; b.vy = rng.nextGaussian() * 6; b.vz = az * 28;
                    b.state = FLY;
                    b.shot = null;
                }
            }
        }
        positionCellBalls();
    }

    private static final double[] ROW = {0, 3.7, -3.7, 7.4, -7.4};

    private void positionCellBalls() {
        for (Hive h : hives) {
            double phi = Math.toRadians(h.phi);
            double nx = Math.sin(phi), nz = Math.cos(phi);
            for (int cell = 0; cell < 2; cell++) {
                double dir = cell == 0 ? 1 : -1;
                double ax = -Math.cos(phi) * dir, az = Math.sin(phi) * dir;
                List<Ball> list = (!h.dump.isEmpty() && h.dumpCell == cell) ? h.dump : h.cells[cell];
                for (int k = 0; k < list.size(); k++) {
                    Ball b = list.get(k);
                    double u = CELL_IN + 2.0 + 3.7 * ((k / 5) % 3);
                    double v = ROW[k % 5];
                    double w = CELL_FLOOR + b.r + 3.3 * (k / 15);
                    b.x = ax * u + nx * w; b.y = h.y + v; b.z = PIVOT_Z + az * u + nz * w;
                }
            }
        }
    }

    // =====================================================================================
    // scoring facts
    // =====================================================================================

    /** Gap in inches between the robot's outline and the nearest perimeter wall (0 = touching). */
    public double wallClearance() {
        double c = Math.cos(rh), s = Math.sin(rh);
        double ex = Math.abs(c) * HL + Math.abs(s) * HW, ey = Math.abs(s) * HL + Math.abs(c) * HW;
        return Math.max(0, Math.min(HALF - Math.abs(rx) - ex, HALF - Math.abs(ry) - ey));
    }

    public boolean touchingWall() {
        return wallClearance() < 0.1;
    }

    /** True if any part of the robot is over the given alliance's LOADING ZONE. */
    public boolean inLoadingZone(String who) {
        double[] z = who.equals("RED") ? LZ_RED : LZ_BLUE;
        double c = Math.cos(rh), s = Math.sin(rh);
        double ex = Math.abs(c) * HL + Math.abs(s) * HW, ey = Math.abs(s) * HL + Math.abs(c) * HW;
        double bcx = (z[0] + z[1]) / 2, bcy = (z[2] + z[3]) / 2, bhx = (z[1] - z[0]) / 2, bhy = (z[3] - z[2]) / 2;
        double dx = rx - bcx, dy = ry - bcy;
        if (Math.abs(dx) >= ex + bhx || Math.abs(dy) >= ey + bhy) return false;
        if (Math.abs(dx * c + dy * s) >= HL + Math.abs(c) * bhx + Math.abs(s) * bhy) return false;
        return Math.abs(-dx * s + dy * c) < HW + Math.abs(s) * bhx + Math.abs(c) * bhy;
    }

    public boolean quiet() {
        synchronized (LOCK) {
            if (Math.abs(bvx) + Math.abs(bvy) > 0.5 || Math.abs(bw) > 0.05) return false;
            for (Hive h : hives) if (h.animating || !h.dump.isEmpty()) return false;
            for (Ball b : balls) {
                if (b.state == FLY) return false;
                if (b.state == FLOOR && Math.hypot(b.vx, b.vy) > 2) return false;
            }
            return true;
        }
    }

    public JsonObject score(String who) {
        Hive h = who.equals("RED") ? red : blue;
        boolean mine = alliance.equals(who);
        int leave = mine && leaveAuto ? 3 : 0;
        int park = mine ? (parkAuto ? 5 : 0) + (parkEnd ? 5 : 0) : 0;
        int cell = 2 * h.cells[h.upCell()].size();
        JsonObject o = new JsonObject();
        o.addProperty("total", leave + park + 20 * h.tips + cell);
        o.addProperty("leave", leave);
        o.addProperty("park", park);
        o.addProperty("tips", h.tips);
        o.addProperty("tipPts", 20 * h.tips);
        o.addProperty("cell", cell);
        return o;
    }

    // =====================================================================================
    // events and snapshots
    // =====================================================================================

    public JsonObject event(String kind, String msg) {
        JsonObject e = new JsonObject();
        e.addProperty("type", "event");
        e.addProperty("t", Math.round(t * 100) / 100.0);
        e.addProperty("kind", kind);
        e.addProperty("msg", msg);
        synchronized (LOCK) { pendingEvents.add(e); }
        return e;
    }

    public List<JsonObject> drainEvents() {
        synchronized (LOCK) {
            List<JsonObject> out = new ArrayList<>(pendingEvents);
            pendingEvents.clear();
            return out;
        }
    }

    private static void num(StringBuilder sb, double v, int decimals) {
        if (Double.isNaN(v) || Double.isInfinite(v)) { sb.append('0'); return; }
        double p = decimals == 1 ? 10 : decimals == 2 ? 100 : decimals == 3 ? 1000 : 1;
        double r = Math.round(v * p) / p;
        if (r == Math.rint(r) && Math.abs(r) < 1e9) sb.append((long) r); else sb.append(r);
    }

    public String stateJson(String phase, double clock, String opName, String opStatus, double speed) {
        synchronized (LOCK) {
            StringBuilder sb = new StringBuilder(4096);
            sb.append("{\"type\":\"state\",\"t\":"); num(sb, t, 3);
            sb.append(",\"speed\":"); num(sb, speed, 2);
            sb.append(",\"robot\":{\"x\":"); num(sb, rx, 2);
            sb.append(",\"y\":"); num(sb, ry, 2);
            sb.append(",\"h\":"); num(sb, rh, 3);
            double[] e = est;
            if (e == null) sb.append(",\"est\":null");
            else { sb.append(",\"est\":["); num(sb, e[0], 2); sb.append(','); num(sb, e[1], 2); sb.append(','); num(sb, e[2], 3); sb.append(']'); }
            sb.append(",\"wheels\":[");
            for (int i = 0; i < 4; i++) { if (i > 0) sb.append(','); num(sb, wheelAngle[i], 2); }
            sb.append("],\"intake\":"); num(sb, intake.pos / intake.ticksPerRev * 2 * Math.PI, 2);
            sb.append(",\"feedL\":"); num(sb, leftFeeder.angle, 2);
            sb.append(",\"feedR\":"); num(sb, rightFeeder.angle, 2);
            sb.append(",\"feedLaunch\":"); num(sb, launcherFeeder.angle, 2);
            sb.append(",\"flywheel\":"); num(sb, launcher.pos / launcher.ticksPerRev * 2 * Math.PI, 2);
            sb.append(",\"flyVel\":"); num(sb, launcher.tps, 1);
            sb.append(",\"flyTarget\":"); num(sb, launcher.velCmd ? launcher.physVelTarget() : 0, 1);
            sb.append(",\"held\":").append(held);
            sb.append("},\"balls\":[");
            boolean first = true;
            for (Ball b : balls) {
                if (!first) sb.append(',');
                first = false;
                sb.append('[').append(b.id).append(',').append(b.kind).append(',');
                num(sb, b.x, 2); sb.append(','); num(sb, b.y, 2); sb.append(','); num(sb, b.z, 2); sb.append(']');
            }
            sb.append("],\"hives\":{");
            for (int i = 0; i < 2; i++) {
                Hive h = hives[i];
                int up = h.upCell(), pollen = 0, nectar = 0;
                for (Ball b : h.cells[up]) { if (b.kind == 0) pollen++; else nectar++; }
                if (i > 0) sb.append(',');
                sb.append('"').append(h.name).append("\":{\"phi\":"); num(sb, h.phi, 2);
                sb.append(",\"pollen\":").append(pollen).append(",\"nectar\":").append(nectar);
                sb.append(",\"tips\":").append(h.tips).append('}');
            }
            sb.append("},\"match\":{\"phase\":\"").append(phase).append("\",\"clock\":"); num(sb, Math.max(0, clock), 1);
            sb.append(",\"alliance\":\"").append(alliance).append("\",\"blue\":").append(score("BLUE"));
            sb.append(",\"red\":").append(score("RED"));
            sb.append("},\"op\":{\"name\":").append(opName == null ? "null" : "\"" + opName.replace("\"", "'") + "\"");
            sb.append(",\"status\":\"").append(opStatus).append("\"}}");
            return sb.toString();
        }
    }
}
