package com.acmerobotics.roadrunner.ftc;

import sim.SimHooks;

public final class DownsampledWriter {
    private final String channel;
    private final long maxPeriod;

    public DownsampledWriter(String channel, long maxPeriod) {
        this.channel = channel;
        this.maxPeriod = maxPeriod;
    }

    public String getChannel() { return channel; }
    public long getMaxPeriod() { return maxPeriod; }

    public void write(Object msg) { SimHooks.record(channel, msg); }
}
