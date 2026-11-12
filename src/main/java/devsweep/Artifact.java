package devsweep;

import java.nio.file.Path;
import java.time.Instant;

/**
 * A build artifact directory found during a scan.
 *
 * @param path       the artifact directory itself, e.g. {@code ~/code/app/node_modules}
 * @param kind       what produced it
 * @param sizeBytes  total size of the regular files inside it
 * @param lastActive best guess of when the owning project was last worked on
 */
public record Artifact(Path path, ArtifactKind kind, long sizeBytes, Instant lastActive) {

    public Path projectDir() {
        return path.getParent();
    }
}
