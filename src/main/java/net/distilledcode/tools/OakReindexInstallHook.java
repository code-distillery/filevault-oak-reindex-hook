package net.distilledcode.tools;

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

/**
 * The OakReindexInstallHook automatically triggers a re-index of
 * Oak indexes if their definitions are modified.
 * <br/>
 * The hook works by searching any contained index definitions
 * in the prepare phase. It then (transiently) removes and records
 * the {@code reindex} and {@code reindexCount} properties of the
 * definition nodes. This step is necessary to avoid false positives
 * for definition changes.
 * <br/>
 * Next any changes on index definitions during package installation
 * are tracked.
 * <br/>
 * Finally the previously recorded properties are restored, with the
 * exception of modified index definitions. For them, only the
 * {@code reindexCount} property is restored and the {@code reindex}
 * property is set to {@code true}.
 */
public class OakReindexInstallHook implements InstallHook {

    private static final Logger LOG = LoggerFactory.getLogger(OakReindexInstallHook.class);

    private final PropertyTrackingListener propertyTrackingListener = new PropertyTrackingListener();

    @Override
    public void execute(InstallContext context) throws PackageException {
        LOG.info("enter execute in phase {}", context.getPhase());
        try {
            switch(context.getPhase()) {
                case PREPARE:
                    final IndexChangeListener indexChangeListener = new IndexChangeListener(context.getSession(), propertyTrackingListener);
                    recordAndRemoveReindexProperties(context, propertyTrackingListener);
                    registerChangeListener(context, indexChangeListener);
                    break;
                case END:
                    propertyTrackingListener.restoreUnchangedProperties(context.getSession());
                    break;
            }
        } catch (RepositoryException e) {
            throw new PackageException(e);
        } finally {
            LOG.info("exit execute in phase {}", context.getPhase());
        }
    }

    private void recordAndRemoveReindexProperties(InstallContext context, final PropertyTrackingListener propertyTrackingListener) throws RepositoryException {
        final Session session = context.getSession();
        final Node indexes = session.getNode("/oak:index");
        final WorkspaceFilter filter = context.getPackage().getArchive().getMetaInf().getFilter();
        filter.dumpCoverage(indexes, propertyTrackingListener);
        propertyTrackingListener.removeAndRecordReindexProps(session);
    }

    private void registerChangeListener(InstallContext context, final IndexChangeListener indexChangeListener) {
        final ImportOptions options = context.getOptions();
        options.setListener(CompoundProgressTrackerListener.create(options.getListener(), indexChangeListener));
    }
}
