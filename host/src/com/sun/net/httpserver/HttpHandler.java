package com.sun.net.httpserver;

import java.io.IOException;

/** Subset of the JDK's com.sun.net.httpserver for plugins on Android (see HttpServer). */
public interface HttpHandler {
    void handle(HttpExchange exchange) throws IOException;
}
