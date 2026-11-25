package com.acmerobotics.roadrunner;

import java.util.ArrayList;
import java.util.List;

import sim.SimClock;

/**
 * Replaces the RoadRunner "Actions" file facade so that RoadRunner reads the SIMULATED clock.
 * The real one is `now() = System.nanoTime() * 1e-9` plus the private helper seqCons. This class
 * sits earlier on the classpath than the actions jar, so every caller (SleepAction, the
 * trajectory builder, and the team's MecanumDrive) gets sim time. That is what lets the sim
 * pause, slow down, or run faster than real time.
 */
public final class Actions {
    private Actions() {}

    public static double now() {
        return SimClock.now();
    }

    private static Action seqCons(Action hd, Action tl) {
        if (tl instanceof SequentialAction) {
            List<Action> all = new ArrayList<>();
            all.add(hd);
            all.addAll(((SequentialAction) tl).getInitialActions());
            return new SequentialAction(all);
        }
        return new SequentialAction(hd, tl);
    }

    public static Action access$seqCons(Action hd, Action tl) {
        return seqCons(hd, tl);
    }
}
