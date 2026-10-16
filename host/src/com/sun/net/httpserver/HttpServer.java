package com.sun.net.httpserver;

import android.util.Log;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Subset of the JDK's com.sun.net.httpserver.HttpServer (not on Android) for plugins that serve
 * HTTP, e.g. OpenRune-Developer-Tools' MCP server. Hardened for a game client:
 * - off unless `setprop debug.osrsrl.devtools 1` (start() throws otherwise);
 * - always binds 127.0.0.1, whatever address the plugin asks for;
 * - every request needs `Authorization: Bearer <token>` (or `?token=`), where the random token is
 *   only written to logcat ("httpserver token ..."), which only adb can read (as the memory agent).
 * One request per connection, Content-Length bodies only.
 */
public class HttpServer {
    private static final String TAG = "osrsrl-host";
    private static final int MAX_BODY = 4 << 20;
    private static String token;

    private final int port;
    private final List<HttpContext> contexts = new ArrayList<>();
    private Executor executor;
    private ServerSocket socket;
    private Thread acceptor;

    protected HttpServer(int port) { this.port = port; }

    public static HttpServer create() throws IOException { return new HttpServer(0); }

    public static HttpServer create(InetSocketAddress addr, int backlog) throws IOException {
        return new HttpServer(addr != null ? addr.getPort() : 0);
    }

    public void bind(InetSocketAddress addr, int backlog) throws IOException {
        throw new IOException("bind: pass the address to create()");
    }

    public void setExecutor(Executor executor) { this.executor = executor; }
    public Executor getExecutor() { return executor; }

    public synchronized HttpContext createContext(String path, HttpHandler handler) {
        HttpContext c = new HttpContext(this, path, handler);
        contexts.add(c);
        return c;
    }

    public synchronized HttpContext createContext(String path) { return createContext(path, null); }

    public synchronized void removeContext(String path) { contexts.removeIf(c -> c.path.equals(path)); }

    public InetSocketAddress getAddress() {
        return new InetSocketAddress(InetAddress.getLoopbackAddress(), socket != null ? socket.getLocalPort() : port);
    }

    private static boolean enabled() {
        try {
            Class<?> n = Class.forName("net.runelite.host.Native");
            java.lang.reflect.Method m = n.getDeclaredMethod("property", String.class);
            m.setAccessible(true);
            return "1".equals(m.invoke(null, "debug.osrsrl.devtools"));
        } catch (Throwable t) {
            return false;
        }
    }

    private static synchronized String token() {
        if (token == null) {
            byte[] b = new byte[16];
            new SecureRandom().nextBytes(b);
            StringBuilder s = new StringBuilder();
            for (byte x : b) s.append(String.format("%02x", x & 0xff));
            token = s.toString();
        }
        return token;
    }

    public void start() {
        if (!enabled()) throw new IllegalStateException("HTTP servers are off (setprop debug.osrsrl.devtools 1, then restart the game)");
        try {
            socket = new ServerSocket(port, 16, InetAddress.getByName("127.0.0.1"));
        } catch (IOException e) {
            throw new IllegalStateException("bind 127.0.0.1:" + port + " failed", e);
        }
        Log.i(TAG, "httpserver token " + token() + " port " + socket.getLocalPort());
        acceptor = new Thread(this::acceptLoop, "httpserver-" + socket.getLocalPort());
        acceptor.setDaemon(true);
        acceptor.start();
    }

    public void stop(int delay) {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
        socket = null;
    }

    private void acceptLoop() {
        ServerSocket s = socket;
        while (s != null && !s.isClosed()) {
            final Socket c;
            try {
                c = s.accept();
            } catch (IOException e) {
                break;
            }
            Runnable r = () -> serve(c);
            Executor ex = executor;
            try {
                if (ex != null) ex.execute(r);
                else new Thread(r, "httpserver-conn").start();
            } catch (RuntimeException e) {
                try { c.close(); } catch (IOException ignored) {}
            }
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int ch;
        while ((ch = in.read()) != -1) {
            if (ch == '\n') break;
            if (ch != '\r') b.write(ch);
            if (b.size() > 8192) throw new IOException("header line too long");
        }
        if (ch == -1 && b.size() == 0) return null;
        return new String(b.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    private void serve(Socket c) {
        try {
            c.setSoTimeout(30000);
            InputStream in = new BufferedInputStream(c.getInputStream());
            String request = readLine(in);
            if (request == null) { c.close(); return; }
            String[] parts = request.split(" ");
            if (parts.length < 2) { c.close(); return; }
            Headers headers = new Headers();
            for (String line; (line = readLine(in)) != null && !line.isEmpty(); ) {
                int i = line.indexOf(':');
                if (i > 0) headers.add(line.substring(0, i).trim(), line.substring(i + 1).trim());
            }
            String len = headers.getFirst("Content-Length");
            int n = len != null ? Integer.parseInt(len.trim()) : 0;
            if (n < 0 || n > MAX_BODY) throw new IOException("bad Content-Length " + n);
            byte[] body = new byte[n];
            for (int off = 0; off < n; ) {
                int r = in.read(body, off, n - off);
                if (r < 0) throw new IOException("short body");
                off += r;
            }
            URI uri = URI.create(parts[1]);
            HttpContext ctx = match(uri.getPath());
            HttpExchange ex = new HttpExchange(c, parts[0], uri, headers, new ByteArrayInputStream(body), ctx);
            if (!authorized(headers, uri)) {
                ex.sendResponseHeaders(401, -1);
                ex.close();
                return;
            }
            if (ctx == null || ctx.handler == null) {
                ex.sendResponseHeaders(404, -1);
                ex.close();
                return;
            }
            try {
                ctx.handler.handle(ex);
            } finally {
                ex.close();
            }
        } catch (Throwable t) {
            Log.w(TAG, "httpserver: " + t);
            try { c.close(); } catch (IOException ignored) {}
        }
    }

    private static boolean authorized(Headers headers, URI uri) {
        String t = token();
        String auth = headers.getFirst("Authorization");
        if (auth != null && auth.equals("Bearer " + t)) return true;
        String q = uri.getRawQuery();
        if (q != null) for (String p : q.split("&")) if (p.equals("token=" + t)) return true;
        return false;
    }

    private synchronized HttpContext match(String path) {
        HttpContext best = null;
        if (path == null) path = "/";
        for (HttpContext c : contexts)
            if (path.startsWith(c.path) && (best == null || c.path.length() > best.path.length())) best = c;
        return best;
    }
}
