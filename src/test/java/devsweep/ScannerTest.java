package devsweep;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScannerTest {

    @TempDir
    Path root;

    private final Scanner scanner = new Scanner();

    @Test
    void findsEachKindOnlyWhenItsMarkerFileExists() throws IOException {
        Path maven = project("maven-app", "pom.xml");
        Path node = project("web-app", "package.json");
        Path gradle = project("gradle-app", "build.gradle.kts");
        Path rust = project("rust-app", "Cargo.toml");
        file(maven.resolve("target/classes/App.class"), 100);
        file(node.resolve("node_modules/react/index.js"), 200);
        file(gradle.resolve("build/libs/app.jar"), 300);
        file(gradle.resolve(".gradle/8.10/checksums.bin"), 50);
        file(rust.resolve("target/debug/app"), 400);
        // Same names but no marker file: must be ignored.
        file(root.resolve("photos/build/holiday.jpg"), 999);
        file(root.resolve("notes/target/plan.txt"), 999);

        List<Artifact> found = scanner.scan(List.of(root));

        Set<String> kinds = found.stream().map(a -> a.kind() + ":" + root.relativize(a.path()))
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                "MAVEN_TARGET:" + Path.of("maven-app", "target"),
                "NODE_MODULES:" + Path.of("web-app", "node_modules"),
                "GRADLE_BUILD:" + Path.of("gradle-app", "build"),
                "GRADLE_PROJECT_CACHE:" + Path.of("gradle-app", ".gradle"),
                "CARGO_TARGET:" + Path.of("rust-app", "target")), kinds);
    }

    @Test
    void doesNotDescendIntoArtifacts() throws IOException {
        Path app = project("app", "package.json");
        // A dependency inside node_modules has its own package.json and node_modules.
        Path dep = app.resolve("node_modules/some-lib");
        file(dep.resolve("package.json"), 10);
        file(dep.resolve("node_modules/nested/index.js"), 10);

        List<Artifact> found = scanner.scan(List.of(root));

        assertEquals(1, found.size());
        assertEquals(app.resolve("node_modules"), found.get(0).path());
    }

    @Test
    void skipsGitDirectories() throws IOException {
        file(root.resolve(".git/pom.xml"), 10);
        file(root.resolve(".git/target/x"), 10);

        assertTrue(scanner.scan(List.of(root)).isEmpty());
    }

    @Test
    void measuresSizeOfAllNestedFiles() throws IOException {
        Path app = project("app", "pom.xml");
        file(app.resolve("target/a.class"), 1000);
        file(app.resolve("target/deep/er/b.class"), 2500);

        Artifact artifact = scanner.scan(List.of(root)).get(0);

        assertEquals(3500, artifact.sizeBytes());
    }

    @Test
    void lastActiveIsNewestOfMarkerGitIndexAndArtifact() throws IOException {
        Path app = project("app", "pom.xml");
        Path target = Files.createDirectories(app.resolve("target"));
        Instant old = Instant.now().minus(400, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant recent = Instant.now().minus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        touch(app.resolve("pom.xml"), old);
        touch(target, old);
        assertEquals(old, Scanner.lastActive(target, ArtifactKind.MAVEN_TARGET));

        Path gitIndex = file(app.resolve(".git/index"), 10);
        touch(gitIndex, recent);
        assertEquals(recent, Scanner.lastActive(target, ArtifactKind.MAVEN_TARGET));
    }

    private Path project(String name, String marker) throws IOException {
        Path dir = Files.createDirectories(root.resolve(name));
        Files.writeString(dir.resolve(marker), "");
        return dir;
    }

    static Path file(Path path, int size) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, new byte[size]);
        return path;
    }

    static void touch(Path path, Instant time) throws IOException {
        Files.setLastModifiedTime(path, FileTime.from(time));
    }
}
