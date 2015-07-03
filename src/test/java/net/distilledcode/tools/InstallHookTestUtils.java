package net.distilledcode.tools;

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
import org.apache.jackrabbit.vault.packaging.impl.ZipVaultPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class InstallHookTestUtils {

    private static final Logger LOG = LoggerFactory.getLogger(InstallHookTestUtils.class);

    public static void installWithHook(final Session session, final String packagePath, final InstallHook hook)
            throws IOException, PackageException, RepositoryException {
        final VaultPackage vaultPackage = loadVaultPackage(packagePath);
        final HookImportOptions defaultOptions = getDefaultOptions();
        defaultOptions.addHook(hook);
        vaultPackage.extract(session, defaultOptions);
        session.refresh(false);
    }

    private static VaultPackage loadVaultPackage(final String packageName) throws IOException {
        final URL packageURL = IndexChangeHandlerTest.class.getResource("/packages/" + packageName);
        final String filename = packageURL.getFile();
        final File file = new File(filename);
        return new TestVaultPackage(new FileArchive(file), true);
    }

    private static HookImportOptions getDefaultOptions() {
        final HookImportOptions opts = new HookImportOptions();
        opts.setListener(new ProgressTrackerListener() {

            private final Logger LOG = LoggerFactory.getLogger("Package Installation");

            public void onMessage(Mode mode, String action, String path) {
                LOG.info("{} {}", action, path);
            }

            public void onError(Mode mode, String path, Exception e) {
                LOG.info("E {} {}", path, e.toString());
            }
        });
        return opts;
    }

    /**
     * VaultPackage implementation that makes constructor visible for testing.
     */
    private static class TestVaultPackage extends ZipVaultPackage {
        public TestVaultPackage(final Archive archive, final boolean strict) throws IOException {
            super(archive, strict);
        }
    }

    /**
     * ImportOptions implementation that allows setting custom InstallHooks
     * for testing.
     */
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

    /**
     * InstallHookProcessor implementation for testing that allows
     * adding InstallHook instances. Used in HookImportOptions.
     */
    private static class TestHookProcessor implements InstallHookProcessor {

        private List<InstallHook> hooks = new ArrayList<InstallHook>();

        public void addHook(final InstallHook hook) {
            hooks.add(hook);
        }

        @Override
        public void registerHooks(final Archive archive, final ClassLoader classLoader)
                throws PackageException {
            // do nothing
        }

        @Override
        public void registerHook(final VaultInputSource input, final ClassLoader classLoader)
                throws IOException, PackageException {
            // do nothing
        }

        @Override
        public boolean hasHooks() {
            return !hooks.isEmpty();
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
            return true;
        }
    }
}
