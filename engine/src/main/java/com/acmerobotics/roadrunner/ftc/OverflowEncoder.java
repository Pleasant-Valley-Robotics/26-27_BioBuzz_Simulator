package com.acmerobotics.roadrunner.ftc;

import com.qualcomm.robotcore.hardware.DcMotorSimple;

/** The simulated hub does not overflow its 16-bit velocity, so this simply passes through. */
public final class OverflowEncoder implements Encoder {
    public final RawEncoder encoder;

    public OverflowEncoder(RawEncoder e) { this.encoder = e; }

    @Override public DcMotorSimple.Direction getDirection() { return encoder.getDirection(); }
    @Override public void setDirection(DcMotorSimple.Direction direction) { encoder.setDirection(direction); }
    @Override public PositionVelocityPair getPositionAndVelocity() { return encoder.getPositionAndVelocity(); }
}
