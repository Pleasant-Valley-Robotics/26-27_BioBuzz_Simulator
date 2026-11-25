package com.qualcomm.robotcore.hardware;

public interface CRServo extends DcMotorSimple {
    default int getPortNumber() { return 0; }
}
