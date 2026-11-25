package sim;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * BioBuzz simulator for Radical (FTC 7196).
 *   ./sim                 start the engine and open the 3D page
 *   ./sim test <OpMode>   headless run(s), see Headless
 *   ./sim shots ...       shot accuracy table, see ShotLab
 * On Windows the launcher is sim.bat, with the same arguments.
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        Path simDir = findHome();
        Path clone = simDir.resolveSibling("26-27_BioBuzz_7196");
        int port = 8790;
        boolean open = true;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--clone")) clone = Path.of(args[i + 1]).toAbsolutePath().normalize();
            if (args[i].equals("--port")) port = Integer.parseInt(args[i + 1]);
            if (args[i].equals("--no-open")) open = false;
        }
        if (!Files.isDirectory(clone.resolve("TeamCode/src/main/java"))) {
            System.err.println("Cannot find the team code at " + clone + " (use --clone <path>)");
            System.exit(2);
        }
        Files.createDirectories(simDir.resolve(".cache"));
        if (args.length > 0 && args[0].equals("shots")) {
            System.exit(ShotLab.run(simDir, args));
        }
        if (args.length > 0 && args[0].equals("test")) {
            int rc = Headless.run(simDir, clone, args);
            System.exit(rc);
        }
        Engine engine = new Engine(simDir);
        engine.team = TeamCode.compile(clone.resolve("TeamCode/src/main/java"), clone, engine.cacheDir, "working tree");
        TeamCode tc = engine.team;
        System.out.println("Team code: " + clone + " [" + tc.branch + " @ " + tc.commit + (tc.dirty ? ", uncommitted edits" : "") + "]");
        if (tc.ok) {
            System.out.println("Compiled " + tc.opModes.size() + " OpModes.");
        } else {
            System.out.println("COMPILE ERRORS (shown in the page too):");
            for (TeamCode.Err e : tc.errors) System.out.println("  " + e.file + ":" + e.line + "  " + e.msg);
        }
        Server server = new Server(engine, port, simDir.resolve("web"));
        server.startAll();
        engine.server = server;
        String url = "http://localhost:" + port + "/";
        System.out.println("Simulator running at " + url + "  (Ctrl+C to quit)");
        if (open && !openBrowser(url)) System.out.println("Open " + url + " in Chrome.");
        engine.loopForever();
    }

    /**
     * The simulator folder (the one that holds web/ and engine/). It is found from the place this
     * class was loaded from, so the launcher does not have to pass it and a folder name with spaces
     * is fine. -Dsim.home=<path> overrides it.
     */
    static Path findHome() {
        String given = System.getProperty("sim.home");
        if (given != null && !given.isEmpty()) return Path.of(given).toAbsolutePath().normalize();
        try {
            Path p = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath();
            for (; p != null; p = p.getParent()) {
                if (Files.isRegularFile(p.resolve("web/index.html")) && Files.isDirectory(p.resolve("engine"))) return p;
            }
        } catch (Exception e) {
            // fall back to the current folder
        }
        return Path.of(".").toAbsolutePath().normalize();
    }

    /** Opens the page: Chrome on a Mac when it is installed, otherwise the default browser. */
    private static boolean openBrowser(String url) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<List<String>> commands = new ArrayList<>();
        if (os.contains("mac")) {
            commands.add(List.of("open", "-a", "Google Chrome", url));
            commands.add(List.of("open", url));
        } else if (os.contains("win")) {
            commands.add(List.of("rundll32", "url.dll,FileProtocolHandler", url));
        } else {
            commands.add(List.of("xdg-open", url));
        }
        for (List<String> command : commands) {
            try {
                Process p = new ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start();
                // Still running after 5 s counts as opened (some openers stay alive with the browser).
                if (!p.waitFor(5, TimeUnit.SECONDS) || p.exitValue() == 0) return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                // not available here, try the next one
            }
        }
        return false;
    }
}
