package sim;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.acmerobotics.dashboard.canvas.Canvas;
import com.acmerobotics.dashboard.canvas.CanvasOp;
import com.acmerobotics.dashboard.telemetry.TelemetryPacket;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;

/**
 * Ties everything together: the physics clock, the Driver Station buttons, the match periods
 * (AUTO 0:30, transition 0:08, TELEOP 2:00), scoring moments, and what gets sent to the page.
 */
public final class Engine implements SimHooks.Listener {
    public final Path simDir, cacheDir, knobsFile;
    public final Knobs knobs;
    public final World world;
    public final OpRunner runner;
    public volatile TeamCode team;
    public volatile Server server;

    // match state (touched only on the physics thread)
    public String phase = "IDLE";
    public double clock = 0;
    private boolean fullMatch = false, over = false, userStopped = false, cutOff = false;
    private String teleopName = null;
    private double overAt = 0, startWorldT = 0, startClockT = 0;
    public volatile String selected = null;
    public volatile double speed = 1.0;
    /** Headless debugging: print the robot state every this many simulated seconds (0 = off). */
    public double trace = 0;
    private double nextTrace = 0;

    private final ConcurrentLinkedQueue<Runnable> commands = new ConcurrentLinkedQueue<>();
    private final float[][] padAxes = new float[2][6];
    @SuppressWarnings("unchecked")
    private final Set<String>[] padButtons = new Set[] {new HashSet<String>(), new HashSet<String>()};

    private volatile List<String> dsLines = List.of();
    private final Map<String, String> dash = new TreeMap<>();
    private volatile long telemVersion = 0;
    private long telemSent = -1;
    public volatile String previewMsg = null;
    private volatile int previewCount = 0;
    private long lastLiveNs = 0;
    public volatile double[] lastTarget = null;
    public volatile JsonObject lastReport = null;
    public final List<JsonObject> eventLog = new ArrayList<>();
    private OpRunner.Status lastStatus = OpRunner.Status.NONE;
    private Throwable reportedError = null;
    private String runOpName = "";
    private String autoOpName = "";
    private double opEndedRel = -1;
    private String endNote = "";

    public Engine(Path simDir) {
        this.simDir = simDir;
        this.cacheDir = simDir.resolve(".cache");
        this.knobsFile = simDir.resolve("knobs.json");
        this.knobs = Knobs.load(knobsFile);
        SimClock.loopPeriod = knobs.loopMs / 1000.0;
        this.world = new World(knobs);
        this.runner = new OpRunner(world, knobs, lines -> {
            dsLines = List.copyOf(lines);
            telemVersion++;
        });
        SimHooks.listener = this;
    }

    public void post(Runnable r) { commands.add(r); }

    // =====================================================================================
    // loops
    // =====================================================================================

    /** Serve mode: real time (scaled by the speed setting), forever. */
    public void loopForever() throws InterruptedException {
        long wallRef = System.nanoTime(), lastState = 0;
        double simRef = SimClock.now();
        boolean freeRun = false;
        while (true) {
            drain();
            double sp = speed;
            if (sp <= 0) {
                Thread.sleep(10);
                wallRef = System.nanoTime();
                simRef = SimClock.now();
            } else {
                boolean parked = SimClock.awaitOpParked(freeRun ? 0 : 300);
                freeRun = !parked;
                tick();
                long target = wallRef + (long) ((SimClock.now() - simRef) / sp * 1e9);
                long ahead = target - System.nanoTime();
                if (ahead > 200_000L) {
                    Thread.sleep(ahead / 1_000_000L, (int) (ahead % 1_000_000L));
                } else if (ahead < -200_000_000L) {
                    wallRef = System.nanoTime();
                    simRef = SimClock.now();
                }
            }
            long now = System.nanoTime();
            if (now - lastState >= 33_000_000L) {
                lastState = now;
                broadcastState();
            }
        }
    }

    /** Headless: run one OpMode as fast as the computer allows. Returns the report. */
    public JsonObject runHeadless(String opName, long seed, String teleop) throws InterruptedException {
        selected = opName;
        lastReport = null;
        eventLog.clear();
        doInit(seed);
        if (teleop != null) { fullMatch = true; teleopName = teleop; }
        if (runner.status == OpRunner.Status.ERROR) return buildReport("AUTO");
        // a short INIT phase, like a real match, then START
        for (int i = 0; i < 100; i++) stepHeadless();
        doStart();
        double limit = SimClock.now() + (teleop != null ? 175 : 40);
        while (lastReport == null && SimClock.now() < limit) stepHeadless();
        if (lastReport == null) {
            runner.stop();
            finish("AUTO");
        }
        runner.stop();
        return lastReport;
    }

    private void stepHeadless() throws InterruptedException {
        drain();
        if (!SimClock.awaitOpParked(3000)) Thread.sleep(5);
        tick();
    }

    private void drain() {
        Runnable r;
        while ((r = commands.poll()) != null) {
            try {
                r.run();
            } catch (Throwable t) {
                t.printStackTrace();
                world.event("error", "Simulator command failed: " + t);
            }
        }
    }

    private void tick() {
        OpMode o = runner.op;
        if (o != null && runner.active()) {
            o.gamepad1.simUpdate(padAxes[0][0], padAxes[0][1], padAxes[0][2], padAxes[0][3], padAxes[0][4], padAxes[0][5], padButtons[0]);
            o.gamepad2.simUpdate(padAxes[1][0], padAxes[1][1], padAxes[1][2], padAxes[1][3], padAxes[1][4], padAxes[1][5], padButtons[1]);
        }
        world.step(SimClock.TICK);
        SimClock.advance(SimClock.TICK);
        matchTick(SimClock.TICK);
        for (JsonObject e : world.drainEvents()) {
            if (trace > 0) System.out.printf("   [%6.2f] %s: %s%n", world.t - startWorldT, e.get("kind").getAsString(), e.get("msg").getAsString());
            emit(e);
        }
        if (trace > 0 && world.t >= nextTrace) {
            nextTrace = world.t + trace;
            double[] es = world.est;
            System.out.printf("   t=%6.2f %-10s pose(%7.2f,%7.2f,%7.1f) est(%s) wheels[%5.1f %5.1f %5.1f %5.1f] fly %6.0f/%4.0f held %d intake %.1f feed %.1f%n",
                    world.t - startWorldT, phase, world.rx, world.ry, Math.toDegrees(world.rh),
                    es == null ? "none" : String.format("%7.2f,%7.2f,%7.1f", es[0], es[1], Math.toDegrees(es[2])),
                    world.wheel[0], world.wheel[1], world.wheel[2], world.wheel[3], world.launcher.tps,
                    world.launcher.velCmd ? world.launcher.velTarget : 0, world.held, world.intake.physPower(), world.launcherFeeder.physPower());
            if (world.intake.physPower() > 0.3) {
                StringBuilder nb = new StringBuilder("      floor POLLEN near robot (robot frame fwd,left):");
                double c = Math.cos(world.rh), sn = Math.sin(world.rh);
                for (World.Ball b : world.balls) {
                    if (b.kind != 0 || b.state != World.FLOOR) continue;
                    double dx = b.x - world.rx, dy = b.y - world.ry;
                    double lx = dx * c + dy * sn, ly = -dx * sn + dy * c;
                    if (Math.hypot(lx, ly) < 20) nb.append(String.format(" (%.1f,%.1f)", lx, ly));
                }
                System.out.println(nb);
            }
        }
    }

    private void emit(JsonObject e) {
        Server s = server;
        if (s != null) s.broadcast(e.toString());
        else eventLog.add(e);
    }

    private void broadcastState() {
        Server s = server;
        if (s == null) return;
        s.broadcast(world.stateJson(phase, clock, runner.name, runner.status.name(), speed));
        if (telemVersion != telemSent) {
            telemSent = telemVersion;
            s.broadcast(telemetryJson());
        }
    }

    public String telemetryJson() {
        JsonObject o = new JsonObject();
        o.addProperty("type", "telemetry");
        JsonArray lines = new JsonArray();
        for (String l : dsLines) lines.add(l);
        synchronized (dash) {
            if (!dash.isEmpty()) {
                lines.add("[FTC Dashboard values]");
                for (Map.Entry<String, String> e : dash.entrySet()) lines.add(e.getKey() + " : " + e.getValue());
            }
        }
        o.add("lines", lines);
        return o.toString();
    }

    // =====================================================================================
    // match flow
    // =====================================================================================

    private void matchTick(double dt) {
        OpRunner.Status st = runner.status;
        if (st != lastStatus) {
            lastStatus = st;
            world.event("opmode", runner.name + ": " + st);
        }
        if (st == OpRunner.Status.ERROR && runner.error != null && runner.error != reportedError) {
            reportedError = runner.error;
            JsonObject e = world.event("error", runner.name + " crashed: " + runner.error);
            StringBuilder sb = new StringBuilder();
            StackTraceElement[] tr = runner.error.getStackTrace();
            for (int i = 0; i < Math.min(tr.length, 14); i++) sb.append("  at ").append(tr[i]).append('\n');
            e.addProperty("stack", sb.toString());
        }
        switch (phase) {
            case "AUTO":
                clock -= dt;
                if (!over && (clock <= 0 || (!runner.active() && (!fullMatch || userStopped || st == OpRunner.Status.ERROR)))) {
                    over = true;
                    overAt = world.t;
                    cutOff = runner.active();
                    noteOpEnd();
                    if (runner.active()) runner.stop();
                }
                if (over && (world.quiet() || world.t - overAt > 3.0)) {
                    assessAuto();
                    TeamCode tc = team;
                    TeamCode.OpInfo tele = (fullMatch && !userStopped && tc != null && teleopName != null) ? tc.find(teleopName) : null;
                    if (tele != null) {
                        phase = "TRANSITION";
                        clock = 8;
                        runner.init(tc, tele);
                    } else {
                        finish("AUTO");
                    }
                }
                break;
            case "TRANSITION":
                clock -= dt;
                if (clock <= 0) {
                    runner.start();
                    phase = "TELEOP";
                    clock = 120;
                    world.autoPeriod = false;
                    over = false;
                    userStopped = false;
                    runOpName = runner.name;
                }
                break;
            case "TELEOP":
                clock -= dt;
                if (!over && (clock <= 0 || !runner.active())) {
                    over = true;
                    overAt = world.t;
                    if (!fullMatch) noteOpEnd();
                    if (fullMatch && runner.active()) runner.stop();
                }
                if (over && (world.quiet() || world.t - overAt > 3.0)) {
                    world.parkEnd = world.inLoadingZone(world.alliance);
                    world.event("park", world.parkEnd ? "PARK at end of match: +5" : "No PARK at end of match");
                    finish("MATCH");
                }
                break;
            default:
                break;
        }
    }

    private void noteOpEnd() {
        if (runner.status == OpRunner.Status.ERROR) {
            endNote = "crashed: " + runner.error;
            opEndedRel = runner.endedAt >= 0 ? runner.endedAt - startClockT : -1;
        } else if (userStopped) {
            endNote = "stopped with the STOP button";
        } else if (runner.active()) {
            endNote = phase.equals("AUTO") ? "STILL RUNNING when the 30 s AUTO timer stopped it" : "running at the end of the period";
        } else {
            opEndedRel = runner.endedAt >= 0 ? runner.endedAt - startClockT : world.t - startWorldT;
            endNote = String.format("finished by itself %.1f s after START", opEndedRel);
        }
    }

    private void assessAuto() {
        world.leaveAuto = !world.touchingWall();
        world.leaveClearance = world.wallClearance();
        world.parkAuto = world.inLoadingZone(world.alliance);
        world.event("leave", world.leaveAuto
                ? String.format("LEAVE: +3 (robot is %.1f in clear of the wall)", world.leaveClearance)
                : "No LEAVE: robot still touching a perimeter wall");
        world.event("park", world.parkAuto ? "PARK in AUTO: +5" : "No PARK in AUTO: robot is not over its LOADING ZONE");
    }

    private void finish(String kind) {
        phase = "DONE";
        clock = 0;
        JsonObject rep = buildReport(kind);
        lastReport = rep;
        fullMatch = false;
        Server s = server;
        if (s != null) s.broadcast(rep.toString());
    }

    // =====================================================================================
    // Driver Station commands (physics thread)
    // =====================================================================================

    void doInit(long seed) {
        TeamCode tc = team;
        if (tc == null || !tc.ok) {
            world.event("error", "The team code did not compile. Fix the errors and press Reload code.");
            return;
        }
        TeamCode.OpInfo info = selected == null ? null : tc.find(selected);
        if (info == null) {
            world.event("error", "Pick an OpMode first.");
            return;
        }
        runner.stop();
        clearRun();
        String lower = info.name.toLowerCase();
        if (lower.contains("blue")) world.alliance = "BLUE";
        else if (lower.contains("red")) world.alliance = "RED";
        if (info.flavor.equals("AUTO")) {
            // A new match: fresh field, robot placed where this OpMode says it starts, 4 POLLEN preloaded.
            if (info.startPose != null) {
                world.startX = info.startPose[0];
                world.startY = info.startPose[1];
                world.startH = info.startPose[2];
            }
            world.reset(seed);
            if (info.startPose != null) {
                world.event("log", String.format("Robot placed on %s's start pose (%.1f, %.1f, %.1f deg)",
                        info.name, info.startPose[0], info.startPose[1], Math.toDegrees(info.startPose[2])));
            }
            phase = "IDLE";
            clock = 0;
            autoOpName = info.name;
        }
        fullMatch = false;
        teleopName = null;
        runner.init(tc, info);
        runOpName = info.name;
    }

    void doStart() {
        if (runner.status != OpRunner.Status.INIT) return;
        runner.start();
        over = false;
        userStopped = false;
        cutOff = false;
        opEndedRel = -1;
        endNote = "";
        startWorldT = world.t;
        startClockT = SimClock.now();
        if (runner.info.flavor.equals("AUTO")) {
            phase = "AUTO";
            clock = 30;
            world.autoPeriod = true;
        } else {
            phase = "TELEOP";
            clock = 120;
            world.autoPeriod = false;
        }
    }

    private void doStop() {
        if (!runner.active()) return;
        userStopped = true;
        noteOpEnd();
        runner.stop();
    }

    private void clearRun() {
        dsLines = List.of();
        synchronized (dash) { dash.clear(); }
        telemVersion++;
        previewMsg = null;
        previewCount = 0;
        lastTarget = null;
        reportedError = null;
        lastReport = null;
        Server s = server;
        if (s != null) {
            s.broadcast("{\"type\":\"overlay\",\"kind\":\"clear\"}");
            // a new run starts: take the previous run's report off the screen
            s.broadcast("{\"type\":\"report\",\"clear\":true}");
        }
    }

    private void doReset() {
        runner.stop();
        clearRun();
        world.reset(0);
        phase = "IDLE";
        clock = 0;
        fullMatch = false;
        over = false;
        world.event("log", "Field reset");
    }

    public void reload(Path sourceRoot, Path repoDir, String label) {
        runner.stop();
        team = TeamCode.compile(sourceRoot, repoDir, cacheDir, label);
        Server s = server;
        if (s != null) s.broadcast(helloJson());
    }

    /** A message from the page (called on a server thread; the work happens on the physics thread). */
    public void handle(JsonObject m) {
        post(() -> dispatch(m));
    }

    private void dispatch(JsonObject m) {
        String type = m.has("type") ? m.get("type").getAsString() : "";
        switch (type) {
            case "select":
                selected = m.get("opMode").getAsString();
                break;
            case "init":
                doInit(0);
                break;
            case "start":
                doStart();
                break;
            case "stop":
                doStop();
                break;
            case "reset":
                doReset();
                break;
            case "place":
                if (runner.status != OpRunner.Status.RUNNING) {
                    world.place(m.get("x").getAsDouble(), m.get("y").getAsDouble(), m.get("h").getAsDouble());
                }
                break;
            case "alliance":
                world.alliance = "RED".equalsIgnoreCase(m.get("value").getAsString()) ? "RED" : "BLUE";
                break;
            case "knobs": {
                JsonObject v = m.getAsJsonObject("values");
                knobs.apply(v);
                knobs.save(knobsFile);
                SimClock.loopPeriod = knobs.loopMs / 1000.0;
                synchronized (World.LOCK) { world.calibrate(); }
                break;
            }
            case "reload": {
                TeamCode tc = team;
                if (tc != null) reload(tc.sourceRoot, tc.repoDir, tc.label);
                break;
            }
            case "speed":
                speed = Math.max(0, Math.min(8, m.get("value").getAsDouble()));
                break;
            case "match":
                selected = m.get("auto").getAsString();
                doInit(0);
                if (runner.status == OpRunner.Status.INIT) {
                    fullMatch = true;
                    teleopName = m.get("teleop").getAsString();
                    doStart();
                }
                break;
            case "gamepad": {
                int id = m.get("id").getAsInt() - 1;
                if (id < 0 || id > 1) break;
                String[] keys = {"lx", "ly", "rx", "ry", "lt", "rt"};
                for (int i = 0; i < 6; i++) padAxes[id][i] = m.has(keys[i]) ? m.get(keys[i]).getAsFloat() : 0f;
                padButtons[id].clear();
                if (m.has("b")) for (JsonElement b : m.getAsJsonArray("b")) padButtons[id].add(b.getAsString());
                break;
            }
            default:
                break;
        }
    }

    // =====================================================================================
    // hooks from the stand-in SDK (OpMode thread)
    // =====================================================================================

    @Override public void preview(Canvas c) {
        previewCount = c.getOperations().size();
        Server s = server;
        if (s == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("type", "overlay");
        o.addProperty("kind", "preview");
        o.add("ops", convert(c.getOperations(), 0));
        previewMsg = o.toString();
        s.broadcast(previewMsg);
    }

    @Override public void packet(TelemetryPacket p) {
        try {
            Field f = TelemetryPacket.class.getDeclaredField("data");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, String> data = (Map<String, String>) f.get(p);
            if (data != null && !data.isEmpty()) {
                synchronized (dash) { dash.putAll(data); }
                telemVersion++;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // dashboard values are a convenience only
        }
        Server s = server;
        if (s == null) return;
        long now = System.nanoTime();
        if (now - lastLiveNs < 70_000_000L) return;
        lastLiveNs = now;
        JsonObject o = new JsonObject();
        o.addProperty("type", "overlay");
        o.addProperty("kind", "live");
        o.add("ops", convert(p.fieldOverlay().getOperations(), previewCount));
        s.broadcast(o.toString());
    }

    @Override public void record(String channel, Object message) {
        if ("ESTIMATED_POSE".equals(channel)) {
            double[] p = poseOf(message);
            if (p != null) world.est = p;
        } else if ("TARGET_POSE".equals(channel)) {
            double[] p = poseOf(message);
            if (p != null) lastTarget = p;
        }
    }

    private static double[] poseOf(Object msg) {
        try {
            Class<?> k = msg.getClass();
            return new double[] {k.getField("x").getDouble(msg), k.getField("y").getDouble(msg), k.getField("heading").getDouble(msg)};
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Object priv(Object o, String name) throws ReflectiveOperationException {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    private static JsonArray arr(double[] a) {
        JsonArray j = new JsonArray();
        for (double v : a) j.add(Math.round(v * 100) / 100.0);
        return j;
    }

    /** FTC Dashboard canvas operations to the page's overlay format. */
    private static JsonArray convert(List<CanvasOp> ops, int from) {
        JsonArray out = new JsonArray();
        for (int i = Math.max(0, from); i < ops.size(); i++) {
            CanvasOp op = ops.get(i);
            try {
                JsonObject o = new JsonObject();
                switch (op.getClass().getSimpleName()) {
                    case "Stroke":
                        o.addProperty("op", "stroke");
                        o.addProperty("color", (String) priv(op, "color"));
                        break;
                    case "Fill":
                        o.addProperty("op", "fill");
                        o.addProperty("color", (String) priv(op, "color"));
                        break;
                    case "StrokeWidth":
                        o.addProperty("op", "width");
                        o.addProperty("w", (Integer) priv(op, "width"));
                        break;
                    case "Polyline":
                        o.addProperty("op", "polyline");
                        o.add("x", arr((double[]) priv(op, "xPoints")));
                        o.add("y", arr((double[]) priv(op, "yPoints")));
                        break;
                    case "Polygon":
                        o.addProperty("op", "polygon");
                        o.add("x", arr((double[]) priv(op, "xPoints")));
                        o.add("y", arr((double[]) priv(op, "yPoints")));
                        o.addProperty("fill", !(Boolean) priv(op, "stroke"));
                        break;
                    case "Circle":
                        o.addProperty("op", "circle");
                        o.addProperty("x", (Double) priv(op, "x"));
                        o.addProperty("y", (Double) priv(op, "y"));
                        o.addProperty("r", (Double) priv(op, "radius"));
                        o.addProperty("fill", !(Boolean) priv(op, "stroke"));
                        break;
                    default:
                        continue;
                }
                out.add(o);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // skip an operation we cannot read
            }
        }
        return out;
    }

    // =====================================================================================
    // hello and report
    // =====================================================================================

    public String helloJson() {
        JsonObject o = new JsonObject();
        o.addProperty("type", "hello");
        TeamCode tc = team;
        JsonArray ops = new JsonArray();
        if (tc != null) {
            o.add("code", tc.codeJson());
            for (TeamCode.OpInfo i : tc.opModes) {
                JsonObject j = new JsonObject();
                j.addProperty("name", i.name);
                j.addProperty("group", i.group);
                j.addProperty("flavor", i.flavor);
                ops.add(j);
            }
        }
        o.add("opModes", ops);
        o.add("knobs", knobs.toJson());
        JsonArray meta = new JsonArray();
        for (Knobs.Meta m : Knobs.META) {
            JsonObject j = new JsonObject();
            j.addProperty("key", m.key);
            j.addProperty("label", m.label);
            j.addProperty("group", m.group);
            j.addProperty("min", m.min);
            j.addProperty("max", m.max);
            j.addProperty("step", m.step);
            meta.add(j);
        }
        o.add("knobMeta", meta);
        o.add("field", fieldJson());
        JsonObject r = new JsonObject();
        r.addProperty("length", World.HL * 2);
        r.addProperty("width", World.HW * 2);
        r.addProperty("height", World.ROBOT_H);
        r.addProperty("wheelRadius", World.WHEEL_R);
        r.addProperty("launchExitX", World.EXIT_X);
        r.addProperty("launchExitZ", World.EXIT_Z);
        o.add("robot", r);
        if (selected != null) o.addProperty("selected", selected);
        return o.toString();
    }

    private static JsonObject rect(double[] z) {
        JsonObject j = new JsonObject();
        j.addProperty("x0", z[0]); j.addProperty("x1", z[1]); j.addProperty("y0", z[2]); j.addProperty("y1", z[3]);
        return j;
    }

    private static JsonObject fieldJson() {
        JsonObject f = new JsonObject();
        f.addProperty("half", World.HALF);
        f.addProperty("tile", 24);
        f.addProperty("wallHeight", World.WALL_H);
        JsonObject lz = new JsonObject();
        lz.add("blue", rect(World.LZ_BLUE));
        lz.add("red", rect(World.LZ_RED));
        f.add("lz", lz);
        JsonObject g = new JsonObject();
        g.add("blue", rect(new double[] {-72, -70, 49, 72}));
        g.add("red", rect(new double[] {70, 72, -72, -49}));
        f.add("garden", g);
        JsonArray fl = new JsonArray();
        String[] walls = {"back", "blue", "red", "audience"};
        for (int i = 0; i < World.FLOWERS.length; i++) {
            JsonObject j = new JsonObject();
            j.addProperty("x", World.FLOWERS[i][0]);
            j.addProperty("y", World.FLOWERS[i][1]);
            j.addProperty("wall", walls[i]);
            fl.add(j);
        }
        f.add("flowers", fl);
        JsonObject h = new JsonObject();
        h.addProperty("pivotZ", World.PIVOT_Z);
        h.addProperty("blueY", World.BLUE_Y);
        h.addProperty("redY", World.RED_Y);
        h.addProperty("frameHalfW", World.FRAME_HALF_W);
        h.addProperty("frameHalfD", World.FRAME_HALF_D);
        h.addProperty("cellInner", World.CELL_IN);
        h.addProperty("cellOuter", World.CELL_OUT);
        h.addProperty("cellHalfW", World.CELL_HALF_W);
        h.addProperty("cellFloorW", World.CELL_FLOOR);
        h.addProperty("cellSide", World.CELL_SIDE);
        h.addProperty("cellPeak", World.CELL_PEAK);
        h.addProperty("tiltDeg", World.TILT);
        f.add("hive", h);
        return f;
    }

    private static double r1(double v) { return Math.round(v * 10) / 10.0; }

    private JsonObject buildReport(String kind) {
        synchronized (World.LOCK) {
            JsonObject data = new JsonObject();
            StringBuilder sb = new StringBuilder();
            TeamCode tc = team;
            String who = world.alliance;
            JsonObject sc = world.score(who);
            String op = kind.equals("AUTO") ? runOpName : autoOpName + " + " + runOpName;
            sb.append(kind.equals("AUTO") ? "AUTO report: " : "MATCH report: ").append(op).append(" (").append(who).append(")\n");
            if (tc != null) {
                sb.append("Code: ").append(tc.label).append(tc.commit.isEmpty() ? "" : " @ " + tc.commit).append(tc.dirty ? " + uncommitted edits" : "").append('\n');
                data.addProperty("code", tc.label);
                data.addProperty("commit", tc.commit);
            }
            if (runner.error != null && endNote.isEmpty()) endNote = "crashed: " + runner.error;
            sb.append("OpMode: ").append(endNote.isEmpty() ? "ended" : endNote).append('\n');
            data.addProperty("opMode", op);
            data.addProperty("alliance", who);
            data.addProperty("endNote", endNote);
            data.addProperty("cutOff", cutOff);
            data.addProperty("opEnded", r1(opEndedRel));
            if (runner.error != null) data.addProperty("error", String.valueOf(runner.error));

            sb.append(String.format("Score (%s): %d = LEAVE %d + PARK %d + TIPS %d x 20 + CELL %d%n", who,
                    sc.get("total").getAsInt(), sc.get("leave").getAsInt(), sc.get("park").getAsInt(),
                    sc.get("tips").getAsInt(), sc.get("cell").getAsInt()));
            sb.append("  LEAVE: ").append(world.leaveAuto
                    ? String.format("yes (%.1f in clear of the wall%s)", world.leaveClearance, world.leaveClearance < 1.5 ? ", that is close" : "")
                    : "NO (still touching a wall at the end of AUTO)");
            sb.append("   PARK in AUTO: ").append(world.parkAuto ? "yes" : "NO (robot not over the " + who.toLowerCase() + " LOADING ZONE)");
            if (kind.equals("MATCH")) sb.append("   PARK at end: ").append(world.parkEnd ? "yes" : "NO");
            sb.append('\n');
            data.add("score", sc);
            data.addProperty("leave", world.leaveAuto);
            data.addProperty("wallClearance", r1(world.leaveClearance));
            data.addProperty("parkAuto", world.parkAuto);
            data.addProperty("parkEnd", world.parkEnd);
            World.Hive mine = who.equals("RED") ? world.red : world.blue;
            data.addProperty("tips", mine.tips);
            data.addProperty("tipsAuto", mine.tipsAuto);

            int scored = 0, missed = 0, pending = 0;
            Map<String, Integer> why = new TreeMap<>();
            JsonArray shots = new JsonArray();
            StringBuilder lines = new StringBuilder();
            for (World.Shot s : world.shots) {
                if (s.result.equals("score")) scored++;
                else if (s.result.equals("miss")) { missed++; why.merge(s.why, 1, Integer::sum); }
                else pending++;
                JsonObject j = new JsonObject();
                j.addProperty("n", s.index);
                j.addProperty("t", r1(s.t - startWorldT));
                j.addProperty("flyVel", Math.round(s.flyTps));
                j.addProperty("flyTarget", Math.round(s.flyTarget));
                j.addProperty("exitSpeed", r1(s.exitSpeed));
                j.addProperty("result", s.result);
                j.addProperty("why", s.why);
                j.addProperty("x", r1(s.x));
                j.addProperty("y", r1(s.y));
                j.addProperty("hDeg", r1(Math.toDegrees(s.h)));
                shots.add(j);
                if (world.shots.size() <= 24) {
                    lines.append(String.format("  #%-2d t=%5.1fs  flywheel %4.0f of %4.0f  from (%.1f, %.1f)  %s%s%n", s.index,
                            s.t - startWorldT, s.flyTps, s.flyTarget, s.x, s.y, s.result.equals("score") ? "SCORED" : s.result,
                            s.why.isEmpty() ? "" : ": " + s.why));
                }
            }
            sb.append(String.format("Shots: %d fired, %d scored, %d missed %s%s%n", world.shots.size(), scored, missed,
                    why.isEmpty() ? "" : why.toString(), pending > 0 ? ", " + pending + " still in the air" : ""));
            sb.append(lines);
            sb.append(String.format("POLLEN collected: %d, still in the robot: %d%n", world.intakeCount, world.held));
            data.add("shots", shots);
            data.addProperty("fired", world.shots.size());
            data.addProperty("scored", scored);
            data.addProperty("missed", missed);
            JsonObject whyJ = new JsonObject();
            for (Map.Entry<String, Integer> e : why.entrySet()) whyJ.addProperty(e.getKey(), e.getValue());
            data.add("missWhy", whyJ);
            data.addProperty("nominalExitSpeed", r1(world.nominalExitSpeed()));
            data.addProperty("collected", world.intakeCount);
            data.addProperty("held", world.held);

            sb.append(String.format("Final pose: (%.1f, %.1f, %.1f deg), drove %.0f in%n", world.rx, world.ry,
                    Math.toDegrees(normalize(world.rh)), world.distanceDriven));
            JsonArray fp = new JsonArray();
            fp.add(r1(world.rx)); fp.add(r1(world.ry)); fp.add(r1(Math.toDegrees(normalize(world.rh))));
            data.add("finalPose", fp);
            data.addProperty("distance", r1(world.distanceDriven));
            double[] tg = lastTarget;
            if (tg != null) {
                double err = Math.hypot(world.rx - tg[0], world.ry - tg[1]);
                double herr = Math.toDegrees(normalize(world.rh - tg[2]));
                sb.append(String.format("Last path target: (%.1f, %.1f, %.1f deg). Robot is %.1f in and %.1f deg from it%n",
                        tg[0], tg[1], Math.toDegrees(normalize(tg[2])), err, herr));
                data.addProperty("targetErrIn", r1(err));
                data.addProperty("targetErrDeg", r1(herr));
            }
            double[] es = world.est;
            if (es != null) {
                double err = Math.hypot(world.rx - es[0], world.ry - es[1]);
                sb.append(String.format("Team's own pose estimate is %.1f in from the true pose%n", err));
                data.addProperty("estErrIn", r1(err));
            }
            JsonObject rep = new JsonObject();
            rep.addProperty("type", "report");
            rep.addProperty("text", sb.toString());
            rep.add("data", data);
            return rep;
        }
    }

    private static double normalize(double a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }
}
