package com.agentledger.utils;

import java.io.IOException;

import java.nio.charset.StandardCharsets;

import java.nio.file.*;

import java.time.LocalDateTime;

import java.time.format.DateTimeFormatter;

/**

 * Minimal file logger. Writes to <appDataDir>/app.log so a non-technical client

 * can send the file when reporting a problem. No console output in production.

 */

public final class Log {

    private Log() {}

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static Path logFile() {

        // mirror Database.appDataDir() without importing it (avoid init-order coupling)

        String appData = System.getenv("APPDATA");

        Path base = (appData != null && !appData.isBlank())

                ? Path.of(appData) : Path.of(System.getProperty("user.home"));

        return base.resolve("AgentLedger").resolve("app.log");

    }

    public static synchronized void info(String msg) { write("INFO", msg, null); }

    public static synchronized void error(String msg, Throwable t) { write("ERROR", msg, t); }

    public static synchronized void error(Throwable t) { write("ERROR", t.getMessage(), t); }

    private static void write(String level, String msg, Throwable t) {

        try {

            Path f = logFile();

            Files.createDirectories(f.getParent());

            StringBuilder sb = new StringBuilder()

                    .append(LocalDateTime.now().format(TS)).append(' ')

                    .append(level).append(' ')

                    .append(msg == null ? "" : msg).append(System.lineSeparator());

            if (t != null) {

                for (StackTraceElement e : t.getStackTrace())

                    sb.append("    at ").append(e).append(System.lineSeparator());

            }

            Files.writeString(f, sb.toString(), StandardCharsets.UTF_8,

                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        } catch (IOException ignore) {

            // logging must never crash the app

        }

    }

}

