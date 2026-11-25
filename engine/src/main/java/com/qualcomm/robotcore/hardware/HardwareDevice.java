package com.qualcomm.robotcore.hardware;

public interface HardwareDevice {
    enum Manufacturer { Unknown, Other, Lego, HiTechnic, ModernRobotics, Adafruit, Matrix, Lynx, AMS, STMicroelectronics, Broadcom, DFRobot, SparkFun, LimelightVision, GoBilda }

    default Manufacturer getManufacturer() { return Manufacturer.Other; }
    default String getDeviceName() { return getClass().getSimpleName(); }
    default String getConnectionInfo() { return "simulated"; }
    default int getVersion() { return 1; }
    default void resetDeviceConfigurationForOpMode() {}
    default void close() {}
}
