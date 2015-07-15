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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

    public static final String PN_REINDEX = "reindex";

    public static final String PN_REINDEX_COUNT = "reindexCount";

    private Map<String, ReindexRecord> reindexRecords;

    private final IndexDefinitionListener modificationCollector = new IndexDefinitionListener("A", "U", "D");

    @Override
    public void execute(InstallContext context) throws PackageException {
        try {
            switch (context.getPhase()) {
                case PREPARE:
                    final Set<String> definitionPaths = collectIndexDefinitionPaths(context);
                    reindexRecords = removeReindexProperties(context.getSession(), definitionPaths);
                    registerChangeListener(context, modificationCollector);
                    break;
                case INSTALLED:
                    reindexRecords = handleChangedIndexDefinitions(
                            context.getSession(), reindexRecords, modificationCollector.getIndexDefinitionPaths());
                case END:
                    final Session session = context.getSession();
                    restoreUnchangedProperties(session, reindexRecords);
                    session.save();
                    break;
            }
        } catch (RepositoryException e) {
            throw new PackageException(e);
        }
    }

    private static void registerChangeListener(final InstallContext context, final ProgressTrackerListener listener) {
        final ImportOptions options = context.getOptions();
        options.setListener(CompoundProgressTrackerListener.create(options.getListener(), listener));
    }

    private static Map<String, ReindexRecord> handleChangedIndexDefinitions(
            final Session session, final Map<String, ReindexRecord> records, final Set<String> paths)
            throws RepositoryException {

        final Map<String, ReindexRecord> reindexRecords = new HashMap<String, ReindexRecord>(records);
        for (final String path : paths) {
            if (reindexRecords.containsKey(path)) {
                final ReindexRecord record = reindexRecords.remove(path);
                record.reindex = true;
                restoreIndexingProperties(session, path, record);
                LOG.info("Marked index at {} for reindexing", path);
            }
        }
        return reindexRecords;
    }

    private static void restoreUnchangedProperties(final Session session, final Map<String, ReindexRecord> records)
            throws RepositoryException {

        for (final String path : records.keySet()) {
            final ReindexRecord record = records.get(path);
            restoreIndexingProperties(session, path, record);
            LOG.info("Restored unchanged index properties for {}", path);
        }
    }

    private static void restoreIndexingProperties(final Session session, final String path, final ReindexRecord record)
            throws RepositoryException {

        final Node definition = session.getNode(path);
        definition.setProperty(PN_REINDEX, record.reindex);
        if (record.reindexCount != -1) {
            definition.setProperty(PN_REINDEX_COUNT, record.reindexCount);
        }
    }

    private static Set<String> collectIndexDefinitionPaths(InstallContext context) throws RepositoryException {
        final Session session = context.getSession();
        final WorkspaceFilter filter = context.getPackage().getArchive().getMetaInf().getFilter();
        final IndexDefinitionListener collector = new IndexDefinitionListener("A");
        filter.dumpCoverage(session.getRootNode(), collector);
        return collector.getIndexDefinitionPaths();
    }

    private static Map<String, ReindexRecord> removeReindexProperties(final Session session, final Set<String> paths)
            throws RepositoryException {

        final Map<String, ReindexRecord> records = new HashMap<String, ReindexRecord>();
        for (final String path : paths) {
            final ReindexRecord record = new ReindexRecord();
            final Node definition = session.getNode(path);
            if (definition.hasProperty(PN_REINDEX)) {
                final Property property = definition.getProperty(PN_REINDEX);
                record.reindex = property.getBoolean();
                property.remove();
            }
            if (definition.hasProperty(PN_REINDEX_COUNT)) {
                final Property property = definition.getProperty(PN_REINDEX_COUNT);
                record.reindexCount = property.getLong();
                property.remove();
            }
            records.put(path, record);
        }
        return records;
    }

    private static class ReindexRecord {
        boolean reindex = false;
        long reindexCount = -1;
    }
}
