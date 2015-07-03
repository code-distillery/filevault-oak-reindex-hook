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

    private static final List<String> ACTIONS = asList("U", "A");

    private Session session;
    private PropertyTrackingListener propertyTrackingListener;

    public IndexChangeListener(final Session session, final PropertyTrackingListener propertyTrackingListener) {
        this.session = session;
        this.propertyTrackingListener = propertyTrackingListener;
    }

    @Override
    public void onMessage(Mode mode, String action, String path) {
        if (ACTIONS.contains(action) && path.contains("/oak:index/")) {
            // TODO: get index definition path
            final String definitionPath = getIndexDefinitionPath(path);
            try {
                propertyTrackingListener.restoreUnchangedProperties(session, definitionPath);
            } catch (RepositoryException e) {
                LOG.error("Failed to restore properties", e);
            }
        }
    }

    private String getIndexDefinitionPath(final String path) {
        final String[] parts = path.split("/oak:index/");
        if (parts.length == 2 && parts[1].contains("/")) {
            return parts[0] + "/oak:index/" + parts[1].substring(0, parts[1].indexOf("/"));
        }
        return path;
    }

    @Override
    public void onError(Mode mode, String path, Exception e) {
        LOG.error("Mode: {}, path: {}", mode, path, e);
    }
}
