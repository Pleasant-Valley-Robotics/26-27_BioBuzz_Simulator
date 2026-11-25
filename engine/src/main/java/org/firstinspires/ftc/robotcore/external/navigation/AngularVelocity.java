package org.firstinspires.ftc.robotcore.external.navigation;

public class AngularVelocity {
    public AngleUnit unit;
    public float xRotationRate;
    public float yRotationRate;
    public float zRotationRate;
    public long acquisitionTime;

    public AngularVelocity() { this(AngleUnit.DEGREES, 0, 0, 0, 0); }

    public AngularVelocity(AngleUnit unit, float xRotationRate, float yRotationRate, float zRotationRate, long acquisitionTime) {
        this.unit = unit;
        this.xRotationRate = xRotationRate;
        this.yRotationRate = yRotationRate;
        this.zRotationRate = zRotationRate;
        this.acquisitionTime = acquisitionTime;
    }

    public AngularVelocity toAngleUnit(AngleUnit to) {
        if (to == unit) return this;
        double k = to == AngleUnit.RADIANS ? Math.PI / 180.0 : 180.0 / Math.PI;
        return new AngularVelocity(to, (float) (xRotationRate * k), (float) (yRotationRate * k),
                (float) (zRotationRate * k), acquisitionTime);
    }
}
