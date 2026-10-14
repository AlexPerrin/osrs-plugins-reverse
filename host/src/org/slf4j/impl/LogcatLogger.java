package org.slf4j.impl;

import android.util.Log;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MarkerIgnoringBase;
import org.slf4j.helpers.MessageFormatter;

/** slf4j logger writing to logcat under tag "rl" + the logger's simple class name (max 23 chars). Generated layout; see StaticLoggerBinder. */
final class LogcatLogger extends MarkerIgnoringBase {
    private final String tag;

    LogcatLogger(String name) {
        this.name = name;
        String simple = name.substring(name.lastIndexOf('.') + 1);
        String t = "rl." + simple;
        this.tag = t.length() > 23 ? t.substring(0, 23) : t;
    }

    @Override public boolean isTraceEnabled() { return Log.isLoggable(tag, Log.VERBOSE); }
    @Override public void trace(String msg) { Log.v(tag, msg); }
    @Override public void trace(String f, Object a) { logTrace(MessageFormatter.format(f, a)); }
    @Override public void trace(String f, Object a, Object b) { logTrace(MessageFormatter.format(f, a, b)); }
    @Override public void trace(String f, Object... a) { logTrace(MessageFormatter.arrayFormat(f, a)); }
    @Override public void trace(String msg, Throwable t) { Log.v(tag, msg, t); }
    private void logTrace(FormattingTuple t) { Log.v(tag, t.getMessage(), t.getThrowable()); }
    @Override public boolean isDebugEnabled() { return Log.isLoggable(tag, Log.DEBUG); }
    @Override public void debug(String msg) { Log.d(tag, msg); }
    @Override public void debug(String f, Object a) { logDebug(MessageFormatter.format(f, a)); }
    @Override public void debug(String f, Object a, Object b) { logDebug(MessageFormatter.format(f, a, b)); }
    @Override public void debug(String f, Object... a) { logDebug(MessageFormatter.arrayFormat(f, a)); }
    @Override public void debug(String msg, Throwable t) { Log.d(tag, msg, t); }
    private void logDebug(FormattingTuple t) { Log.d(tag, t.getMessage(), t.getThrowable()); }
    @Override public boolean isInfoEnabled() { return Log.isLoggable(tag, Log.INFO); }
    @Override public void info(String msg) { Log.i(tag, msg); }
    @Override public void info(String f, Object a) { logInfo(MessageFormatter.format(f, a)); }
    @Override public void info(String f, Object a, Object b) { logInfo(MessageFormatter.format(f, a, b)); }
    @Override public void info(String f, Object... a) { logInfo(MessageFormatter.arrayFormat(f, a)); }
    @Override public void info(String msg, Throwable t) { Log.i(tag, msg, t); }
    private void logInfo(FormattingTuple t) { Log.i(tag, t.getMessage(), t.getThrowable()); }
    @Override public boolean isWarnEnabled() { return Log.isLoggable(tag, Log.WARN); }
    @Override public void warn(String msg) { Log.w(tag, msg); }
    @Override public void warn(String f, Object a) { logWarn(MessageFormatter.format(f, a)); }
    @Override public void warn(String f, Object a, Object b) { logWarn(MessageFormatter.format(f, a, b)); }
    @Override public void warn(String f, Object... a) { logWarn(MessageFormatter.arrayFormat(f, a)); }
    @Override public void warn(String msg, Throwable t) { Log.w(tag, msg, t); }
    private void logWarn(FormattingTuple t) { Log.w(tag, t.getMessage(), t.getThrowable()); }
    @Override public boolean isErrorEnabled() { return Log.isLoggable(tag, Log.ERROR); }
    @Override public void error(String msg) { Log.e(tag, msg); }
    @Override public void error(String f, Object a) { logError(MessageFormatter.format(f, a)); }
    @Override public void error(String f, Object a, Object b) { logError(MessageFormatter.format(f, a, b)); }
    @Override public void error(String f, Object... a) { logError(MessageFormatter.arrayFormat(f, a)); }
    @Override public void error(String msg, Throwable t) { Log.e(tag, msg, t); }
    private void logError(FormattingTuple t) { Log.e(tag, t.getMessage(), t.getThrowable()); }
}
