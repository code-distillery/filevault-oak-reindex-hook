package net.distilledcode.tools;

import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.HashMap;
import java.util.List;

class PropertyTrackingListener implements ProgressTrackerListener {

    private static final Logger LOG = LoggerFactory.getLogger(PropertyTrackingListener.class);

    private final HashMap<String, ReindexRecord> indexDefinitions = new HashMap<String, ReindexRecord>();

    public void removeAndRecordReindexProps(Session session) throws RepositoryException {
        for (String path : indexDefinitions.keySet()) {
            final ReindexRecord record = indexDefinitions.get(path);
            final Node definition = session.getNode(path);
            if (definition.hasProperty("reindex")) {
                final Property property = definition.getProperty("reindex");
                record.reindex =  property.getBoolean();
                property.remove();
            }
            if (definition.hasProperty("reindexCount")) {
                final Property property = definition.getProperty("reindexCount");
                record.reindexCount =  property.getLong();
                property.remove();
            }
        }
        // no save needed, because the package installation will work with the same session
    }

    public void restoreUnchangedProperties(Session session, String path) throws RepositoryException {
        if (indexDefinitions.containsKey(path)) {
            final ReindexRecord record = indexDefinitions.remove(path);
            final Node definition = session.getNode(path);
            definition.setProperty("reindex", true);
            if (record.reindexCount != -1) {
                definition.setProperty("reindexCount", record.reindexCount);
            }
        }
    }

    public void restoreUnchangedProperties(Session session) throws RepositoryException {
        for (final String path : indexDefinitions.keySet()) {
            final ReindexRecord record = indexDefinitions.get(path);
            final Node definition = session.getNode(path);
            definition.setProperty("reindex", record.reindex);
            if (record.reindexCount != -1) {
                definition.setProperty("reindexCount", record.reindexCount);
            }
        }
        session.save();
    }

    @Override
    public void onMessage(Mode mode, String action, String path) {
        LOG.info("Mode: {}, action: {}, path: {}", mode, action, path);
        if (path.startsWith("/oak:index") && path.lastIndexOf("/") == "/oak:index".length()) {
            LOG.info("Recorded {}", path);
            indexDefinitions.put(path, new ReindexRecord());
        }
    }

    @Override
    public void onError(Mode mode, String path, Exception e) {
        // ignore
    }

    private static class ReindexRecord {
        boolean reindex = false;
        long reindexCount = -1;
    }
}
