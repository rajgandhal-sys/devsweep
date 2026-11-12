# devsweep

[![CI](https://github.com/rajgandhal-sys/devsweep/actions/workflows/ci.yml/badge.svg)](https://github.com/rajgandhal-sys/devsweep/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Find and delete the build junk sitting in projects you stopped working on months ago:
`node_modules`, Maven `target`, Gradle `build` and `.gradle`, Cargo `target`, Next.js `.next`.

Works the same on **macOS, Windows and Linux**. One jar, Java 17+.

```text
$ devsweep scan ~/code --older-than 3m
      SIZE  ACTIVE      KIND                    PATH
    1.2 GB  8mo ago     npm/yarn/pnpm deps      /Users/me/code/old-dashboard/node_modules
  640.3 MB  5mo ago     Gradle build            /Users/me/code/android-demo/build
  212.8 MB  1y ago      Maven build             /Users/me/code/spring-petclinic/target
    2.0 GB  total across 3 directories

Run 'devsweep clean' with the same options to delete these.
```

## Install

Download `devsweep.jar` from the [latest release](https://github.com/rajgandhal-sys/devsweep/releases/latest), then:

```bash
java -jar devsweep.jar scan ~/code
```

Optional alias (macOS/Linux, add to `~/.zshrc` or `~/.bashrc`):

```bash
alias devsweep='java -jar ~/bin/devsweep.jar'
```

## Usage

```bash
devsweep scan [DIR...]                 # list artifacts, biggest first (deletes nothing)
devsweep scan ~/code --older-than 90d  # only projects untouched for 90+ days
devsweep scan ~/code --min-size 100MB  # only big ones
devsweep clean ~/code --older-than 6m --dry-run   # preview a cleanup
devsweep clean ~/code --older-than 6m             # delete, after a y/N prompt
devsweep caches                        # size of ~/.m2, ~/.gradle, ~/.npm ... (read-only)
```

Ages: `30d`, `6w`, `3m` (months), `1y`. Sizes: `500KB`, `50MB`, `1.5GB`.

## How it stays safe

- **Marker files required.** A `target` folder only counts if a `pom.xml` (or `Cargo.toml`) sits next to it; `build` needs `build.gradle(.kts)`; `node_modules` needs `package.json`. A random folder called `build` is never touched.
- **Re-checked before deleting.** Each directory is checked again right before deletion.
- **Links are never followed.** Symlinks and Windows junctions inside `node_modules` (pnpm, `npm link`) are removed as links; whatever they point to is left alone.
- **Confirmation by default.** `clean` asks before deleting unless you pass `--yes`.
- **Global caches are read-only.** `devsweep caches` only reports sizes.

"Active" is the newest of: the project's marker file, its `.git/index` (updated on commits, checkouts and `git status`), and the artifact directory itself (updated on builds).

## Build from source

```bash
./mvnw verify          # Windows: mvnw.cmd verify
java -jar target/devsweep.jar --help
```

## License

[MIT](LICENSE)
