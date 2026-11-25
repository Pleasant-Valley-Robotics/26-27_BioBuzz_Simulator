package sim.hw;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.MotorControlAlgorithm;
import com.qualcomm.robotcore.hardware.PIDFCoefficients;

/**
 * One simulated motor: the configuration and commands the OpMode set (user frame) plus the
 * physical state the world integrates (physical frame: positive = the mechanism's forward).
 */
public final class MotorSim {
    public enum Kind { DRIVE, FLYWHEEL, ROLLER }

    /** goBILDA Yellow Jacket: 28-count encoder on a 6000 RPM motor, so 2800 ticks/s free at any ratio. */
    public static final double FREE_TPS = 2800.0;

    public final String name;
    public final Kind kind;
    /** +1 if FORWARD power drives the mechanism forward on Radical's robot, -1 if it is mirrored. */
    public final double mount;
    public final double ticksPerRev;

    // configuration and commands, as the OpMode sees them
    public DcMotorSimple.Direction direction;
    public DcMotor.RunMode mode;
    public DcMotor.ZeroPowerBehavior zpb;
    public double power;
    public double velTarget;
    public boolean velCmd;
    public PIDFCoefficients pidf;
    public boolean enabled;
    public int targetPosition;
    public int tolerance;

    // physical state
    public double tps;
    public double pos;
    public double encZero;
    public double measTps;
    public boolean newSample;
    public double integ, lastErr, ctrlU;
    private double samplePos, sampleT;

    public MotorSim(String name, Kind kind, double mount, double ticksPerRev) {
        this.name = name;
        this.kind = kind;
        this.mount = mount;
        this.ticksPerRev = ticksPerRev;
        resetConfig();
    }

    /** What the SDK does to a motor at the start of every OpMode. */
    public void resetConfig() {
        direction = DcMotorSimple.Direction.FORWARD;
        mode = DcMotor.RunMode.RUN_WITHOUT_ENCODER;
        zpb = DcMotor.ZeroPowerBehavior.BRAKE;
        pidf = new PIDFCoefficients(10, 3, 0, 0, MotorControlAlgorithm.LegacyPID);
        enabled = true;
        targetPosition = 0;
        tolerance = 5;
        stopCommands();
    }

    public void stopCommands() {
        power = 0;
        velTarget = 0;
        velCmd = false;
        integ = 0;
        lastErr = 0;
        ctrlU = 0;
    }

    public void resetPhysical() {
        tps = 0; pos = 0; encZero = 0; measTps = 0; samplePos = 0; sampleT = 0; newSample = false;
    }

    public double sign() {
        return mount * (direction == DcMotorSimple.Direction.REVERSE ? -1.0 : 1.0);
    }

    public double physPower() {
        return enabled ? Math.max(-1, Math.min(1, power)) * sign() : 0;
    }

    public double physVelTarget() { return velTarget * sign(); }

    /** Advance the shaft and the hub's velocity measurement (counts in a 50 ms window: 20 ticks/s steps). */
    public void integrate(double dt) {
        pos += tps * dt;
        sampleT += dt;
        if (sampleT >= 0.05 - 1e-9) {
            double counts = Math.floor(pos) - Math.floor(samplePos);
            measTps = counts / sampleT;
            samplePos = pos;
            sampleT = 0;
            newSample = true;
        }
    }

    /**
     * REV hub velocity loop: power = (P*e + I*sum(e) + D*de + F*target) / 32767, updated on each
     * new velocity sample. Returns the power in the physical frame.
     */
    public double velocityLoop(double targetPhys) {
        if (newSample) {
            newSample = false;
            double e = targetPhys - measTps;
            integ += e;
            if (pidf.i != 0) {
                double lim = 32767.0 / Math.abs(pidf.i);
                integ = Math.max(-lim, Math.min(lim, integ));
            }
            double de = e - lastErr;
            lastErr = e;
            ctrlU = (pidf.p * e + pidf.i * integ + pidf.d * de + pidf.f * targetPhys) / 32767.0;
            ctrlU = Math.max(-1, Math.min(1, ctrlU));
        }
        return ctrlU;
    }
}
