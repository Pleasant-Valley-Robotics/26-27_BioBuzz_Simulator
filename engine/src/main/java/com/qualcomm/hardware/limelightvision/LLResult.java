package com.qualcomm.hardware.limelightvision;

/** Stand-in: the simulator has no camera, so a result is never valid. */
public class LLResult {
    public boolean isValid() { return false; }
    public double getTx() { return 0; }
    public double getTy() { return 0; }
    public double getTa() { return 0; }
    public double getTxNC() { return 0; }
    public double getTyNC() { return 0; }
    public long getStaleness() { return Long.MAX_VALUE; }
    public int getPipelineIndex() { return 0; }
    public double[] getPythonOutput() { return new double[] {-1, 0, 0, 0, 0, 0, 0, 0}; }
}
