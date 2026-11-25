package sim.hw;

/** One OpMode run. When the run ends, its hardware handles go dead (like the SDK after STOP). */
public final class RunContext {
    public volatile boolean live = true;
}
