package com.qualcomm.robotcore.eventloop.opmode;

import sim.SimClock;

/** Stand-in for the SDK LinearOpMode. Same semantics; waits use the simulated clock. */
public abstract class LinearOpMode extends OpMode {
    public LinearOpMode() {}

    public abstract void runOpMode() throws InterruptedException;

    public void waitForStart() {
        while (!isStarted()) {
            try {
                SimClock.loopYield();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public final void idle() {
        try {
            SimClock.loopYield();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public final void sleep(long milliseconds) {
        try {
            SimClock.sleep(milliseconds / 1000.0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public final boolean opModeIsActive() {
        SimClock.touch();
        boolean isActive = !isStopRequested() && isStarted();
        if (isActive) idle();
        return isActive;
    }

    public final boolean opModeInInit() {
        SimClock.touch();
        return !isStarted() && !isStopRequested();
    }

    public final boolean isStarted() {
        SimClock.touch();
        return simStarted || Thread.currentThread().isInterrupted();
    }

    public final boolean isStopRequested() {
        SimClock.touch();
        return simStopRequested || Thread.currentThread().isInterrupted();
    }

    @Override public final void init() {}
    @Override public final void init_loop() {}
    @Override public final void start() {}
    @Override public final void loop() {}
    @Override public final void stop() {}
}
