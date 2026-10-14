package org.slf4j.impl;

import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.spi.LoggerFactoryBinder;

/** slf4j 1.7 binding to logcat, so plugins' @Slf4j logging shows up in `adb logcat`. */
public final class StaticLoggerBinder implements LoggerFactoryBinder {
    public static String REQUESTED_API_VERSION = "1.6.99";
    private static final StaticLoggerBinder SINGLETON = new StaticLoggerBinder();
    private final ILoggerFactory factory = new ILoggerFactory() {
        private final ConcurrentHashMap<String, Logger> loggers = new ConcurrentHashMap<>();

        @Override
        public Logger getLogger(String name) {
            return loggers.computeIfAbsent(name, LogcatLogger::new);
        }
    };

    public static StaticLoggerBinder getSingleton() {
        return SINGLETON;
    }

    @Override
    public ILoggerFactory getLoggerFactory() {
        return factory;
    }

    @Override
    public String getLoggerFactoryClassStr() {
        return LogcatLogger.class.getName();
    }
}
