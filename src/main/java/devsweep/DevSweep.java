package devsweep;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

@Command(name = "devsweep",
        mixinStandardHelpOptions = true,
        version = "devsweep 0.1.0",
        description = "Find and clean build artifacts (node_modules, target, build, .gradle, .next) "
                + "in projects you no longer touch.",
        subcommands = {DevSweep.Scan.class, DevSweep.Clean.class, DevSweep.Caches.class,
                CommandLine.HelpCommand.class})
public final class DevSweep implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    public static void main(String[] args) {
        System.exit(newCommandLine().execute(args));
    }

    static CommandLine newCommandLine() {
        CommandLine cli = new CommandLine(new DevSweep());
        cli.registerConverter(Duration.class, Units::parseAge);
        cli.registerConverter(Size.class, s -> new Size(Units.parseSize(s)));
        return cli;
    }

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    /** Wrapper so picocli can convert "10MB" style values. */
    record Size(long bytes) {
    }

    /** Options shared by scan and clean. */
    static final class Filters {
        @Parameters(paramLabel = "DIR", arity = "0..*",
                description = "Directories to scan (default: current directory).")
        List<Path> roots = List.of(Path.of("."));

        @Option(names = {"-o", "--older-than"}, paramLabel = "AGE",
                description = "Only projects inactive for at least AGE, e.g. 30d, 6w, 3m, 1y.")
        Duration olderThan;

        @Option(names = {"-s", "--min-size"}, paramLabel = "SIZE",
                description = "Only artifacts at least SIZE, e.g. 50MB.")
        Size minSize;

        List<Artifact> apply(List<Artifact> all, Instant now) {
            return all.stream()
                    .filter(a -> olderThan == null || !a.lastActive().isAfter(now.minus(olderThan)))
                    .filter(a -> minSize == null || a.sizeBytes() >= minSize.bytes())
                    .sorted(Comparator.comparingLong(Artifact::sizeBytes).reversed())
                    .toList();
        }

        void checkRoots(CommandLine cl) {
            for (Path root : roots) {
                if (!Files.isDirectory(root)) {
                    throw new CommandLine.ParameterException(cl, "Not a directory: " + root);
                }
            }
        }
    }

    @Command(name = "scan", description = "List build artifacts and how much space they use. Deletes nothing.")
    static final class Scan implements Callable<Integer> {
        @Mixin
        Filters filters;

        @CommandLine.Spec
        CommandLine.Model.CommandSpec spec;

        @Override
        public Integer call() {
            filters.checkRoots(spec.commandLine());
            Instant now = Instant.now();
            List<Artifact> artifacts = filters.apply(new Scanner().scan(filters.roots), now);
            PrintWriter out = spec.commandLine().getOut();
            Table.print(out, artifacts, now);
            if (!artifacts.isEmpty()) {
                out.println();
                out.println("Run 'devsweep clean' with the same options to delete these.");
            }
            out.flush();
            return 0;
        }
    }

    @Command(name = "clean", description = "Delete build artifacts. Asks for confirmation unless --yes is given.")
    static final class Clean implements Callable<Integer> {
        @Mixin
        Filters filters;

        @Option(names = {"-n", "--dry-run"}, description = "Show what would be deleted, delete nothing.")
        boolean dryRun;

        @Option(names = {"-y", "--yes"}, description = "Do not ask for confirmation.")
        boolean yes;

        @CommandLine.Spec
        CommandLine.Model.CommandSpec spec;

        @Override
        public Integer call() throws IOException {
            filters.checkRoots(spec.commandLine());
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();
            Instant now = Instant.now();
            List<Artifact> artifacts = filters.apply(new Scanner().scan(filters.roots), now);
            Table.print(out, artifacts, now);
            if (artifacts.isEmpty() || dryRun) {
                if (dryRun && !artifacts.isEmpty()) {
                    out.println();
                    out.println("Dry run: nothing deleted.");
                }
                out.flush();
                return 0;
            }
            long total = artifacts.stream().mapToLong(Artifact::sizeBytes).sum();
            out.println();
            if (!yes && !confirm(out, "Delete " + artifacts.size() + " directories (" + Units.formatSize(total) + ")? [y/N] ")) {
                out.println("Aborted.");
                out.flush();
                return 1;
            }
            Cleaner cleaner = new Cleaner();
            long freed = 0;
            int failures = 0;
            for (Artifact a : artifacts) {
                try {
                    cleaner.delete(a);
                    freed += a.sizeBytes();
                    out.println("deleted  " + a.path());
                } catch (IOException | RuntimeException e) {
                    failures++;
                    err.println("failed   " + a.path() + ": " + e.getMessage());
                }
            }
            out.println();
            out.println("Freed " + Units.formatSize(freed) + (failures > 0 ? ", " + failures + " failed" : "") + ".");
            out.flush();
            err.flush();
            return failures > 0 ? 2 : 0;
        }

        private static boolean confirm(PrintWriter out, String prompt) throws IOException {
            out.print(prompt);
            out.flush();
            String answer = new BufferedReader(new InputStreamReader(System.in)).readLine();
            if (answer == null) {
                return false;
            }
            // Keep ASCII letters only: PowerShell pipes can prepend a byte-order mark.
            String word = answer.replaceAll("[^A-Za-z]", "").toLowerCase(Locale.ROOT);
            return word.equals("y") || word.equals("yes");
        }
    }

    @Command(name = "caches", description = "Show the size of global package caches (~/.m2, ~/.gradle, ~/.npm...). Read-only.")
    static final class Caches implements Callable<Integer> {
        @CommandLine.Spec
        CommandLine.Model.CommandSpec spec;

        @Override
        public Integer call() {
            Path home = Path.of(System.getProperty("user.home"));
            List<Path> caches = List.of(
                    home.resolve(".m2").resolve("repository"),
                    home.resolve(".gradle").resolve("caches"),
                    home.resolve(".gradle").resolve("wrapper").resolve("dists"),
                    home.resolve(".npm").resolve("_cacache"),
                    home.resolve(".cargo").resolve("registry"),
                    home.resolve(".cache").resolve("yarn"),
                    home.resolve("Library").resolve("Caches").resolve("Yarn"),
                    home.resolve("AppData").resolve("Local").resolve("pnpm-cache"));
            PrintWriter out = spec.commandLine().getOut();
            long total = 0;
            for (Path cache : caches) {
                if (Files.isDirectory(cache)) {
                    long size = Scanner.sizeOf(cache);
                    total += size;
                    out.printf("%10s  %s%n", Units.formatSize(size), cache);
                }
            }
            out.printf("%10s  total%n", Units.formatSize(total));
            out.println();
            out.println("These are shared by all projects; clear them with your build tool if needed");
            out.println("(e.g. 'npm cache clean --force', or delete ~/.m2/repository and let Maven re-download).");
            out.flush();
            return 0;
        }
    }
}
