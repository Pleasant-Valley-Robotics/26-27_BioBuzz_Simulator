package sim;

import java.util.List;
import java.util.function.Consumer;

import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver;
import com.qualcomm.hardware.limelightvision.Limelight3A;
import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.SimOpModeAccess;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.VoltageSensor;

import sim.hw.CRServoFacade;
import sim.hw.ImuFacade;
import sim.hw.MotorFacade;
import sim.hw.MotorSim;
import sim.hw.RunContext;
import sim.hw.SimTelemetry;

/** Plays the Driver Station: INIT, START, STOP for one OpMode at a time, on its own thread. */
public final class OpRunner {
    public enum Status { NONE, INIT, RUNNING, STOPPED, ERROR }

    private final World world;
    private final Knobs knobs;
    private final Consumer<List<String>> telemetrySink;

    public volatile Status status = Status.NONE;
    public volatile String name = null;
    public volatile TeamCode.OpInfo info = null;
    public volatile OpMode op = null;
    public volatile Throwable error = null;
    public volatile double endedAt = -1;
    private volatile Thread thread = null;
    private volatile RunContext ctx = null;

    public OpRunner(World world, Knobs knobs, Consumer<List<String>> telemetrySink) {
        this.world = world;
        this.knobs = knobs;
        this.telemetrySink = telemetrySink;
    }

    public boolean active() { return status == Status.INIT || status == Status.RUNNING; }

    /** Radical's robot configuration (the names their code asks the hardware map for). */
    private HardwareMap buildHardware(RunContext c) {
        HardwareMap hw = new HardwareMap();
        for (MotorSim m : world.drive) hw.put(m.name, new MotorFacade(m, c));
        hw.put("launcher", new MotorFacade(world.launcher, c));
        hw.put("intake", new MotorFacade(world.intake, c));
        hw.put("leftFeeder", new CRServoFacade(world.leftFeeder, c));
        hw.put("rightFeeder", new CRServoFacade(world.rightFeeder, c));
        hw.put("launcherFeeder", new CRServoFacade(world.launcherFeeder, c));
        hw.put("flowerStick", new sim.hw.StickServoFacade(world, c));
        hw.put("imu", new ImuFacade(world));
        hw.put("pinpoint", new GoBildaPinpointDriver(world.truth(c)));
        hw.put("limelight", new Limelight3A(world::limelightHive));
        hw.put("Control Hub", new LynxModule("Control Hub", true));
        hw.put("Expansion Hub 2", new LynxModule("Expansion Hub 2", false));
        hw.put("Control Hub", (VoltageSensor) () -> { SimClock.touch(); return knobs.batteryVolts; });
        return hw;
    }

    public void init(TeamCode team, TeamCode.OpInfo which) {
        stop();
        error = null;
        endedAt = -1;
        info = which;
        name = which.name;
        final RunContext c = new RunContext();
        ctx = c;
        world.resetDeviceConfig();
        world.est = null;
        final OpMode instance;
        try {
            instance = team.newInstance(which);
        } catch (Throwable t) {
            error = t.getCause() != null ? t.getCause() : t;
            status = Status.ERROR;
            c.live = false;
            return;
        }
        instance.hardwareMap = buildHardware(c);
        instance.telemetry = new SimTelemetry(telemetrySink);
        op = instance;
        status = Status.INIT;
        Thread th = new Thread(() -> runBody(instance, c), "OpMode " + which.name);
        th.setDaemon(true);
        thread = th;
        th.start();
    }

    private void runBody(OpMode instance, RunContext c) {
        SimClock.bindOpThread(Thread.currentThread());
        Throwable failure = null;
        try {
            instance.resetRuntime();
            if (instance instanceof LinearOpMode) {
                ((LinearOpMode) instance).runOpMode();
            } else {
                instance.init();
                while (!SimOpModeAccess.isStarted(instance) && !stopping(instance)) {
                    instance.time = instance.getRuntime();
                    instance.init_loop();
                    instance.telemetry.update();
                    SimClock.loopYield();
                }
                if (!stopping(instance)) {
                    instance.resetRuntime();
                    instance.start();
                    while (!stopping(instance)) {
                        instance.time = instance.getRuntime();
                        instance.loop();
                        instance.telemetry.update();
                        SimClock.loopYield();
                    }
                }
                instance.stop();
            }
        } catch (InterruptedException | OpMode.OpModeTerminated e) {
            // normal stop
        } catch (Throwable t) {
            failure = t;
        } finally {
            c.live = false;
            world.stopAllCommands();
            SimClock.unbindOpThread(Thread.currentThread());
            if (ctx == c) {
                error = failure;
                endedAt = SimClock.now();
                status = failure != null ? Status.ERROR : Status.STOPPED;
            }
        }
    }

    private static boolean stopping(OpMode instance) {
        return SimOpModeAccess.isStopRequested(instance) || Thread.currentThread().isInterrupted();
    }

    public void start() {
        OpMode o = op;
        if (o == null || status != Status.INIT) return;
        o.gamepad1.resetEdgeDetection();
        o.gamepad2.resetEdgeDetection();
        o.resetRuntime();
        SimOpModeAccess.setStarted(o, true);
        status = Status.RUNNING;
    }

    /** STOP button. The OpMode thread is interrupted exactly like the SDK does it. */
    public void stop() {
        Thread th = thread;
        OpMode o = op;
        RunContext c = ctx;
        if (th == null || o == null) return;
        SimOpModeAccess.setStopRequested(o, true);
        th.interrupt();
        try {
            th.join(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (th.isAlive()) {
            // The OpMode ignores the stop request (a loop that never checks). Cut its hardware off.
            if (c != null) c.live = false;
            world.stopAllCommands();
            SimClock.unbindOpThread(th);
            if (ctx == c && active()) {
                endedAt = SimClock.now();
                status = Status.STOPPED;
            }
        }
        thread = null;
    }
}
