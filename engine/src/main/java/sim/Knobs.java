package sim;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Every number the simulator had to estimate lives here, visible in the page's Knobs drawer and
 * saved to knobs.json. Values marked "estimate" were not measured on the real robot.
 */
public final class Knobs {
    // ---- Launcher ----
    public double launchAngleDeg = 72.0;
    public double calibDistance = 47.3;
    public double exitSpeedScale = 1.0;
    public double flyMaxTps = 2184.0;
    public double flyTau = 1.0;
    public double shotDropPct = 12.0;
    public double feedSecPerBall = 0.45;
    public double shotSpeedSigmaPct = 4.2;
    public double shotPitchSigmaDeg = 2.0;
    public double shotYawSigmaDeg = 2.2;
    public double rimMargin = 0.8;
    // ---- Drive ----
    public double batteryVolts = 12.8;
    public double rueMaxFraction = 0.85;
    public double wheelVarPct = 2.0;
    public double driveKS = 0.8270156386520844;
    public double driveKV = 0.00038686461610924766 / 0.00206362854686156;
    public double driveKA = 0.0001 / 0.00206362854686156;
    public double lateralMult = 0.00206362854686156 / 0.0015802426576599661;
    public double trackWidth = 7064.795951976685 * 0.00206362854686156;
    public double odoScaleErrPct = 0.0;
    public double loopMs = 15.0;
    // ---- Field ----
    public double tipThreshold = 7.9;
    public double ballFriction = 45.0;
    public double dragPerInch = 0.0012;
    public double preload = 4.0;
    public double flowerIntake = 0.0;
    public double seed = 0.0;

    public static final class Meta {
        public final String key, label, group;
        public final double min, max, step;
        Meta(String key, String label, String group, double min, double max, double step) {
            this.key = key; this.label = label; this.group = group; this.min = min; this.max = max; this.step = step;
        }
    }

    public static final List<Meta> META = new ArrayList<>();
    static {
        String L = "Launcher (estimates)", D = "Drive", F = "Field";
        META.add(new Meta("launchAngleDeg", "Launch angle above horizontal (deg)", L, 30, 89, 0.5));
        META.add(new Meta("calibDistance", "Distance the launcher is tuned for at target speed (in)", L, 15, 90, 0.1));
        META.add(new Meta("exitSpeedScale", "Exit speed scale (1 = hits center from tuned distance)", L, 0.7, 1.3, 0.005));
        META.add(new Meta("flyMaxTps", "Launcher top speed at 12.5 V (ticks/s)", L, 1400, 2800, 1));
        META.add(new Meta("flyTau", "Launcher spin-up time constant (s)", L, 0.1, 4, 0.05));
        META.add(new Meta("shotDropPct", "Flywheel speed lost per shot (%)", L, 0, 40, 0.5));
        META.add(new Meta("feedSecPerBall", "Feeder seconds per ball at full power", L, 0.1, 3, 0.05));
        META.add(new Meta("shotSpeedSigmaPct", "Random exit speed spread, 1 sigma (%)", L, 0, 10, 0.1));
        META.add(new Meta("shotPitchSigmaDeg", "Random launch angle spread, 1 sigma (deg)", L, 0, 6, 0.1));
        META.add(new Meta("shotYawSigmaDeg", "Random sideways spread, 1 sigma (deg)", L, 0, 8, 0.1));
        META.add(new Meta("rimMargin", "CELL rim margin (in)", L, 0, 3, 0.1));
        META.add(new Meta("batteryVolts", "Battery voltage (V)", D, 10.5, 14, 0.1));
        META.add(new Meta("rueMaxFraction", "RUN_USING_ENCODER: speed at power 1.0 (fraction of free speed)", D, 0.5, 1, 0.01));
        META.add(new Meta("wheelVarPct", "Random per-wheel strength variation (%)", D, 0, 10, 0.5));
        META.add(new Meta("driveKS", "True drive static friction (V)", D, 0, 2, 0.01));
        META.add(new Meta("driveKV", "True drive kV (V per in/s)", D, 0.1, 0.3, 0.001));
        META.add(new Meta("driveKA", "True drive kA (V per in/s^2)", D, 0.005, 0.2, 0.001));
        META.add(new Meta("lateralMult", "True strafe ratio (wheel in per strafe in)", D, 1, 1.8, 0.005));
        META.add(new Meta("trackWidth", "True effective track width (in)", D, 8, 22, 0.05));
        META.add(new Meta("odoScaleErrPct", "Odometry scale error (%)", D, -5, 5, 0.1));
        META.add(new Meta("loopMs", "OpMode loop time (ms)", D, 5, 60, 1));
        META.add(new Meta("tipThreshold", "HIVE tips at this load (POLLEN = 1, NECTAR = 1.67)", F, 3, 12, 0.1));
        META.add(new Meta("ballFriction", "Ball rolling friction (in/s^2)", F, 5, 200, 1));
        META.add(new Meta("dragPerInch", "Ball air drag (1/in)", F, 0, 0.006, 0.0001));
        META.add(new Meta("preload", "POLLEN preloaded in the robot", F, 0, 4, 1));
        META.add(new Meta("flowerIntake", "Intake can take POLLEN straight out of a FLOWER (1 = yes, 0 = needs the flowerStick)", F, 0, 1, 1));
        META.add(new Meta("seed", "Random seed (0 = new every reset)", F, 0, 99999, 1));
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public double get(String key) {
        try {
            return getClass().getField(key).getDouble(this);
        } catch (ReflectiveOperationException e) {
            return Double.NaN;
        }
    }

    public boolean set(String key, double value) {
        try {
            Field f = getClass().getField(key);
            if (Double.isNaN(value) || Double.isInfinite(value)) return false;
            for (Meta m : META) {
                if (m.key.equals(key)) value = Math.max(m.min, Math.min(m.max, value));
            }
            f.setDouble(this, value);
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    public JsonObject toJson() { return GSON.toJsonTree(this).getAsJsonObject(); }

    public void apply(JsonObject o) {
        for (String k : o.keySet()) {
            JsonElement e = o.get(k);
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) set(k, e.getAsDouble());
        }
    }

    public static Knobs load(Path file) {
        Knobs k = new Knobs();
        try {
            if (Files.exists(file)) {
                k.apply(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
            }
        } catch (Exception e) {
            System.err.println("knobs.json ignored: " + e.getMessage());
        }
        return k;
    }

    public void save(Path file) {
        try {
            Files.writeString(file, GSON.toJson(this) + "\n", StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("could not save knobs: " + e.getMessage());
        }
    }
}
