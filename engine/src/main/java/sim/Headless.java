package sim;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * ./sim test <OpMode> [--runs N] [--code <branch or commit>] [--seed S] [--teleop <OpMode>] [--verbose]
 * Runs an OpMode with no browser, faster than real time, and prints what happened.
 */
public final class Headless {
    private Headless() {}

    private static String git(Path repo, String... args) throws Exception {
        List<String> cmd = new ArrayList<>(List.of("git", "-C", repo.toString()));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (p.waitFor() != 0) throw new IllegalStateException("git " + String.join(" ", args) + ": " + out);
        return out;
    }

    /** A read-only snapshot of another branch, extracted with "git archive" into .cache. The clone is not touched. */
    static Path snapshot(Path clone, Path cacheDir, String ref) throws Exception {
        if (!ref.matches("[A-Za-z0-9_./@^~-]+")) throw new IllegalArgumentException("odd branch name: " + ref);
        String sha = git(clone, "rev-parse", "--short", ref + "^{commit}");
        Path dest = cacheDir.resolve("src").resolve(ref.replaceAll("[^A-Za-z0-9_.-]", "_") + "-" + sha);
        Path src = dest.resolve("TeamCode/src/main/java");
        if (Files.isDirectory(src)) return src;
        Files.createDirectories(dest);
        ProcessBuilder a = new ProcessBuilder("git", "-C", clone.toString(), "archive", ref, "TeamCode/src/main/java");
        ProcessBuilder b = new ProcessBuilder("tar", "-x", "-C", dest.toString());
        a.redirectError(ProcessBuilder.Redirect.INHERIT);
        b.redirectError(ProcessBuilder.Redirect.INHERIT);
        List<Process> ps = ProcessBuilder.startPipeline(List.of(a, b));
        for (Process p : ps) if (p.waitFor() != 0) throw new IllegalStateException("git archive failed for " + ref);
        return src;
    }

    public static int run(Path simDir, Path clone, String[] args) throws Exception {
        String opName = null, code = null, teleop = null;
        int runs = 5;
        long seed = 1000;
        boolean verbose = false;
        double traceEvery = 0;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--runs": runs = Integer.parseInt(args[++i]); break;
                case "--code": code = args[++i]; break;
                case "--seed": seed = Long.parseLong(args[++i]); break;
                case "--teleop": teleop = args[++i]; break;
                case "--verbose": verbose = true; break;
                case "--trace": traceEvery = Double.parseDouble(args[++i]); break;
                default: if (opName == null) opName = args[i]; break;
            }
        }
        Engine engine = new Engine(simDir);
        engine.trace = traceEvery;
        String branch = git(clone, "rev-parse", "--abbrev-ref", "HEAD");
        Path src;
        String label;
        if (code == null || code.equals(branch) || code.equals("working")) {
            src = clone.resolve("TeamCode/src/main/java");
            label = branch + " (working tree)";
        } else {
            src = snapshot(clone, engine.cacheDir, code);
            label = code + " (snapshot)";
        }
        engine.team = TeamCode.compile(src, code == null || code.equals(branch) || code.equals("working") ? clone : null, engine.cacheDir, label);
        TeamCode tc = engine.team;
        if (!tc.ok) {
            System.out.println("COMPILE FAILED for " + label);
            for (TeamCode.Err e : tc.errors) System.out.println("  " + e.file + ":" + e.line + "  " + e.msg);
            return 2;
        }
        if (opName == null || opName.equals("list")) {
            System.out.println("OpModes in " + label + ":");
            for (TeamCode.OpInfo o : tc.opModes) System.out.printf("  %-7s %s%n", o.flavor, o.name);
            return 0;
        }
        if (tc.find(opName) == null) {
            System.out.println("No OpMode named \"" + opName + "\" in " + label + ". Try: ./sim test list");
            return 2;
        }
        opName = tc.find(opName).name;
        System.out.printf("%s on %s, %d run(s), seeds %d..%d%n", opName, label, runs, seed + 1, seed + runs);
        JsonArray all = new JsonArray();
        double sumScore = 0, sumTips = 0, sumErr = 0, sumEnd = 0;
        int fired = 0, scored = 0, leave = 0, park = 0, cut = 0, crashed = 0, errN = 0, endN = 0, collected = 0;
        Map<String, Integer> why = new TreeMap<>();
        for (int i = 1; i <= runs; i++) {
            JsonObject rep = engine.runHeadless(opName, seed + i, teleop);
            JsonObject d = rep.getAsJsonObject("data");
            d.addProperty("seed", seed + i);
            all.add(d);
            JsonObject sc = d.getAsJsonObject("score");
            sumScore += sc.get("total").getAsInt();
            sumTips += d.get("tips").getAsInt();
            fired += d.get("fired").getAsInt();
            scored += d.get("scored").getAsInt();
            collected += d.get("collected").getAsInt();
            if (d.get("leave").getAsBoolean()) leave++;
            if (d.get("parkAuto").getAsBoolean()) park++;
            if (d.get("cutOff").getAsBoolean()) cut++;
            if (d.has("error")) crashed++;
            if (d.has("targetErrIn")) { sumErr += d.get("targetErrIn").getAsDouble(); errN++; }
            if (d.get("opEnded").getAsDouble() >= 0) { sumEnd += d.get("opEnded").getAsDouble(); endN++; }
            JsonObject w = d.getAsJsonObject("missWhy");
            for (String k : w.keySet()) why.merge(k, w.get(k).getAsInt(), Integer::sum);
            System.out.printf("  run %-2d score %3d (LEAVE %d, PARK %d, TIPS %d, CELL %d) | shots %d: %d in, %d miss %s | picked up %d | %s%n",
                    i, sc.get("total").getAsInt(), sc.get("leave").getAsInt(), sc.get("park").getAsInt(),
                    sc.get("tips").getAsInt(), sc.get("cell").getAsInt(), d.get("fired").getAsInt(),
                    d.get("scored").getAsInt(), d.get("missed").getAsInt(), w.size() == 0 ? "" : w.toString(),
                    d.get("collected").getAsInt(), d.get("endNote").getAsString());
            if (verbose || i == 1) System.out.println(indent(rep.get("text").getAsString()));
        }
        System.out.println("SUMMARY " + opName + " on " + label);
        System.out.printf("  mean score %.1f | mean TIPS %.2f | shots in %d of %d (%.0f%%) | misses %s%n", sumScore / runs,
                sumTips / runs, scored, fired, fired == 0 ? 0 : 100.0 * scored / fired, why);
        System.out.printf("  LEAVE %d/%d | PARK %d/%d | cut off by the 30 s timer %d/%d | crashed %d/%d | POLLEN picked up %.1f per run%n",
                leave, runs, park, runs, cut, runs, crashed, runs, (double) collected / runs);
        if (errN > 0) System.out.printf("  mean distance from the last path target %.1f in%n", sumErr / errN);
        if (endN > 0) System.out.printf("  OpMode finished by itself in %d/%d runs, mean %.1f s%n", endN, runs, sumEnd / endN);
        JsonObject out = new JsonObject();
        out.addProperty("opMode", opName);
        out.addProperty("code", label);
        out.addProperty("commit", tc.commit);
        out.addProperty("runs", runs);
        out.addProperty("meanScore", sumScore / runs);
        out.addProperty("shotPct", fired == 0 ? 0 : 100.0 * scored / fired);
        out.add("knobs", engine.knobs.toJson());
        out.add("results", all);
        Path dir = simDir.resolve("reports");
        Files.createDirectories(dir);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path file = dir.resolve(opName + "-" + label.replaceAll("[^A-Za-z0-9_.-]+", "_") + "-" + stamp + ".json");
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(out) + "\n", StandardCharsets.UTF_8);
        System.out.println("  saved " + simDir.relativize(file));
        return 0;
    }

    private static String indent(String s) {
        StringBuilder sb = new StringBuilder();
        for (String l : s.split("\n")) sb.append("      ").append(l).append('\n');
        return sb.toString().replaceAll("\n$", "");
    }
}
