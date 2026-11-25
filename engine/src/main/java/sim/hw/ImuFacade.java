package sim.hw;

import com.qualcomm.robotcore.hardware.IMU;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.AngularVelocity;
import org.firstinspires.ftc.robotcore.external.navigation.YawPitchRollAngles;

import sim.SimClock;
import sim.World;

/** Hub IMU: yaw is the true robot heading relative to the last resetYaw(). */
public final class ImuFacade implements IMU {
    private final World world;
    private double yawZero;

    public ImuFacade(World world) {
        this.world = world;
        this.yawZero = world.rh;
    }

    @Override public boolean initialize(Parameters parameters) { return true; }

    @Override public void resetYaw() {
        synchronized (World.LOCK) { yawZero = world.rh; }
    }

    @Override public YawPitchRollAngles getRobotYawPitchRollAngles() {
        SimClock.touch();
        synchronized (World.LOCK) {
            double yaw = AngleUnit.normalizeRadians(world.rh - yawZero);
            return new YawPitchRollAngles(AngleUnit.RADIANS, yaw, 0, 0, System.nanoTime());
        }
    }

    @Override public AngularVelocity getRobotAngularVelocity(AngleUnit angleUnit) {
        SimClock.touch();
        synchronized (World.LOCK) {
            double w = angleUnit == AngleUnit.RADIANS ? world.bw : Math.toDegrees(world.bw);
            return new AngularVelocity(angleUnit, 0f, 0f, (float) w, System.nanoTime());
        }
    }

    @Override public Manufacturer getManufacturer() { return Manufacturer.Lynx; }
    @Override public String getDeviceName() { return "IMU"; }
}
