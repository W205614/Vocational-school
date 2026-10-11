package com.tianji.common.utils;

import org.slf4j.Logger;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Shared startup message for the independently runnable applications. */
public final class StartupLogger {
    private StartupLogger() {
    }

    public static void log(Environment environment, Logger logger) throws UnknownHostException {
        String protocol = environment.getProperty("server.ssl.key-store") == null ? "http" : "https";
        logger.info("--/\n---------------------------------------------------------------------------------------\n\t" +
                        "Application '{}' is running! Access URLs:\n\t" +
                        "Local: \t\t{}://localhost:{}\n\t" +
                        "External: \t{}://{}:{}\n\t" +
                        "Profile(s): \t{}" +
                        "\n---------------------------------------------------------------------------------------",
                environment.getProperty("spring.application.name"),
                protocol,
                environment.getProperty("server.port"),
                protocol,
                InetAddress.getLocalHost().getHostAddress(),
                environment.getProperty("server.port"),
                environment.getActiveProfiles());
    }
}
