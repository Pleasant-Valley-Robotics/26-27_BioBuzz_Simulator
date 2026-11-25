package sim.hw;

import com.qualcomm.robotcore.hardware.DcMotorSimple;

/** One simulated continuous-rotation servo. */
public final class ServoSim {
    public final String name;
    public final double mount;
    public DcMotorSimple.Direction direction = DcMotorSimple.Direction.FORWARD;
    public double power;
    public double angle;

    public ServoSim(String name, double mount) {
        this.name = name;
        this.mount = mount;
    }

    public void resetConfig() {
        direction = DcMotorSimple.Direction.FORWARD;
        power = 0;
    }

    public double physPower() {
        return Math.max(-1, Math.min(1, power)) * mount * (direction == DcMotorSimple.Direction.REVERSE ? -1.0 : 1.0);
    }
}
