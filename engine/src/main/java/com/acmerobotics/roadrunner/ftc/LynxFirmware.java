package com.acmerobotics.roadrunner.ftc;

import com.qualcomm.robotcore.hardware.HardwareMap;

public final class LynxFirmware {
    private LynxFirmware() {}

    /** The simulated hubs always have current firmware. */
    public static void throwIfModulesAreOutdated(HardwareMap hardwareMap) {}
}
