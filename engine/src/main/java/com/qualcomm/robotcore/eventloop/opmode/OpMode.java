package com.qualcomm.robotcore.eventloop.opmode;

import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import sim.SimClock;

/** Stand-in for the SDK iterative OpMode base class. */
public abstract class OpMode {
    public volatile Gamepad gamepad1 = new Gamepad();
    public volatile Gamepad gamepad2 = new Gamepad();
    public Telemetry telemetry;
    public volatile HardwareMap hardwareMap;
    public volatile double time = 0.0;

    volatile double startTimeSeconds = SimClock.now();
    volatile boolean simStarted = false;
    volatile boolean simStopRequested = false;

    public OpMode() {}

    public abstract void init();
    public void init_loop() {}
    public void start() {}
    public abstract void loop();
    public void stop() {}

    public final void requestOpModeStop() { simStopRequested = true; }

    public final void terminateOpModeNow() {
        simStopRequested = true;
        throw new OpModeTerminated();
    }

    public double getRuntime() { return SimClock.now() - startTimeSeconds; }
    public void resetRuntime() { startTimeSeconds = SimClock.now(); }
    public void updateTelemetry(Telemetry telemetry) { telemetry.update(); }

    /** Thrown by terminateOpModeNow(); the simulator treats it as a normal stop. */
    public static final class OpModeTerminated extends Error {
        public OpModeTerminated() { super("terminateOpModeNow"); }
    }
}
