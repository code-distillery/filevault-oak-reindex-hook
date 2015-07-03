package net.distilledcode.tools;

import org.apache.jackrabbit.oak.jcr.Jcr;
import org.apache.jackrabbit.oak.plugins.index.lucene.LuceneIndexEditorProvider;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.junit.Before;
import org.junit.Test;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import java.io.IOException;

import static net.distilledcode.tools.InstallHookTestUtils.installWithHook;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IndexChangeHandlerTest {

    public static final SimpleCredentials ADMIN_CREDENTIALS = new SimpleCredentials("admin", "admin".toCharArray());

    private Session admin;

    @Before
    public void setup() throws RepositoryException {
        admin = new Jcr()
                .with(new LuceneIndexEditorProvider())
                .createRepository().login(ADMIN_CREDENTIALS, null);
    }

    @Test
    public void reindexModifiedIndex() throws PackageException, IOException, RepositoryException {
        // install package version 1
        installWithHook(admin, "property-index-definition/version1", new OakReindexInstallHook());
        assertTrue(admin.getRootNode().hasNode("oak:index/jcrMimeType"));
        final Node definition = admin.getNode("/oak:index/jcrMimeType");
        assertFalse(definition.getProperty("reindex").getBoolean());
        assertEquals(1, definition.getProperty("reindexCount").getLong());

        // install package version 2
        installWithHook(admin, "property-index-definition/version2", new OakReindexInstallHook());
        assertFalse(definition.getProperty("reindex").getBoolean());
        assertEquals(2, definition.getProperty("reindexCount").getLong());
    }

    @Test
    public void noReindexWhenNothingChanged() throws PackageException, IOException, RepositoryException {
        // install package version 1
        installWithHook(admin, "property-index-definition/version1", new OakReindexInstallHook());
        assertTrue(admin.getRootNode().hasNode("oak:index/jcrMimeType"));
        final Node definition = admin.getNode("/oak:index/jcrMimeType");
        assertFalse(definition.getProperty("reindex").getBoolean());
        assertEquals(1, definition.getProperty("reindexCount").getLong());

        // re-install package version 1 (no changes)
        installWithHook(admin, "property-index-definition/version1", new OakReindexInstallHook());
        assertFalse(definition.getProperty("reindex").getBoolean());
        assertEquals(1, definition.getProperty("reindexCount").getLong());
    }

    @Test
    public void reindexModifiedDeepIndexDefinition() throws PackageException, IOException, RepositoryException {
        // install package version 1
        installWithHook(admin, "lucene-index-definition/version1", new OakReindexInstallHook());
        assertTrue(admin.getRootNode().hasNode("oak:index/ntFile"));
        final Node definition = admin.getNode("/oak:index/ntFile");
        assertFalse("reindex != false", definition.getProperty("reindex").getBoolean());
        assertEquals(1, definition.getProperty("reindexCount").getLong());

        // install package version 2
        installWithHook(admin, "lucene-index-definition/version2", new OakReindexInstallHook());
        assertFalse(definition.getProperty("reindex").getBoolean());
        assertEquals(2, definition.getProperty("reindexCount").getLong());
    }

    @Test
    public void noReindexWhenNothingChangedInDeepIndexDefinition() throws PackageException, IOException, RepositoryException {
        // install package version 1
        installWithHook(admin, "lucene-index-definition/version1", new OakReindexInstallHook());
        assertTrue(admin.getRootNode().hasNode("oak:index/ntFile"));
        final Node definition = admin.getNode("/oak:index/ntFile");
        assertFalse("reindex != false", definition.getProperty("reindex").getBoolean());
        assertEquals(1, definition.getProperty("reindexCount").getLong());

        // install package version 1 (no changes)
        installWithHook(admin, "lucene-index-definition/version1", new OakReindexInstallHook());
        assertFalse(definition.getProperty("reindex").getBoolean());
        assertEquals(1, definition.getProperty("reindexCount").getLong());
    }
}
