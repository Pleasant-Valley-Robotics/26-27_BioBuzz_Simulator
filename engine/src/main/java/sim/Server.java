package sim;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

/** Serves the page (http) and streams the simulation (WebSocket on the next port). Localhost only. */
public final class Server extends WebSocketServer {
    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=utf-8", "js", "text/javascript; charset=utf-8",
            "css", "text/css; charset=utf-8", "json", "application/json", "png", "image/png",
            "jpg", "image/jpeg", "svg", "image/svg+xml", "ico", "image/x-icon", "md", "text/plain; charset=utf-8");

    private final Engine engine;
    private final Path webDir;
    private final int httpPort;

    public Server(Engine engine, int httpPort, Path webDir) {
        super(new InetSocketAddress("127.0.0.1", httpPort + 1));
        this.engine = engine;
        this.webDir = webDir.toAbsolutePath().normalize();
        this.httpPort = httpPort;
        setReuseAddr(true);
        setTcpNoDelay(true);
        setConnectionLostTimeout(0);
    }

    public void startAll() throws IOException {
        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", httpPort), 0);
        http.createContext("/", ex -> {
            try {
                String p = ex.getRequestURI().getPath();
                if (p.equals("/")) p = "/index.html";
                Path f = webDir.resolve(p.substring(1)).normalize();
                if (!f.startsWith(webDir) || !Files.isRegularFile(f)) {
                    byte[] msg = "not found".getBytes(StandardCharsets.UTF_8);
                    ex.sendResponseHeaders(404, msg.length);
                    try (OutputStream os = ex.getResponseBody()) { os.write(msg); }
                    return;
                }
                String name = f.getFileName().toString();
                String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
                byte[] body = Files.readAllBytes(f);
                ex.getResponseHeaders().set("Content-Type", TYPES.getOrDefault(ext, "application/octet-stream"));
                ex.getResponseHeaders().set("Cache-Control", "no-store");
                ex.sendResponseHeaders(200, body.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(body); }
            } catch (IOException | RuntimeException e) {
                ex.close();
            }
        });
        http.start();
        start();
    }

    @Override public void onOpen(WebSocket conn, ClientHandshake handshake) {
        conn.send(engine.helloJson());
        String prev = engine.previewMsg;
        if (prev != null) conn.send(prev);
        conn.send(engine.telemetryJson());
        JsonObject rep = engine.lastReport;
        if (rep != null) conn.send(rep.toString());
    }

    @Override public void onClose(WebSocket conn, int code, String reason, boolean remote) {}

    @Override public void onMessage(WebSocket conn, String message) {
        try {
            engine.handle(JsonParser.parseString(message).getAsJsonObject());
        } catch (RuntimeException e) {
            System.err.println("bad message from page: " + e.getMessage());
        }
    }

    @Override public void onError(WebSocket conn, Exception ex) {
        if (conn == null) System.err.println("WebSocket server error: " + ex);
    }

    @Override public void onStart() {}
}
