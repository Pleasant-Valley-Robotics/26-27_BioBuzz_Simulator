package com.qualcomm.hardware.limelightvision;

import com.qualcomm.robotcore.hardware.HardwareDevice;

/** Stand-in Limelight: accepts every call and never sees anything (camera emulation is out of scope). */
public class Limelight3A implements HardwareDevice {
    private boolean running = false;

    public boolean pipelineSwitch(int index) { return true; }
    public void start() { running = true; }
    public void pause() { running = false; }
    public void stop() { running = false; }
    public boolean isRunning() { return running; }
    public boolean isConnected() { return true; }
    public void setPollRateHz(int rateHz) {}
    public boolean updatePythonInputs(double[] inputs) { return true; }
    public boolean updatePythonInputs(double a, double b, double c, double d, double e, double f, double g, double h) { return true; }
    public boolean updateRobotOrientation(double yaw) { return true; }
    public boolean reloadPipeline() { return true; }
    public LLResult getLatestResult() { return null; }
    public void shutdown() { running = false; }
    @Override public Manufacturer getManufacturer() { return Manufacturer.LimelightVision; }
    @Override public String getDeviceName() { return "Limelight3A"; }
}
