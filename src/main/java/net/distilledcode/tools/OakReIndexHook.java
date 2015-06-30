package net.distilledcode.tools;

import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.InstallContext;
import org.apache.jackrabbit.vault.packaging.InstallHook;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static java.util.Arrays.asList;

public class OakReIndexHook implements InstallHook {

    private static final Logger LOG = LoggerFactory.getLogger(OakReIndexHook.class);

    private PropertyTrackingListener propertyTrackingListener = new PropertyTrackingListener();
    private IndexChangeListener indexChangeListener = new IndexChangeListener();

    @Override
    public void execute(InstallContext context) throws PackageException {
        try {
            switch(context.getPhase()) {
                case PREPARE:
                    recordAndRemoveReindexProperties(context);
                    registerChangeListener(context);
                    break;
                case INSTALLED:
                    restoreReindexProperties(context);
                    break;
        }
        } catch (RepositoryException e) {
            throw new PackageException(e);
        }
    }

    private void recordAndRemoveReindexProperties(InstallContext context) throws RepositoryException {
        final Session session = context.getSession();
        final Node indexes = session.getNode("/oak:index");

        final WorkspaceFilter filter = context.getPackage().getArchive().getMetaInf().getFilter();
        filter.dumpCoverage(indexes, propertyTrackingListener);
        propertyTrackingListener.removeAndRecordReindexProps(session);
    }

    private void registerChangeListener(InstallContext context) {
        final ImportOptions options = context.getOptions();
        final ProgressTrackerListener listener = options.getListener();

        if (listener != null) {
            options.setListener(new CompoundListener(listener, indexChangeListener));
            LOG.info("Registered compound listener");
        } else {
            options.setListener(indexChangeListener);
            LOG.info("Registered simple listener");
        }
    }

    private void restoreReindexProperties(InstallContext context) throws RepositoryException {
        final Session session = context.getSession();
        propertyTrackingListener.restoreReindexProps(session, indexChangeListener.getReindexPaths());
    }

    private static class IndexChangeListener implements ProgressTrackerListener {

        private static final List<String> ACTIONS = asList("M", "U");

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

    private static class CompoundListener implements ProgressTrackerListener {

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

    private static class PropertyTrackingListener implements ProgressTrackerListener {

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

        public void restoreReindexProps(Session session, List<String> reindexPaths) throws RepositoryException {
            for (String path : indexDefinitions.keySet()) {
                final ReindexRecord record = indexDefinitions.get(path);
                final Node definition = session.getNode(path);
                definition.setProperty("reindex", record.reindex || reindexPaths.contains(path));
                if (record.reindexCount != -1) {
                    definition.setProperty("reindexCount", record.reindexCount);
                }
            }
            session.save();
        }

        @Override
        public void onMessage(Mode mode, String action, String path) {
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
}
