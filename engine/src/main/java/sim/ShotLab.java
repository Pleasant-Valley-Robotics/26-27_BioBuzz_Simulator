package sim;

import java.nio.file.Path;
import java.util.Locale;

/**
 * ./sim shots [--from 22 --to 50 --step 2] [--tps 1150:1400:25] [--n 400] [--y 13.645] [--pose x,y,headingDeg]
 *
 * Shot accuracy table. The robot is parked (not moving) with the flywheel already turning at a
 * given speed, fires one POLLEN at the blue HIVE's starting upward CELL (the back one), and the
 * result is counted. Repeated n times for every distance and speed.
 *
 * "Distance" is from the robot's center to the middle of the CELL opening, measured along the
 * field's audience axis. The robot faces the back wall (heading 180), launcher toward the HIVE.
 * The audience-side CELL is the mirror image, so the same table applies there.
 */
public final class ShotLab {
    private ShotLab() {}

    /** Middle of the upward CELL opening (blue HIVE, back CELL up). */
    public static final double TARGET_X = -(World.CELL_OUT * Math.cos(Math.toRadians(World.TILT))
            - Math.sin(Math.toRadians(World.TILT)) * (World.CELL_FLOOR + World.CELL_PEAK / 2));
    public static final double TARGET_Y = World.BLUE_Y;

    public static int run(Path simDir, String[] args) {
        double from = 22, to = 50, step = 2, y = 13.645;
        double tpsLo = 1150, tpsHi = 1400, tpsStep = 25;
        int n = 400;
        double[] pose = null;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--from": from = Double.parseDouble(args[++i]); break;
                case "--to": to = Double.parseDouble(args[++i]); break;
                case "--step": step = Double.parseDouble(args[++i]); break;
                case "--y": y = Double.parseDouble(args[++i]); break;
                case "--n": n = Integer.parseInt(args[++i]); break;
                case "--tps": {
                    String[] p = args[++i].split(":");
                    tpsLo = Double.parseDouble(p[0]);
                    tpsHi = p.length > 1 ? Double.parseDouble(p[1]) : tpsLo;
                    tpsStep = p.length > 2 ? Double.parseDouble(p[2]) : 25;
                    break;
                }
                case "--pose": {
                    String[] p = args[++i].split(",");
                    pose = new double[] {Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2])};
                    break;
                }
                default: break;
            }
        }
        Knobs knobs = Knobs.load(simDir.resolve("knobs.json"));
        World w = new World(knobs);
        System.out.printf(Locale.US, "Shot lab: %d shots per cell, launch angle %.1f deg, spread %.1f%% speed / %.1f deg up-down / %.1f deg sideways%n",
                n, knobs.launchAngleDeg, knobs.shotSpeedSigmaPct, knobs.shotPitchSigmaDeg, knobs.shotYawSigmaDeg);
        System.out.printf(Locale.US, "Target: middle of the CELL opening at x %.1f, y %.1f. Numbers are %% of shots that score.%n", TARGET_X, TARGET_Y);
        StringBuilder head = new StringBuilder(pose == null ? "  dist   robot x |" : "  pose                    |");
        for (double tps = tpsLo; tps <= tpsHi + 1e-9; tps += tpsStep) head.append(String.format(Locale.US, "%5.0f", tps));
        head.append(" | best");
        System.out.println(head);
        if (pose != null) {
            row(w, n, pose[0], pose[1], Math.toRadians(pose[2]), tpsLo, tpsHi, tpsStep,
                    String.format(Locale.US, "  (%6.1f, %5.1f, %6.1f) |", pose[0], pose[1], pose[2]));
            return 0;
        }
        for (double d = from; d <= to + 1e-9; d += step) {
            double x = TARGET_X - d;
            row(w, n, x, y, Math.PI, tpsLo, tpsHi, tpsStep, String.format(Locale.US, "  %4.1f   %7.1f |", d, x));
        }
        return 0;
    }

    private static void row(World w, int n, double x, double y, double h, double tpsLo, double tpsHi, double tpsStep, String label) {
        StringBuilder sb = new StringBuilder(label);
        double best = -1, bestTps = 0;
        for (double tps = tpsLo; tps <= tpsHi + 1e-9; tps += tpsStep) {
            double pct = 100.0 * hits(w, n, x, y, h, tps) / n;
            sb.append(String.format(Locale.US, "%5.0f", pct));
            if (pct > best) { best = pct; bestTps = tps; }
        }
        sb.append(String.format(Locale.US, " | %.0f%% at %.0f", best, bestTps));
        System.out.println(sb);
    }

    /** How many of n single shots from a parked robot score. */
    public static int hits(World w, int n, double x, double y, double h, double tps) {
        int in = 0;
        for (int i = 1; i <= n; i++) {
            w.reset(5000 + i);
            w.place(x, y, h);
            World.Shot s = w.testShot(tps);
            for (int k = 0; k < 800 && s.result.equals("pending"); k++) w.step(0.005);
            if (s.result.equals("score")) in++;
        }
        return in;
    }
}
