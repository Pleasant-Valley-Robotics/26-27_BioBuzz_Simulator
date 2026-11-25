package sim.hw;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.PIDFCoefficients;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.CurrentUnit;

import sim.SimClock;
import sim.World;

/** The DcMotorEx an OpMode gets from the hardware map. */
public final class MotorFacade implements DcMotorEx {
    private final MotorSim m;
    private final RunContext ctx;

    public MotorFacade(MotorSim m, RunContext ctx) {
        this.m = m;
        this.ctx = ctx;
    }

    @Override public void setDirection(Direction direction) {
        SimClock.touch();
        synchronized (World.LOCK) { if (ctx.live) m.direction = direction; }
    }

    @Override public Direction getDirection() { return m.direction; }

    @Override public void setPower(double power) {
        SimClock.touch();
        synchronized (World.LOCK) {
            if (!ctx.live) return;
            m.power = Math.max(-1.0, Math.min(1.0, power));
            m.velCmd = false;
        }
    }

    @Override public double getPower() {
        SimClock.touch();
        synchronized (World.LOCK) {
            if (m.velCmd) return Math.max(-1, Math.min(1, m.velTarget / (MotorSim.FREE_TPS * 0.85)));
            return m.power;
        }
    }

    @Override public void setZeroPowerBehavior(ZeroPowerBehavior zeroPowerBehavior) {
        SimClock.touch();
        synchronized (World.LOCK) { if (ctx.live) m.zpb = zeroPowerBehavior; }
    }

    @Override public ZeroPowerBehavior getZeroPowerBehavior() { return m.zpb; }

    @Override public void setTargetPosition(int position) {
        synchronized (World.LOCK) { if (ctx.live) m.targetPosition = position; }
    }

    @Override public int getTargetPosition() { return m.targetPosition; }
    @Override public boolean isBusy() { return false; }

    @Override public int getCurrentPosition() {
        SimClock.touch();
        synchronized (World.LOCK) { return (int) Math.round((m.pos - m.encZero) * m.sign()); }
    }

    @Override public void setMode(RunMode mode) {
        SimClock.touch();
        synchronized (World.LOCK) {
            if (!ctx.live) return;
            if (mode == RunMode.STOP_AND_RESET_ENCODER) m.encZero = m.pos;
            if (mode != m.mode) { m.integ = 0; m.lastErr = 0; m.ctrlU = 0; }
            m.mode = mode;
        }
    }

    @Override public RunMode getMode() { return m.mode; }

    @Override public void setMotorEnable() { synchronized (World.LOCK) { if (ctx.live) m.enabled = true; } }
    @Override public void setMotorDisable() { synchronized (World.LOCK) { if (ctx.live) m.enabled = false; } }
    @Override public boolean isMotorEnabled() { return m.enabled; }

    @Override public void setVelocity(double angularRate) {
        SimClock.touch();
        synchronized (World.LOCK) {
            if (!ctx.live) return;
            if (m.mode != RunMode.RUN_USING_ENCODER && m.mode != RunMode.RUN_TO_POSITION) {
                m.mode = RunMode.RUN_USING_ENCODER;
            }
            m.velTarget = angularRate;
            m.velCmd = true;
        }
    }

    @Override public void setVelocity(double angularRate, AngleUnit unit) {
        double revPerSec = unit == AngleUnit.RADIANS ? angularRate / (2 * Math.PI) : angularRate / 360.0;
        setVelocity(revPerSec * m.ticksPerRev);
    }

    @Override public double getVelocity() {
        SimClock.touch();
        synchronized (World.LOCK) { return m.measTps * m.sign(); }
    }

    @Override public double getVelocity(AngleUnit unit) {
        double revPerSec = getVelocity() / m.ticksPerRev;
        return unit == AngleUnit.RADIANS ? revPerSec * 2 * Math.PI : revPerSec * 360.0;
    }

    @Override public void setPIDFCoefficients(RunMode mode, PIDFCoefficients pidfCoefficients) {
        SimClock.touch();
        synchronized (World.LOCK) {
            if (!ctx.live) return;
            if (mode == RunMode.RUN_USING_ENCODER) m.pidf = new PIDFCoefficients(pidfCoefficients);
        }
    }

    @Override public void setVelocityPIDFCoefficients(double p, double i, double d, double f) {
        setPIDFCoefficients(RunMode.RUN_USING_ENCODER, new PIDFCoefficients(p, i, d, f));
    }

    @Override public void setPositionPIDFCoefficients(double p) {}

    @Override public PIDFCoefficients getPIDFCoefficients(RunMode mode) {
        synchronized (World.LOCK) { return new PIDFCoefficients(m.pidf); }
    }

    @Override public void setTargetPositionTolerance(int tolerance) { m.tolerance = tolerance; }
    @Override public int getTargetPositionTolerance() { return m.tolerance; }

    @Override public double getCurrent(CurrentUnit unit) {
        double amps = 0.3 + 8.0 * Math.abs(m.ctrlU != 0 ? m.ctrlU : m.power);
        return unit == CurrentUnit.AMPS ? amps : amps * 1000.0;
    }

    @Override public double getCurrentAlert(CurrentUnit unit) { return unit == CurrentUnit.AMPS ? 9.2 : 9200; }
    @Override public void setCurrentAlert(double current, CurrentUnit unit) {}
    @Override public boolean isOverCurrent() { return false; }
    @Override public Manufacturer getManufacturer() { return Manufacturer.Lynx; }
    @Override public String getDeviceName() { return "DC Motor " + m.name; }
}
