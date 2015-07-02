package net.distilledcode.tools;

import org.apache.jackrabbit.oak.jcr.Jcr;
import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.FileArchive;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.InstallContext;
import org.apache.jackrabbit.vault.packaging.InstallHook;
import org.apache.jackrabbit.vault.packaging.InstallHookProcessor;
import org.apache.jackrabbit.vault.packaging.InstallHookProcessorFactory;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.impl.InstallContextImpl;
import org.apache.jackrabbit.vault.packaging.impl.InstallHookProcessorImpl;
import org.apache.jackrabbit.vault.packaging.impl.JcrPackageManagerImpl;
import org.apache.jackrabbit.vault.packaging.impl.ZipVaultPackage;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IndexChangeHandlerTest {

    private static final Logger LOG = LoggerFactory.getLogger(IndexChangeHandlerTest.class);

    private Repository repository;

    private Session admin;

    private JcrPackageManagerImpl packMgr;

    @Before
    public void setup() throws RepositoryException {
        repository = new Jcr().createRepository();
        admin = repository.login(new SimpleCredentials("admin", "admin".toCharArray()), null);
        packMgr = new JcrPackageManagerImpl(admin);
    }

    @Test
    public void detectContainedIndexes() throws PackageException, IOException, RepositoryException, TimeoutException {
        installWithHook(admin, "simple-index-definition/step1", new OakReindexInstallHook());

        assertTrue(admin.getRootNode().hasNode("oak:index/jcrMimeType"));
        final Node definition = admin.getNode("/oak:index/jcrMimeType");
        assertFalse(definition.getProperty("reindex").getBoolean());
        assertEquals(1, definition.getProperty("reindexCount").getLong());

        installWithHook(admin, "simple-index-definition/step2", new OakReindexInstallHook());
        assertFalse(definition.getProperty("reindex").getBoolean());
        assertEquals(2, definition.getProperty("reindexCount").getLong());

        installWithHook(admin, "simple-index-definition/step2", new OakReindexInstallHook());
        assertFalse(definition.getProperty("reindex").getBoolean());
        assertEquals(2, definition.getProperty("reindexCount").getLong());
    }

    private void installWithHook(final Session session, final String packagePath, final InstallHook hook)
            throws IOException, PackageException, RepositoryException {
        final VaultPackage vaultPackage = loadVaultPackage(packagePath);
        final HookImportOptions defaultOptions = getDefaultOptions();
        defaultOptions.addHook(hook);
        vaultPackage.extract(session, defaultOptions);
        session.refresh(false);
    }

    private void waitForIndexing(final String definitionPath, final long timeoutMs) throws RepositoryException, TimeoutException {
        final long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        Node definition = admin.getNode(definitionPath);
        while (!definition.hasProperty("reindex") || definition.getProperty("reindex").getBoolean()) {
            try {
                if (end < System.nanoTime()) {
                    throw new TimeoutException("Aborted wait for indexing of " + definition.getPath());
                }
                TimeUnit.MILLISECONDS.sleep(Math.min(timeoutMs / 10, 10));
                admin.refresh(false);
                definition = admin.getNode(definitionPath);
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    private VaultPackage loadVaultPackage(final String packageName) throws IOException {
        final URL packageURL = getClass().getResource("/packages/" + packageName);
        final String filename = packageURL.getFile();
        final File file = new File(filename);
        return new TestVaultPackage(new FileArchive(file), true);
    }

    private HookImportOptions getDefaultOptions() {
        final HookImportOptions opts = new HookImportOptions();
        opts.setListener(new ProgressTrackerListener() {
            public void onMessage(Mode mode, String action, String path) {
                LOG.info("{} {}", action, path);
            }

            public void onError(Mode mode, String path, Exception e) {
                LOG.info("E {} {}", path, e.toString());
            }
        });
        return opts;
    }

    private static class TestVaultPackage extends ZipVaultPackage {
        public TestVaultPackage(final Archive archive, final boolean strict) throws IOException {
            super(archive, strict);
        }

        @Override
        protected InstallContextImpl prepareExtract(final Session session, final ImportOptions opts) throws RepositoryException, PackageException {
            return super.prepareExtract(session, opts);
        }
    }

    private static class HookImportOptions extends ImportOptions implements InstallHookProcessorFactory {

        private TestHookProcessor processor = new TestHookProcessor();

        @Override
        public InstallHookProcessor createInstallHookProcessor() {
            return processor;
        }

        public void addHook(final InstallHook hook) {
            processor.addHook(hook);
        }
    }

    private static class TestHookProcessor implements InstallHookProcessor {

        private InstallHookProcessor delegate;

        private List<InstallHook> hooks = new ArrayList<InstallHook>();

        public TestHookProcessor() {
            this.delegate = new InstallHookProcessorImpl();
        }

        public void addHook(final InstallHook hook) {
            hooks.add(hook);
        }

        @Override
        public void registerHooks(final Archive archive, final ClassLoader classLoader)
                throws PackageException {
            this.delegate.registerHooks(archive, classLoader);
        }

        @Override
        public void registerHook(final VaultInputSource input, final ClassLoader classLoader)
                throws IOException, PackageException {
            this.delegate.registerHook(input, classLoader);
        }

        @Override
        public boolean hasHooks() {
            return !hooks.isEmpty() || this.delegate.hasHooks();
        }

        @Override
        public boolean execute(final InstallContext context) {
            for (final InstallHook hook : hooks) {
                try {
                    hook.execute(context);
                } catch (PackageException e) {
                    // abort processing only for prepare phase
                    if (context.getPhase() == InstallContext.Phase.PREPARE) {
                        LOG.warn("Hook " + hook +" threw package exception. Prepare aborted.", e);
                        return false;
                    }
                    LOG.warn("Hook " + hook +" threw package exception. Ignored", e);
                } catch (Throwable e) {
                    LOG.warn("Hook " + hook +" threw runtime exception.", e);
                }
            }
            return this.delegate.execute(context);
        }
    }
}
