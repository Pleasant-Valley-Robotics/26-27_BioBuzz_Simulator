package com.acmerobotics.roadrunner.ftc;

import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.IMU;
import com.qualcomm.robotcore.hardware.ImuOrientationOnRobot;

public final class LazyHardwareMapImu implements LazyImu {
    private final HardwareMap hardwareMap;
    private final String name;
    private final ImuOrientationOnRobot orientation;
    private IMU imu;

    public LazyHardwareMapImu(HardwareMap hardwareMap, String name, ImuOrientationOnRobot orientation, int timeoutMs) {
        this.hardwareMap = hardwareMap;
        this.name = name;
        this.orientation = orientation;
    }

    public LazyHardwareMapImu(HardwareMap hardwareMap, String name, ImuOrientationOnRobot orientation) {
        this(hardwareMap, name, orientation, 500);
    }

    @Override public synchronized IMU get() {
        if (imu == null) {
            imu = hardwareMap.get(IMU.class, name);
            imu.initialize(new IMU.Parameters(orientation));
        }
        return imu;
    }
}
