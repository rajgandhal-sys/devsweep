package devsweep;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Walks directory trees looking for build artifacts. Symbolic links and Windows junctions are
 * never followed, and the scanner never descends into an artifact it has already found.
 */
public final class Scanner {

    /** Directories that never contain projects worth scanning. */
    private static final Set<String> SKIP = Set.of(".git", ".hg", ".svn", ".idea", ".vscode", ".Trash");

    /** Scans each root and returns every artifact found, sizes included. */
    public List<Artifact> scan(List<Path> roots) {
        List<Path> found = new ArrayList<>();
        for (Path root : roots) {
            found.addAll(findArtifactDirs(root.toAbsolutePath().normalize()));
        }
        return found.parallelStream()
                .distinct()
                .map(this::describe)
                .toList();
    }

    List<Path> findArtifactDirs(Path root) {
        List<Path> found = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (attrs.isOther() || attrs.isSymbolicLink()) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    Path name = dir.getFileName();
                    if (name != null && SKIP.contains(name.toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (ArtifactKind.of(dir).isPresent()) {
                        found.add(dir);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) {
                    return FileVisitResult.CONTINUE; // permission denied etc. - skip quietly
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Could not scan " + root, e);
        }
        return Collections.unmodifiableList(found);
    }

    Artifact describe(Path dir) {
        ArtifactKind kind = ArtifactKind.of(dir)
                .orElseThrow(() -> new IllegalStateException(dir + " is not an artifact"));
        return new Artifact(dir, kind, sizeOf(dir), lastActive(dir, kind));
    }

    /** Total size of regular files under {@code dir}, without following links. */
    static long sizeOf(Path dir) {
        AtomicLong total = new AtomicLong();
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                    return attrs.isOther() ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) {
                        total.addAndGet(attrs.size());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // Partial size is still useful; fall through.
        }
        return total.get();
    }

    /**
     * Best guess of when the project was last worked on: the newest of the marker files
     * (pom.xml, package.json...), the git index (touched by commits, checkouts and status)
     * and the artifact directory itself (touched by builds).
     */
    static Instant lastActive(Path dir, ArtifactKind kind) {
        Path project = dir.getParent();
        Instant newest = modified(dir);
        for (String marker : kind.markers()) {
            newest = max(newest, modified(project.resolve(marker)));
        }
        return max(newest, modified(project.resolve(".git").resolve("index")));
    }

    private static Instant modified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }
}
