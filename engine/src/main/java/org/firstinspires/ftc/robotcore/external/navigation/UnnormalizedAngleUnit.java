package org.firstinspires.ftc.robotcore.external.navigation;

public enum UnnormalizedAngleUnit {
    DEGREES(0), RADIANS(1);

    public final byte bVal;

    UnnormalizedAngleUnit(int i) { bVal = (byte) i; }

    public double fromDegrees(double degrees) { return this == RADIANS ? degrees / 180.0 * Math.PI : degrees; }
    public float fromDegrees(float degrees) { return (float) fromDegrees((double) degrees); }
    public double fromRadians(double radians) { return this == RADIANS ? radians : radians / Math.PI * 180.0; }
    public float fromRadians(float radians) { return (float) fromRadians((double) radians); }

    public double fromUnit(UnnormalizedAngleUnit them, double theirs) {
        return them == RADIANS ? fromRadians(theirs) : fromDegrees(theirs);
    }
    public double fromUnit(AngleUnit them, double theirs) {
        return them == AngleUnit.RADIANS ? fromRadians(theirs) : fromDegrees(theirs);
    }

    public double toDegrees(double inOurUnits) { return DEGREES.fromUnit(this, inOurUnits); }
    public double toRadians(double inOurUnits) { return RADIANS.fromUnit(this, inOurUnits); }

    public AngleUnit getNormalized() { return this == RADIANS ? AngleUnit.RADIANS : AngleUnit.DEGREES; }
}
