package com.qualcomm.robotcore.eventloop.opmode;

/** Simulator-only door into the package-private run flags of an OpMode. Not part of the FTC SDK. */
public final class SimOpModeAccess {
    private SimOpModeAccess() {}

    public static void setStarted(OpMode op, boolean v) { op.simStarted = v; }
    public static void setStopRequested(OpMode op, boolean v) { op.simStopRequested = v; }
    public static boolean isStarted(OpMode op) { return op.simStarted; }
    public static boolean isStopRequested(OpMode op) { return op.simStopRequested; }
}
