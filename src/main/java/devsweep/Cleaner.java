package devsweep;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;

/** Deletes artifact directories. */
public final class Cleaner {

    /**
     * Deletes {@code artifact.path()} recursively. Before deleting, the directory is checked
     * again to still be a recognised artifact, so a stale scan result can never remove
     * anything else. Links inside the directory are removed, never followed.
     */
    public void delete(Artifact artifact) throws IOException {
        Path dir = artifact.path();
        if (ArtifactKind.of(dir).filter(k -> k == artifact.kind()).isEmpty()) {
            throw new IllegalStateException(dir + " is no longer a " + artifact.kind().label() + " directory; refusing to delete");
        }
        if (Files.isSymbolicLink(dir)) {
            throw new IllegalStateException(dir + " is a symbolic link; refusing to delete");
        }
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) throws IOException {
                if (attrs.isOther() && !d.equals(dir)) {
                    // Windows junction: remove the link itself, not what it points to.
                    forceDelete(d);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                forceDelete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException {
                if (e != null) {
                    throw e;
                }
                forceDelete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** Deletes a file, clearing the Windows read-only flag first if that is what blocks it. */
    private static void forceDelete(Path p) throws IOException {
        try {
            Files.delete(p);
        } catch (AccessDeniedException e) {
            DosFileAttributeView dos = Files.getFileAttributeView(p, DosFileAttributeView.class);
            if (dos == null) {
                throw e;
            }
            dos.setReadOnly(false);
            Files.delete(p);
        }
    }
}
