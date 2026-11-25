package org.firstinspires.ftc.robotcore.external.navigation;

public enum CurrentUnit {
    AMPS, MILLIAMPS;

    public double toAmps(double v) { return this == AMPS ? v : v / 1000.0; }
    public double toMilliAmps(double v) { return this == AMPS ? v * 1000.0 : v; }
    public double convert(double v, CurrentUnit from) { return this == AMPS ? from.toAmps(v) : from.toMilliAmps(v); }
}
