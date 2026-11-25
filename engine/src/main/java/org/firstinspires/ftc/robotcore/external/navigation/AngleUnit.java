package org.firstinspires.ftc.robotcore.external.navigation;

public enum AngleUnit {
    DEGREES(0), RADIANS(1);

    public final byte bVal;
    protected static final double TwoPi = 2 * Math.PI;
    public static final float Pif = (float) Math.PI;

    AngleUnit(int i) { bVal = (byte) i; }

    public double fromDegrees(double degrees) {
        return this == RADIANS ? normalize(degrees / 180.0 * Math.PI) : normalize(degrees);
    }
    public float fromDegrees(float degrees) { return (float) fromDegrees((double) degrees); }

    public double fromRadians(double radians) {
        return this == RADIANS ? normalize(radians) : normalize(radians / Math.PI * 180.0);
    }
    public float fromRadians(float radians) { return (float) fromRadians((double) radians); }

    public double fromUnit(AngleUnit them, double theirs) {
        return them == RADIANS ? fromRadians(theirs) : fromDegrees(theirs);
    }
    public float fromUnit(AngleUnit them, float theirs) { return (float) fromUnit(them, (double) theirs); }

    public double toDegrees(double inOurUnits) { return DEGREES.fromUnit(this, inOurUnits); }
    public float toDegrees(float inOurUnits) { return DEGREES.fromUnit(this, inOurUnits); }
    public double toRadians(double inOurUnits) { return RADIANS.fromUnit(this, inOurUnits); }
    public float toRadians(float inOurUnits) { return RADIANS.fromUnit(this, inOurUnits); }

    public double normalize(double mine) {
        return this == RADIANS ? normalizeRadians(mine) : normalizeDegrees(mine);
    }
    public float normalize(float mine) { return (float) normalize((double) mine); }

    public static double normalizeDegrees(double degrees) {
        while (degrees >= 180.0) degrees -= 360.0;
        while (degrees < -180.0) degrees += 360.0;
        return degrees;
    }

    public static double normalizeRadians(double radians) {
        while (radians >= Math.PI) radians -= TwoPi;
        while (radians < -Math.PI) radians += TwoPi;
        return radians;
    }

    public UnnormalizedAngleUnit getUnnormalized() {
        return this == RADIANS ? UnnormalizedAngleUnit.RADIANS : UnnormalizedAngleUnit.DEGREES;
    }
}
