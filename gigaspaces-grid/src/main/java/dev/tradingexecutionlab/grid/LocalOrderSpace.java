package dev.tradingexecutionlab.grid;

import org.openspaces.core.GigaSpace;
import org.openspaces.core.GigaSpaceConfigurer;
import org.openspaces.core.space.EmbeddedSpaceConfigurer;

/** Starts one embedded Space in the current JVM and releases it on close. */
public class LocalOrderSpace implements AutoCloseable {
    private final EmbeddedSpaceConfigurer configurer;
    private final GigaSpace gigaSpace;

    public LocalOrderSpace(String name) {
        configurer = new EmbeddedSpaceConfigurer(name);
        gigaSpace = new GigaSpaceConfigurer(configurer).gigaSpace();
    }

    public GigaSpace getGigaSpace() {
        return gigaSpace;
    }

    @Override
    public void close() {
        configurer.close();
    }
}
