package sim.hw;

import com.qualcomm.robotcore.hardware.Servo;

import sim.SimClock;
import sim.World;

/**
 * The "flowerStick" position servo: a hook on the front of the robot that reaches into a FLOWER.
 * Position 0 = stowed, 1 = reached all the way out. See World.stepStick.
 */
public final class StickServoFacade implements Servo {
    private final World w;
    private final RunContext ctx;
    private Direction dir = Direction.FORWARD;
    private double lo = 0, hi = 1, pos = 0;

    public StickServoFacade(World w, RunContext ctx) {
        this.w = w;
        this.ctx = ctx;
    }

    @Override public void setDirection(Direction direction) { dir = direction; }
    @Override public Direction getDirection() { return dir; }

    @Override public void setPosition(double position) {
        SimClock.touch();
        pos = Math.max(0, Math.min(1, position));
        double scaled = lo + (hi - lo) * pos;
        double phys = dir == Direction.REVERSE ? 1 - scaled : scaled;
        synchronized (World.LOCK) { if (ctx.live) w.stickCmd = phys; }
    }

    @Override public double getPosition() { return pos; }
    @Override public void scaleRange(double min, double max) { lo = Math.max(0, Math.min(1, min)); hi = Math.max(0, Math.min(1, max)); }
    @Override public Manufacturer getManufacturer() { return Manufacturer.Lynx; }
    @Override public String getDeviceName() { return "Servo flowerStick"; }
}
