package com.qualcomm.hardware.limelightvision;

import com.qualcomm.robotcore.hardware.HardwareDevice;

/**
 * Stand-in Limelight. No camera image; while running it reports what Radical's AprilTag cluster pipeline
 * (apriltag_cluster.py) would for the alliance HIVE: {clusterId, numTags, range, bearing, height, elevation,
 * stickerPitch, roll}. ASSUMPTION for the simulator: stickerPitch is the HIVE's tilt in degrees,
 * positive when the back (-x) end is up. Check the sign on the real robot.
 */
public class Limelight3A implements HardwareDevice {
    private boolean running = false;
    private final java.util.function.Supplier<double[]> source;

    public Limelight3A() { this(null); }
    public Limelight3A(java.util.function.Supplier<double[]> source) { this.source = source; }

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
    public LLResult getLatestResult() {
        if (!running || source == null) return null;
        double[] py = source.get();
        return py == null ? null : new LLResult(py);
    }
    public void shutdown() { running = false; }
    @Override public Manufacturer getManufacturer() { return Manufacturer.LimelightVision; }
    @Override public String getDeviceName() { return "Limelight3A"; }
}
