package com.acmerobotics.roadrunner.ftc;

import com.acmerobotics.dashboard.canvas.Canvas;
import com.acmerobotics.dashboard.telemetry.TelemetryPacket;
import com.acmerobotics.roadrunner.Action;

import sim.SimClock;
import sim.SimHooks;

/**
 * Stand-in for RoadRunner-FTC's Actions.runBlocking. Same loop as the real one: preview once,
 * then run the action with a fresh TelemetryPacket until it returns false or the OpMode is
 * stopped. Instead of FTC Dashboard, the packets go to the simulator (path overlay, values).
 */
public final class Actions {
    private Actions() {}

    public static void runBlocking(Action a) {
        Canvas c = new Canvas();
        a.preview(c);
        SimHooks.preview(c);

        boolean b = true;
        while (b && !Thread.currentThread().isInterrupted()) {
            TelemetryPacket p = new TelemetryPacket();
            p.fieldOverlay().getOperations().addAll(c.getOperations());

            b = a.run(p);

            SimHooks.packet(p);
            try {
                SimClock.loopYield();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
