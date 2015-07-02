package net.distilledcode.tools;

import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;

class IndexChangeListener implements ProgressTrackerListener {

    private static final Logger LOG = LoggerFactory.getLogger(IndexChangeListener.class);

    private static final List<String> ACTIONS = asList("U");

    private final List<String> reindexPaths = new ArrayList<String>();

    @Override
    public void onMessage(Mode mode, String action, String path) {
        LOG.info("Mode: {}, action: {}, path: {}", mode, action, path);
        if (ACTIONS.contains(action) && path.contains("/oak:index/")) {
            reindexPaths.add(path);
        }
    }

    @Override
    public void onError(Mode mode, String path, Exception e) {
        LOG.error("Mode: {}, path: {}", mode, path, e);
    }

    public List<String> getReindexPaths() {
        return reindexPaths;
    }
}
