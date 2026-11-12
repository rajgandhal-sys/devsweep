package devsweep;

import java.io.PrintWriter;
import java.time.Instant;
import java.util.List;

/** Prints scan results as an aligned text table. */
final class Table {

    private Table() {
    }

    static void print(PrintWriter out, List<Artifact> artifacts, Instant now) {
        if (artifacts.isEmpty()) {
            out.println("No build artifacts found.");
            return;
        }
        out.printf("%10s  %-10s  %-22s  %s%n", "SIZE", "ACTIVE", "KIND", "PATH");
        long total = 0;
        for (Artifact a : artifacts) {
            total += a.sizeBytes();
            out.printf("%10s  %-10s  %-22s  %s%n",
                    Units.formatSize(a.sizeBytes()),
                    Units.formatAge(a.lastActive(), now),
                    a.kind().label(),
                    a.path());
        }
        out.printf("%10s  total across %d directories%n", Units.formatSize(total), artifacts.size());
    }
}
