package net.distilledcode.tools;

import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.List;

import static java.util.Arrays.asList;

class IndexChangeListener implements ProgressTrackerListener {

    private static final Logger LOG = LoggerFactory.getLogger(IndexChangeListener.class);

    private static final List<String> ACTIONS = asList("U");

    private Session session;
    private PropertyTrackingListener propertyTrackingListener;

    public IndexChangeListener(final Session session, final PropertyTrackingListener propertyTrackingListener) {
        this.session = session;
        this.propertyTrackingListener = propertyTrackingListener;
    }

    @Override
    public void onMessage(Mode mode, String action, String path) {
        LOG.info("Mode: {}, action: {}, path: {}", mode, action, path);
        if (ACTIONS.contains(action) && path.contains("/oak:index/")) {
            // TODO: get index definition path
            try {
                propertyTrackingListener.restoreUnchangedProperties(session, path);
            } catch (RepositoryException e) {
                LOG.error("Failed to restore properties", e);
            }
        }
    }

    @Override
    public void onError(Mode mode, String path, Exception e) {
        LOG.error("Mode: {}, path: {}", mode, path, e);
    }
}
