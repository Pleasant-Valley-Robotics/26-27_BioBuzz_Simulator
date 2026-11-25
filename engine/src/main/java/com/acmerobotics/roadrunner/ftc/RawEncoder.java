package com.acmerobotics.roadrunner.ftc;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;

public final class RawEncoder implements Encoder {
    private final DcMotorEx m;
    private DcMotorSimple.Direction direction = DcMotorSimple.Direction.FORWARD;

    public RawEncoder(DcMotorEx m) { this.m = m; }

    public DcMotorEx getMotor() { return m; }

    @Override public DcMotorSimple.Direction getDirection() { return direction; }
    @Override public void setDirection(DcMotorSimple.Direction direction) { this.direction = direction; }

    private int applyDirection(int x) {
        if (m.getDirection() == DcMotorSimple.Direction.REVERSE) x = -x;
        if (direction == DcMotorSimple.Direction.REVERSE) x = -x;
        return x;
    }

    @Override public PositionVelocityPair getPositionAndVelocity() {
        int rawPos = m.getCurrentPosition();
        int rawVel = (int) m.getVelocity();
        return new PositionVelocityPair(applyDirection(rawPos), applyDirection(rawVel), rawPos, rawVel);
    }
}
