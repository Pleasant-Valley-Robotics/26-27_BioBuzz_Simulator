package com.qualcomm.hardware.lynx;

import com.qualcomm.robotcore.hardware.HardwareDevice;

/** Stand-in for a REV hub. Bulk caching has no effect in the simulator. */
public class LynxModule implements HardwareDevice {
    public enum BulkCachingMode { OFF, MANUAL, AUTO }

    private final String name;
    private final boolean parent;
    private BulkCachingMode mode = BulkCachingMode.OFF;

    public LynxModule(String name, boolean parent) {
        this.name = name;
        this.parent = parent;
    }

    public void setBulkCachingMode(BulkCachingMode mode) { this.mode = mode; }
    public BulkCachingMode getBulkCachingMode() { return mode; }
    public void clearBulkCache() {}
    public boolean isParent() { return parent; }
    public int getModuleAddress() { return parent ? 173 : 2; }
    @Override public Manufacturer getManufacturer() { return Manufacturer.Lynx; }
    @Override public String getDeviceName() { return name; }
}
