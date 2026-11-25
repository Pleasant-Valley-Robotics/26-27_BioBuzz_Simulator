package org.firstinspires.ftc.robotcore.external.navigation;

public enum DistanceUnit {
    METER(0), CM(1), MM(2), INCH(3);

    public final byte bVal;
    public static final double infinity = Double.MAX_VALUE;
    public static final double mmPerInch = 25.4;
    public static final double mPerInch = mmPerInch * 0.001;

    DistanceUnit(int i) { bVal = (byte) i; }

    private double mmPerUnit() {
        switch (this) {
            case METER: return 1000.0;
            case CM: return 10.0;
            case MM: return 1.0;
            default: return mmPerInch;
        }
    }

    public double fromMeters(double meters) { return meters * 1000.0 / mmPerUnit(); }
    public double fromInches(double inches) { return inches * mmPerInch / mmPerUnit(); }
    public double fromCm(double cm) { return cm * 10.0 / mmPerUnit(); }
    public double fromMm(double mm) { return mm / mmPerUnit(); }
    public double fromUnit(DistanceUnit him, double his) { return his * him.mmPerUnit() / mmPerUnit(); }

    public double toMeters(double inOurUnits) { return METER.fromUnit(this, inOurUnits); }
    public double toInches(double inOurUnits) { return INCH.fromUnit(this, inOurUnits); }
    public double toCm(double inOurUnits) { return CM.fromUnit(this, inOurUnits); }
    public double toMm(double inOurUnits) { return MM.fromUnit(this, inOurUnits); }

    @Override public String toString() {
        switch (this) {
            case METER: return "m";
            case CM: return "cm";
            case MM: return "mm";
            default: return "in";
        }
    }
}
