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
import javax.jcr.RepositoryException;
import javax.jcr.Session;

public class OakReIndexInstallHook implements InstallHook {

    private static final Logger LOG = LoggerFactory.getLogger(OakReIndexInstallHook.class);

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
}
