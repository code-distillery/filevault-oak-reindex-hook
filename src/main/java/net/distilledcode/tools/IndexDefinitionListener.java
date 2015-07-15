package net.distilledcode.tools;

import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;

/**
 * A ProgressTrackerListener implementation that collects index
 * definition paths. It records all index definition paths for
 * which its {@link #onMessage(Mode, String, String)} method is
 * called, as long as the {@code action} is in the set of actions
 * passed to the constructor.
 */
public class IndexDefinitionListener implements ProgressTrackerListener {

    private static final String OAK_INDEX = "/oak:index/";

    private Set<String> paths;

    private List<String> actions;

    public IndexDefinitionListener(final String... actions) {
        this.actions = asList(actions);
        this.paths = new HashSet<String>();
    }

    /**
     * Retrieve the set of index definition paths that were recorded
     * by this listener.
     *
     * @return set of recorded index definition paths
     */
    public Set<String> getIndexDefinitionPaths() {
        return Collections.unmodifiableSet(paths);
    }

    @Override
    public void onMessage(final Mode mode, final String action, final String path) {
        if (actions.contains(action)) {
            final String mappedPath = getDefinitionPath(path);
            if (mappedPath != null) {
                paths.add(mappedPath);
            }
        }
    }

    @Override
    public void onError(final Mode mode, final String path, final Exception e) {
        // ignore
    }

    private static String getDefinitionPath(final String path) {
        // returns the direct child of a node called "oak:index" or null
        // e.g.
        // /oak:index/foo/bar        -> /oak:index/foo
        // /oak:index/foo            -> /oak:index/foo
        // /oak:index                -> null
        // /foobar/oak:index/foo/bar -> /foobar/oak:index/foo
        // /foobar/oak:index/foo     -> /foobar/oak:index/foo
        // /foobar/oak:index         -> null
        final int pos = path.indexOf(OAK_INDEX);
        if (pos != -1) {
            final int end = path.indexOf('/', pos + OAK_INDEX.length());
            if (end == -1) {
                return path;
            } else {
                return path.substring(0, end);
            }
        }
        return null;
    }

}
