package net.distilledcode.tools;

import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static java.util.Arrays.asList;

class CompoundListener implements ProgressTrackerListener {

    private List<ProgressTrackerListener> listeners;

    public CompoundListener(ProgressTrackerListener... listeners) {
        this.listeners = asList(listeners);
    }

    @Override
    public void onMessage(Mode mode, String action, String path) {
        for (ProgressTrackerListener listener : listeners) {
            listener.onMessage(mode, action, path);
        }
    }

    @Override
    public void onError(Mode mode, String path, Exception e) {
        for (ProgressTrackerListener listener : listeners) {
            listener.onError(mode, path, e);
        }
    }
}
