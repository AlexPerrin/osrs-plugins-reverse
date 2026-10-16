package com.sun.net.httpserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Subset of the JDK class. One request per connection: the response body is buffered and sent
 * with a Content-Length on close(), then the connection is closed.
 */
public class HttpExchange {
    private final Socket socket;
    private final String method;
    private final URI uri;
    private final Headers requestHeaders;
    private final InputStream requestBody;
    private final Headers responseHeaders = new Headers();
    private final HttpContext context;
    private final ByteArrayOutputStream body = new ByteArrayOutputStream();
    private int status = -1;
    private boolean noBody, closed;

    HttpExchange(Socket socket, String method, URI uri, Headers requestHeaders, InputStream requestBody, HttpContext context) {
        this.socket = socket;
        this.method = method;
        this.uri = uri;
        this.requestHeaders = requestHeaders;
        this.requestBody = requestBody;
        this.context = context;
    }

    public String getRequestMethod() { return method; }
    public URI getRequestURI() { return uri; }
    public Headers getRequestHeaders() { return requestHeaders; }
    public Headers getResponseHeaders() { return responseHeaders; }
    public InputStream getRequestBody() { return requestBody; }
    public HttpContext getHttpContext() { return context; }
    public InetSocketAddress getRemoteAddress() { return (InetSocketAddress) socket.getRemoteSocketAddress(); }
    public InetSocketAddress getLocalAddress() { return (InetSocketAddress) socket.getLocalSocketAddress(); }
    public String getProtocol() { return "HTTP/1.1"; }
    public int getResponseCode() { return status; }

    /** As the JDK: length -1 = no body, 0 = any length (here always buffered). */
    public void sendResponseHeaders(int code, long length) throws IOException {
        if (status >= 0) throw new IOException("headers already sent");
        status = code;
        noBody = length == -1;
    }

    private final OutputStream out = new OutputStream() {
        @Override public void write(int b) { body.write(b); }
        @Override public void write(byte[] b, int off, int len) { body.write(b, off, len); }
        @Override public void close() throws IOException { HttpExchange.this.close(); }
    };

    public OutputStream getResponseBody() { return out; }

    public void close() {
        if (closed) return;
        closed = true;
        try {
            if (status < 0) status = 500;
            StringBuilder h = new StringBuilder("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n");
            for (Map.Entry<String, List<String>> e : responseHeaders.entrySet())
                for (String v : e.getValue()) h.append(e.getKey()).append(": ").append(v).append("\r\n");
            h.append("Content-Length: ").append(noBody ? 0 : body.size()).append("\r\n");
            h.append("Connection: close\r\n\r\n");
            OutputStream o = socket.getOutputStream();
            o.write(h.toString().getBytes(StandardCharsets.ISO_8859_1));
            if (!noBody) body.writeTo(o);
            o.flush();
        } catch (IOException ignored) {
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    static String reason(int code) {
        switch (code) {
            case 200: return "OK";
            case 202: return "Accepted";
            case 204: return "No Content";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 503: return "Service Unavailable";
            default: return code < 400 ? "OK" : "Error";
        }
    }
}
