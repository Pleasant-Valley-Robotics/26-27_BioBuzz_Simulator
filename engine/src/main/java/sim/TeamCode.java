package sim;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/**
 * The team's code: compiled straight from the clone, untouched, against the stand-in SDK.
 * Nothing is ever written into the clone; class files go to biobuzz-sim/.cache.
 */
public final class TeamCode {
    public static final class OpInfo {
        public String name, group, flavor, className;
        public double[] startPose;
    }

    public static final class Err {
        public String file, msg;
        public long line;
    }

    /** Files that only matter for drivetrains and tuning tools Radical's competition OpModes do not use. */
    private static final List<String> SKIP = List.of(
            "TankDrive.java", "OTOSLocalizer.java", "ThreeDeadWheelLocalizer.java", "TwoDeadWheelLocalizer.java");

    public final Path sourceRoot;
    public final Path repoDir;
    public final String label;
    public boolean ok;
    public final List<Err> errors = new ArrayList<>();
    public final List<OpInfo> opModes = new ArrayList<>();
    public Path classesDir;
    public String compiledAt = "", branch = "", commit = "";
    public boolean dirty;

    private TeamCode(Path sourceRoot, Path repoDir, String label) {
        this.sourceRoot = sourceRoot;
        this.repoDir = repoDir;
        this.label = label;
    }

    public static TeamCode compile(Path sourceRoot, Path repoDir, Path cacheDir, String label) {
        TeamCode tc = new TeamCode(sourceRoot, repoDir, label);
        tc.compiledAt = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        tc.gitInfo();
        try {
            tc.doCompile(cacheDir);
        } catch (Exception e) {
            tc.ok = false;
            Err err = new Err();
            err.file = "";
            err.msg = "Could not compile: " + e;
            tc.errors.add(err);
        }
        return tc;
    }

    private void doCompile(Path cacheDir) throws IOException {
        if (!Files.isDirectory(sourceRoot)) throw new IOException("no source folder at " + sourceRoot);
        List<Path> files;
        try (Stream<Path> s = Files.walk(sourceRoot)) {
            files = s.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/tuning/"))
                    .filter(p -> !SKIP.contains(p.getFileName().toString()))
                    .sorted().collect(Collectors.toList());
        }
        classesDir = cacheDir.resolve("classes-" + System.nanoTime());
        Files.createDirectories(classesDir);
        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        if (jc == null) throw new IOException("this Java runtime has no compiler (a JDK is required)");
        DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
        boolean success;
        try (StandardJavaFileManager fm = jc.getStandardFileManager(diags, Locale.US, StandardCharsets.UTF_8)) {
            List<String> opts = List.of("-d", classesDir.toString(), "-cp", System.getProperty("java.class.path"),
                    "-proc:none", "-nowarn", "-Xlint:none", "-encoding", "UTF-8", "-g");
            success = jc.getTask(null, fm, diags, opts, null, fm.getJavaFileObjectsFromPaths(files)).call();
        }
        for (Diagnostic<? extends JavaFileObject> d : diags.getDiagnostics()) {
            if (d.getKind() != Diagnostic.Kind.ERROR) continue;
            Err err = new Err();
            err.file = d.getSource() == null ? "" : sourceRoot.relativize(Path.of(d.getSource().toUri())).toString();
            err.line = d.getLineNumber();
            err.msg = d.getMessage(Locale.US);
            errors.add(err);
        }
        ok = success;
        if (ok) discover();
    }

    private void discover() throws IOException {
        List<String> names;
        try (Stream<Path> s = Files.walk(classesDir)) {
            names = s.filter(p -> p.toString().endsWith(".class"))
                    .map(p -> classesDir.relativize(p).toString().replace('/', '.').replace('\\', '.'))
                    .map(n -> n.substring(0, n.length() - 6))
                    .filter(n -> !n.contains("$")).sorted().collect(Collectors.toList());
        }
        try (URLClassLoader cl = newLoader()) {
            for (String n : names) {
                Class<?> k;
                try {
                    k = Class.forName(n, false, cl);
                } catch (Throwable t) {
                    continue;
                }
                if (!OpMode.class.isAssignableFrom(k) || Modifier.isAbstract(k.getModifiers())) continue;
                if (k.isAnnotationPresent(Disabled.class)) continue;
                TeleOp tele = k.getAnnotation(TeleOp.class);
                Autonomous auto = k.getAnnotation(Autonomous.class);
                if (tele == null && auto == null) continue;
                OpInfo info = new OpInfo();
                info.className = n;
                info.flavor = auto != null ? "AUTO" : "TELEOP";
                String nm = auto != null ? auto.name() : tele.name();
                info.name = nm.isEmpty() ? k.getSimpleName() : nm;
                info.group = auto != null ? auto.group() : tele.group();
                info.startPose = findStartPose(sourceRoot.resolve(n.replace('.', '/') + ".java"));
                opModes.add(info);
            }
        }
        opModes.sort(Comparator.comparing((OpInfo o) -> o.flavor).thenComparing(o -> o.name));
    }

    private URLClassLoader newLoader() throws IOException {
        return new URLClassLoader(new URL[] {classesDir.toUri().toURL()}, TeamCode.class.getClassLoader());
    }

    /** A fresh class loader per run, so static fields (Robot.launcher and friends) start clean, as after a robot restart. */
    public OpMode newInstance(OpInfo info) throws Exception {
        URLClassLoader cl = newLoader();
        return (OpMode) cl.loadClass(info.className).getDeclaredConstructor().newInstance();
    }

    public OpInfo find(String name) {
        for (OpInfo o : opModes) if (o.name.equals(name)) return o;
        for (OpInfo o : opModes) if (o.name.equalsIgnoreCase(name) || o.className.endsWith("." + name)) return o;
        return null;
    }

    // ---- where the OpMode believes it starts: the first "new Pose2d(x, y, heading)" in its source ----

    static double[] findStartPose(Path file) {
        try {
            String src = Files.readString(file, StandardCharsets.UTF_8);
            src = src.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\n]*", " ");
            int at = src.indexOf("new Pose2d(");
            if (at < 0) return null;
            int i = at + "new Pose2d(".length(), depth = 1, start = i;
            List<String> args = new ArrayList<>();
            for (; i < src.length() && depth > 0; i++) {
                char ch = src.charAt(i);
                if (ch == '(') depth++;
                else if (ch == ')') { depth--; if (depth == 0) args.add(src.substring(start, i)); }
                else if (ch == ',' && depth == 1) { args.add(src.substring(start, i)); start = i + 1; }
            }
            if (args.size() != 3) return null;
            return new double[] {new Expr(args.get(0)).parse(), new Expr(args.get(1)).parse(), new Expr(args.get(2)).parse()};
        } catch (Exception e) {
            return null;
        }
    }

    /** Tiny arithmetic evaluator: numbers, + - * /, parentheses, Math.toRadians, Math.toDegrees, Math.PI. */
    private static final class Expr {
        private final String s;
        private int p = 0;
        Expr(String s) { this.s = s.replaceAll("\\s+", ""); }

        double parse() {
            double v = sum();
            if (p != s.length()) throw new IllegalArgumentException("unexpected: " + s.substring(p));
            return v;
        }
        private double sum() {
            double v = product();
            while (p < s.length() && (s.charAt(p) == '+' || s.charAt(p) == '-')) {
                char op = s.charAt(p++);
                double r = product();
                v = op == '+' ? v + r : v - r;
            }
            return v;
        }
        private double product() {
            double v = unary();
            while (p < s.length() && (s.charAt(p) == '*' || s.charAt(p) == '/')) {
                char op = s.charAt(p++);
                double r = unary();
                v = op == '*' ? v * r : v / r;
            }
            return v;
        }
        private double unary() {
            if (s.charAt(p) == '-') { p++; return -unary(); }
            if (s.charAt(p) == '+') { p++; return unary(); }
            if (s.charAt(p) == '(') { p++; double v = sum(); expect(')'); return v; }
            if (s.startsWith("Math.toRadians(", p)) { p += 15; double v = sum(); expect(')'); return Math.toRadians(v); }
            if (s.startsWith("Math.toDegrees(", p)) { p += 15; double v = sum(); expect(')'); return Math.toDegrees(v); }
            if (s.startsWith("Math.PI", p)) { p += 7; return Math.PI; }
            int st = p;
            while (p < s.length() && (Character.isDigit(s.charAt(p)) || s.charAt(p) == '.' || s.charAt(p) == 'e' || s.charAt(p) == 'E')) p++;
            if (st == p) throw new IllegalArgumentException("not a number: " + s.substring(p));
            double v = Double.parseDouble(s.substring(st, p));
            if (p < s.length() && (s.charAt(p) == 'd' || s.charAt(p) == 'D' || s.charAt(p) == 'f' || s.charAt(p) == 'F')) p++;
            return v;
        }
        private void expect(char c) {
            if (p >= s.length() || s.charAt(p) != c) throw new IllegalArgumentException("expected " + c);
            p++;
        }
    }

    // ---- git facts for the page header (read-only commands) ----

    private void gitInfo() {
        if (repoDir == null) return;
        branch = git("rev-parse", "--abbrev-ref", "HEAD");
        commit = git("rev-parse", "--short", "HEAD");
        dirty = !git("status", "--porcelain", "--", "TeamCode").isEmpty();
    }

    private String git(String... args) {
        try {
            List<String> cmd = new ArrayList<>(List.of("git", "-C", repoDir.toString()));
            cmd.addAll(List.of(args));
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return p.waitFor() == 0 ? out : "";
        } catch (Exception e) {
            return "";
        }
    }

    public JsonObject codeJson() {
        JsonObject o = new JsonObject();
        o.addProperty("path", repoDir != null ? repoDir.toString() : sourceRoot.toString());
        o.addProperty("label", label);
        o.addProperty("branch", branch);
        o.addProperty("commit", commit);
        o.addProperty("dirty", dirty);
        o.addProperty("compiledAt", compiledAt);
        o.addProperty("ok", ok);
        JsonArray arr = new JsonArray();
        for (Err e : errors) {
            JsonObject j = new JsonObject();
            j.addProperty("file", e.file);
            j.addProperty("line", e.line);
            j.addProperty("msg", e.msg);
            arr.add(j);
        }
        o.add("errors", arr);
        return o;
    }
}
