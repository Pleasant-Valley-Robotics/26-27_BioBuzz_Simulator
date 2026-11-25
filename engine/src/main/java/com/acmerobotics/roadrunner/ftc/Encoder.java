package com.acmerobotics.roadrunner.ftc;

import com.qualcomm.robotcore.hardware.DcMotorSimple;

public interface Encoder {
    DcMotorSimple.Direction getDirection();
    void setDirection(DcMotorSimple.Direction direction);
    PositionVelocityPair getPositionAndVelocity();
}
