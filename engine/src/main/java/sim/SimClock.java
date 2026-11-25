package sim;

/**
 * The simulated clock, and the handshake that keeps the team's OpMode thread and the physics
 * thread in lockstep: the world only moves forward while the OpMode thread is parked in a wait
 * (end of a control loop, sleep, idle). That makes runs repeatable and lets headless tests run
 * far faster than real time.
 */
public final class SimClock {
    private SimClock() {}

    private static final Object MON = new Object();
    private static volatile double t = 0.0;
    private static volatile Thread op = null;
    private static boolean parked = false;
    private static double wake = 0.0;
    private static double lastYield = 0.0;
    private static int touches = 0;

    /** Simulated duration of one OpMode control loop (hardware reads and writes), seconds. */
    public static volatile double loopPeriod = 0.015;
    /** Physics tick, seconds. */
    public static final double TICK = 0.005;

    public static double now() { return t; }

    public static void bindOpThread(Thread th) {
        synchronized (MON) {
            op = th;
            parked = false;
            lastYield = t;
            touches = 0;
            MON.notifyAll();
        }
    }

    public static void unbindOpThread(Thread th) {
        synchronized (MON) {
            if (op == th) {
                op = null;
                parked = false;
                MON.notifyAll();
            }
        }
    }

    public static boolean isOpThread() { return Thread.currentThread() == op; }

    /**
     * Called by time reads and hardware calls. A loop that never reaches a real wait point
     * (for example "while (timer.seconds() < 1) {}") still lets simulated time pass.
     */
    public static void touch() {
        if (Thread.currentThread() != op) return;
        if (++touches >= 40) {
            try {
                loopYield();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** End of one control loop: wait until one loop period of simulated time has passed. */
    public static void loopYield() throws InterruptedException {
        if (Thread.currentThread() != op) {
            Thread.sleep(Math.max(1L, (long) (loopPeriod * 1000)));
            return;
        }
        park(Math.max(lastYield + loopPeriod, t + TICK * 0.5));
        lastYield = t;
        touches = 0;
    }

    public static void sleep(double seconds) throws InterruptedException {
        if (Thread.currentThread() != op) {
            Thread.sleep(Math.max(0L, (long) (seconds * 1000)));
            return;
        }
        if (seconds > 0) park(t + seconds);
        lastYield = t;
        touches = 0;
    }

    private static void park(double target) throws InterruptedException {
        synchronized (MON) {
            wake = target;
            parked = true;
            MON.notifyAll();
            try {
                while (t < target) MON.wait();
            } finally {
                parked = false;
            }
        }
    }

    // ---- physics thread side ----

    /** Wait until the OpMode thread is parked (or there is none). False if it is still busy after maxMs. */
    static boolean awaitOpParked(long maxMs) throws InterruptedException {
        long end = System.nanoTime() + maxMs * 1_000_000L;
        synchronized (MON) {
            while (op != null && op.isAlive() && !(parked && t < wake)) {
                long left = end - System.nanoTime();
                if (left <= 0) return false;
                MON.wait(left / 1_000_000L, (int) (left % 1_000_000L));
            }
            return true;
        }
    }

    static void advance(double dt) {
        synchronized (MON) {
            t += dt;
            MON.notifyAll();
        }
    }
}
