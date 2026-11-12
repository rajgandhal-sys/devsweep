package devsweep;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static devsweep.ScannerTest.file;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CleanerTest {

    @TempDir
    Path root;

    private final Cleaner cleaner = new Cleaner();

    @Test
    void deletesArtifactButKeepsProject() throws IOException {
        Path app = Files.createDirectories(root.resolve("app"));
        file(app.resolve("pom.xml"), 10);
        file(app.resolve("src/main/java/App.java"), 10);
        file(app.resolve("target/classes/App.class"), 10);
        Path readOnly = file(app.resolve("target/locked.jar"), 10);
        assertTrue(readOnly.toFile().setReadOnly());

        cleaner.delete(new Artifact(app.resolve("target"), ArtifactKind.MAVEN_TARGET, 30, Instant.now()));

        assertFalse(Files.exists(app.resolve("target")));
        assertTrue(Files.exists(app.resolve("pom.xml")));
        assertTrue(Files.exists(app.resolve("src/main/java/App.java")));
    }

    @Test
    void refusesWhenDirectoryIsNoLongerAnArtifact() throws IOException {
        Path app = Files.createDirectories(root.resolve("app"));
        file(app.resolve("target/important.txt"), 10); // no pom.xml next to it
        Artifact stale = new Artifact(app.resolve("target"), ArtifactKind.MAVEN_TARGET, 10, Instant.now());

        assertThrows(IllegalStateException.class, () -> cleaner.delete(stale));
        assertTrue(Files.exists(app.resolve("target/important.txt")));
    }

    @Test
    void removesLinksWithoutTouchingTheirTargets() throws IOException {
        Path outside = file(root.resolve("outside/keep.txt"), 10);
        Path app = Files.createDirectories(root.resolve("app"));
        file(app.resolve("package.json"), 10);
        Path modules = Files.createDirectories(app.resolve("node_modules"));
        try {
            Files.createSymbolicLink(modules.resolve("linked"), outside.getParent());
        } catch (IOException | UnsupportedOperationException e) {
            // Windows without developer mode cannot create symlinks; nothing to test here.
            org.junit.jupiter.api.Assumptions.abort("symbolic links not supported: " + e.getMessage());
        }

        cleaner.delete(new Artifact(modules, ArtifactKind.NODE_MODULES, 0, Instant.now()));

        assertFalse(Files.exists(modules));
        assertTrue(Files.exists(outside));
    }
}
