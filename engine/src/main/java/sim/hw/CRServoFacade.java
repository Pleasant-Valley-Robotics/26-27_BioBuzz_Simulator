package sim.hw;

import com.qualcomm.robotcore.hardware.CRServo;

import sim.SimClock;
import sim.World;

/** The CRServo an OpMode gets from the hardware map. */
public final class CRServoFacade implements CRServo {
    private final ServoSim s;
    private final RunContext ctx;

    public CRServoFacade(ServoSim s, RunContext ctx) {
        this.s = s;
        this.ctx = ctx;
    }

    @Override public void setDirection(Direction direction) {
        SimClock.touch();
        synchronized (World.LOCK) { if (ctx.live) s.direction = direction; }
    }

    @Override public Direction getDirection() { return s.direction; }

    @Override public void setPower(double power) {
        SimClock.touch();
        synchronized (World.LOCK) { if (ctx.live) s.power = Math.max(-1.0, Math.min(1.0, power)); }
    }

    @Override public double getPower() { return s.power; }
    @Override public Manufacturer getManufacturer() { return Manufacturer.Lynx; }
    @Override public String getDeviceName() { return "Continuous Rotation Servo " + s.name; }
}
