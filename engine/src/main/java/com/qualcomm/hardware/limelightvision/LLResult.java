package com.qualcomm.hardware.limelightvision;

/**
 * Stand-in result. The simulator has no camera image; the only thing it fills in is the Python output of
 * Radical's AprilTag cluster pipeline for the alliance HIVE (see Limelight3A).
 */
public class LLResult {
    private final double[] py;
    public LLResult() { this(null); }
    public LLResult(double[] py) { this.py = py; }
    public boolean isValid() { return py != null && py[0] >= 0; }
    public double getTx() { return 0; }
    public double getTy() { return 0; }
    public double getTa() { return 0; }
    public double getTxNC() { return 0; }
    public double getTyNC() { return 0; }
    public long getStaleness() { return py != null ? 0 : Long.MAX_VALUE; }
    public int getPipelineIndex() { return 0; }
    public double[] getPythonOutput() { return py != null ? py.clone() : new double[] {-1, 0, 0, 0, 0, 0, 0, 0}; }
}
