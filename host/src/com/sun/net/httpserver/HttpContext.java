package com.sun.net.httpserver;

/** Subset of the JDK class: a path prefix and its handler. */
public class HttpContext {
    final String path;
    final HttpHandler handler;
    final HttpServer server;

    HttpContext(HttpServer server, String path, HttpHandler handler) {
        this.server = server;
        this.path = path;
        this.handler = handler;
    }

    public String getPath() { return path; }
    public HttpHandler getHandler() { return handler; }
    public HttpServer getServer() { return server; }
}
