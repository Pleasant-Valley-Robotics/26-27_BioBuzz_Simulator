package com.acmerobotics.roadrunner.ftc;

import sim.SimHooks;

public final class FlightRecorder {
    private FlightRecorder() {}

    public static void write(String ch, Object o) { SimHooks.record(ch, o); }
}
