package net.distilledcode.tools;

import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;

import java.util.ArrayList;
import java.util.List;

class CompoundProgressTrackerListener implements ProgressTrackerListener {

    private List<ProgressTrackerListener> listeners;

    private CompoundProgressTrackerListener(List<ProgressTrackerListener> listeners) {
        this.listeners = listeners;
    }

    public static ProgressTrackerListener create(ProgressTrackerListener... listeners) {
        final List<ProgressTrackerListener> ptls = new ArrayList<ProgressTrackerListener>();
        for (final ProgressTrackerListener listener : listeners) {
            if (listener != null) {
                if (listener instanceof CompoundProgressTrackerListener) {
                    ptls.addAll(((CompoundProgressTrackerListener) listener).listeners);
                } else {
                    ptls.add(listener);
                }
            }
        }
        return new CompoundProgressTrackerListener(ptls);
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
