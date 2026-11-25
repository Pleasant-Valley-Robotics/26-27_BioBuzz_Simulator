package com.qualcomm.hardware.gobilda;

import com.qualcomm.robotcore.hardware.HardwareDevice;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.Pose2D;
import org.firstinspires.ftc.robotcore.external.navigation.UnnormalizedAngleUnit;

import sim.SimClock;

/**
 * Stand-in for the goBILDA Pinpoint odometry computer. It integrates the simulated robot's TRUE
 * motion the way the real device integrates its two pods and IMU: pose is relative to the last
 * reset, x forward, y left, heading counterclockwise.
 *
 * The encoder directions and resolution that Radical's code sets on limlitConfig are treated as
 * the correct ones for their robot. If the code changes them, the estimate goes wrong in the same
 * way it would on the real robot (mirrored axis, wrong scale).
 */
public class GoBildaPinpointDriver implements HardwareDevice {
    public enum DeviceStatus {
        NOT_READY, READY, CALIBRATING, FAULT_X_POD_NOT_DETECTED, FAULT_Y_POD_NOT_DETECTED,
        FAULT_NO_PODS_DETECTED, FAULT_IMU_RUNAWAY, FAULT_BAD_READ
    }
    public enum EncoderDirection { FORWARD, REVERSED }
    public enum GoBildaOdometryPods { goBILDA_SWINGARM_POD, goBILDA_4_BAR_POD }
    public enum ReadData { ONLY_UPDATE_HEADING }

    /** Simulator side: where the true robot motion comes from. */
    public interface Truth {
        /** {x, y, heading} of the robot in the field frame (in, rad). */
        double[] pose();
        /** {forward, left, angular} velocity of the robot in its own frame (in/s, rad/s). */
        double[] bodyVel();
        /** Multiplicative scale error of the odometry (1.0 = perfect). */
        double scale();
        boolean live();
    }

    /** What Radical's robot is wired for (from PinpointLocalizer on limlitConfig). */
    private static final EncoderDirection CORRECT_X = EncoderDirection.FORWARD;
    private static final EncoderDirection CORRECT_Y = EncoderDirection.REVERSED;
    private static final double CORRECT_TICKS_PER_MM = 1.0 / (0.00206362854686156 * 25.4);
    private static final double CALIBRATION_SECONDS = 0.25;

    private final Truth truth;
    private EncoderDirection xDir = CORRECT_X;
    private EncoderDirection yDir = CORRECT_Y;
    private double ticksPerMm = CORRECT_TICKS_PER_MM;
    private double xOffsetMm = 0, yOffsetMm = 0;
    private double yawScalar = 1.0;

    private double[] lastTruth;
    private double px = 0, py = 0, ph = 0;      // integrated pose, inches and radians
    private double vx = 0, vy = 0, vh = 0;      // velocity in the Pinpoint frame
    private double readyAt = 0;
    private double lastUpdate = 0;
    private int loopTime = 0;

    public GoBildaPinpointDriver(Truth truth) {
        this.truth = truth;
        this.lastTruth = truth.pose();
        this.lastUpdate = SimClock.now();
    }

    private double sx() { return xDir == CORRECT_X ? 1.0 : -1.0; }
    private double sy() { return yDir == CORRECT_Y ? 1.0 : -1.0; }
    private double scale() { return truth.scale() * CORRECT_TICKS_PER_MM / ticksPerMm; }

    public void update() {
        SimClock.touch();
        if (!truth.live()) return;
        double[] now = truth.pose();
        double dx = now[0] - lastTruth[0], dy = now[1] - lastTruth[1];
        double c = Math.cos(lastTruth[2]), s = Math.sin(lastTruth[2]);
        // motion since the last read, expressed in the robot frame at the last read
        double fwd = (dx * c + dy * s) * sx() * scale();
        double left = (-dx * s + dy * c) * sy() * scale();
        double dh = (now[2] - lastTruth[2]) * yawScalar;
        lastTruth = now;
        double t = SimClock.now();
        loopTime = (int) Math.round((t - lastUpdate) * 1e6);
        lastUpdate = t;
        if (getDeviceStatus() != DeviceStatus.READY) return;
        double ch = Math.cos(ph), sh = Math.sin(ph);
        px += fwd * ch - left * sh;
        py += fwd * sh + left * ch;
        ph += dh;
        double[] bv = truth.bodyVel();
        double bf = bv[0] * sx() * scale(), bl = bv[1] * sy() * scale();
        ch = Math.cos(ph); sh = Math.sin(ph);
        vx = bf * ch - bl * sh;
        vy = bf * sh + bl * ch;
        vh = bv[2] * yawScalar;
    }

    public void update(ReadData data) { update(); }

    public void setOffsets(double xOffset, double yOffset, DistanceUnit unit) {
        xOffsetMm = unit.toMm(xOffset);
        yOffsetMm = unit.toMm(yOffset);
    }

    public void recalibrateIMU() {
        readyAt = SimClock.now() + CALIBRATION_SECONDS;
    }

    public void resetPosAndIMU() {
        SimClock.touch();
        px = 0; py = 0; ph = 0; vx = 0; vy = 0; vh = 0;
        lastTruth = truth.pose();
        readyAt = SimClock.now() + CALIBRATION_SECONDS;
    }

    public void setEncoderDirections(EncoderDirection xEncoder, EncoderDirection yEncoder) {
        xDir = xEncoder;
        yDir = yEncoder;
    }

    public void setEncoderResolution(GoBildaOdometryPods pods) {
        ticksPerMm = pods == GoBildaOdometryPods.goBILDA_SWINGARM_POD ? 13.26291192 : 19.89436789;
    }

    public void setEncoderResolution(double ticksPerUnit, DistanceUnit unit) {
        ticksPerMm = ticksPerUnit / unit.toMm(1.0);
    }

    public void setYawScalar(double yawScalar) { this.yawScalar = yawScalar; }

    public void setPosition(Pose2D pos) {
        px = pos.getX(DistanceUnit.INCH);
        py = pos.getY(DistanceUnit.INCH);
        ph = pos.getHeading(AngleUnit.RADIANS);
    }

    public void setPosX(double posX, DistanceUnit unit) { px = unit.toInches(posX); }
    public void setPosY(double posY, DistanceUnit unit) { py = unit.toInches(posY); }
    public void setHeading(double heading, AngleUnit unit) { ph = unit.toRadians(heading); }

    public int getDeviceID() { return 1; }
    public int getDeviceVersion() { return 2; }
    public float getYawScalar() { return (float) yawScalar; }

    public DeviceStatus getDeviceStatus() {
        return SimClock.now() >= readyAt ? DeviceStatus.READY : DeviceStatus.CALIBRATING;
    }

    public int getLoopTime() { return loopTime; }
    public double getFrequency() { return loopTime > 0 ? 1e6 / loopTime : 0; }
    public int getEncoderX() { return (int) Math.round(px * 25.4 * ticksPerMm); }
    public int getEncoderY() { return (int) Math.round(py * 25.4 * ticksPerMm); }
    public double getPosX(DistanceUnit unit) { return unit.fromInches(px); }
    public double getPosY(DistanceUnit unit) { return unit.fromInches(py); }
    public double getHeading(AngleUnit unit) { return unit.fromRadians(ph); }
    public double getHeading(UnnormalizedAngleUnit unit) { return unit.fromRadians(ph); }
    public double getVelX(DistanceUnit unit) { return unit.fromInches(vx); }
    public double getVelY(DistanceUnit unit) { return unit.fromInches(vy); }
    public double getHeadingVelocity(UnnormalizedAngleUnit unit) { return unit.fromRadians(vh); }
    public float getXOffset(DistanceUnit unit) { return (float) unit.fromMm(xOffsetMm); }
    public float getYOffset(DistanceUnit unit) { return (float) unit.fromMm(yOffsetMm); }

    public Pose2D getPosition() {
        return new Pose2D(DistanceUnit.INCH, px, py, AngleUnit.RADIANS, AngleUnit.normalizeRadians(ph));
    }

    public double getPitch(AngleUnit unit) { return 0; }
    public double getRoll(AngleUnit unit) { return 0; }

    @Override public Manufacturer getManufacturer() { return Manufacturer.GoBilda; }
    @Override public String getDeviceName() { return "goBILDA Pinpoint Odometry Computer"; }
}
