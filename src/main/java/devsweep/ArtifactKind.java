package devsweep;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * A kind of regenerable build output. A directory only counts as an artifact when its
 * parent contains one of the marker files, so a random folder called "build" or "target"
 * is never touched.
 */
public enum ArtifactKind {
    NODE_MODULES("node_modules", "npm/yarn/pnpm deps", "package.json"),
    MAVEN_TARGET("target", "Maven build", "pom.xml"),
    CARGO_TARGET("target", "Cargo build", "Cargo.toml"),
    GRADLE_BUILD("build", "Gradle build", "build.gradle", "build.gradle.kts"),
    GRADLE_PROJECT_CACHE(".gradle", "Gradle project cache",
            "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts"),
    NEXT_BUILD(".next", "Next.js build", "next.config.js", "next.config.mjs", "next.config.ts");

    private final String dirName;
    private final String label;
    private final List<String> markers;

    ArtifactKind(String dirName, String label, String... markers) {
        this.dirName = dirName;
        this.label = label;
        this.markers = List.of(markers);
    }

    public String dirName() {
        return dirName;
    }

    public String label() {
        return label;
    }

    public List<String> markers() {
        return markers;
    }

    /** Returns the kind of artifact {@code dir} is, or empty if it is not a recognised artifact. */
    public static Optional<ArtifactKind> of(Path dir) {
        Path name = dir.getFileName();
        Path parent = dir.getParent();
        if (name == null || parent == null) {
            return Optional.empty();
        }
        for (ArtifactKind kind : values()) {
            if (kind.dirName.equals(name.toString()) && kind.hasMarker(parent)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    private boolean hasMarker(Path projectDir) {
        for (String marker : markers) {
            if (Files.isRegularFile(projectDir.resolve(marker))) {
                return true;
            }
        }
        return false;
    }
}
