package sim;

import com.acmerobotics.dashboard.canvas.Canvas;
import com.acmerobotics.dashboard.telemetry.TelemetryPacket;

/** Where the stand-in SDK classes hand things to the simulator (overlays, dashboard packets, logs). */
public final class SimHooks {
    private SimHooks() {}

    public interface Listener {
        void preview(Canvas canvas);
        void packet(TelemetryPacket packet);
        void record(String channel, Object message);
    }

    public static volatile Listener listener = null;

    public static void preview(Canvas c) {
        Listener l = listener;
        if (l != null && SimClock.isOpThread()) l.preview(c);
    }

    public static void packet(TelemetryPacket p) {
        Listener l = listener;
        if (l != null && SimClock.isOpThread()) l.packet(p);
    }

    public static void record(String channel, Object message) {
        Listener l = listener;
        if (l != null && SimClock.isOpThread()) l.record(channel, message);
    }
}
