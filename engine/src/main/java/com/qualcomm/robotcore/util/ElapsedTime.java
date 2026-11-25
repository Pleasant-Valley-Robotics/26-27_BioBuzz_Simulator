package com.qualcomm.robotcore.util;

import java.util.concurrent.TimeUnit;

import sim.SimClock;

/** Stand-in for the SDK ElapsedTime. It reads the SIMULATED clock, so it pauses and scales with the sim. */
public class ElapsedTime {
    public enum Resolution { SECONDS, MILLISECONDS }

    public static final long SECOND_IN_NANO = 1000000000L;
    public static final long MILLIS_IN_NANO = 1000000L;

    protected volatile long nsStartTime;
    protected final double resolution;

    public ElapsedTime() {
        resolution = SECOND_IN_NANO;
        nsStartTime = nsNow();
    }

    public ElapsedTime(long startTime) {
        resolution = SECOND_IN_NANO;
        nsStartTime = startTime;
    }

    public ElapsedTime(Resolution resolution) {
        this.resolution = resolution == Resolution.MILLISECONDS ? MILLIS_IN_NANO : SECOND_IN_NANO;
        nsStartTime = nsNow();
    }

    protected long nsNow() {
        SimClock.touch();
        return (long) (SimClock.now() * 1e9);
    }

    public long now(TimeUnit unit) { return unit.convert(nsNow(), TimeUnit.NANOSECONDS); }
    public void reset() { nsStartTime = nsNow(); }
    public double startTime() { return nsStartTime / resolution; }
    public long startTimeNanoseconds() { return nsStartTime; }
    public double time() { return (nsNow() - nsStartTime) / resolution; }
    public long time(TimeUnit unit) { return unit.convert(nanoseconds(), TimeUnit.NANOSECONDS); }
    public double seconds() { return nanoseconds() / (double) SECOND_IN_NANO; }
    public double milliseconds() { return seconds() * 1000.0; }
    public long nanoseconds() { return nsNow() - nsStartTime; }
    public Resolution getResolution() { return resolution == MILLIS_IN_NANO ? Resolution.MILLISECONDS : Resolution.SECONDS; }

    @Override public String toString() {
        return String.format("%1.4f %s", time(), resolution == MILLIS_IN_NANO ? "milliseconds" : "seconds");
    }
}
