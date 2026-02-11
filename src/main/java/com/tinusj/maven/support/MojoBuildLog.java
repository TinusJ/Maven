package com.tinusj.maven.support;

import org.apache.maven.plugin.logging.Log;
import org.springframework.boot.buildpack.platform.build.AbstractBuildLog;
import org.springframework.boot.buildpack.platform.docker.TotalProgressEvent;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * BuildLog implementation that delegates to Maven's logging system.
 */
public class MojoBuildLog extends AbstractBuildLog {

    private final Supplier<Log> logSupplier;

    /**
     * Creates a new MojoBuildLog that delegates to the provided Maven log.
     *
     * @param logSupplier supplier of Maven log
     */
    public MojoBuildLog(Supplier<Log> logSupplier) {
        this.logSupplier = logSupplier;
    }

    @Override
    protected void log(String message) {
        logSupplier.get().info(message);
    }

    @Override
    protected Consumer<TotalProgressEvent> getProgressConsumer(String message) {
        return new ProgressConsumer(message);
    }

    /**
     * Consumer that logs progress with throttling to avoid log spam.
     */
    private class ProgressConsumer implements Consumer<TotalProgressEvent> {

        private final String message;
        private long lastLogTime;
        private static final long THROTTLE_MILLIS = 2000; // 2 seconds

        ProgressConsumer(String message) {
            this.message = message;
        }

        @Override
        public void accept(TotalProgressEvent event) {
            long now = System.currentTimeMillis();
            if (now - lastLogTime >= THROTTLE_MILLIS) {
                int percent = event.getPercent();
                logSupplier.get().info(message + " " + percent + "%");
                lastLogTime = now;
            }
        }
    }
}
