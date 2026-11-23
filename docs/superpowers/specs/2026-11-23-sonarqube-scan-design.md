# SonarQube security scan (source-only): design

## Why

The sibling web repo (pentana-system) is already scanned by the company SonarQube server on every
push to its GitLab copy. This repo is not scanned at all. The aim is the same baseline here: a
static security and quality scan of the Kotlin and Swift code, plus a secrets scan of the git
history, with as little new machinery as possible.

## Goals

- Every push to the GitLab copy runs a SonarQube analysis of the Kotlin and Swift source.
- Every push runs gitleaks over the full git history.
- Same shape as the sibling repo, so there is one pattern to maintain across both.
- Report-only at first: a failing quality gate shows as a warning, not a failed pipeline.

## Non-goals

- No build, no tests and no coverage in CI (see Option B below).
- No dependency audit job. The sibling runs `composer audit`; Gradle has no built-in equivalent,
  and adding a plugin for it is a Gradle change outside this work. Possible follow-up.
- No change to the development flow: GitHub stays the source of truth for code, PRs and reviews.
- No GitHub Actions workflow.

## Anti-goals

- Internal details in this public repo: no hostnames, no tokens, no names, no ticket numbers.
- A project key that looks real but is not. A wrong key that looks right is worse than an obvious
  placeholder.
- Claiming the pipeline works before it has run once.

## Constraints

- The GitHub repo is public. The scan runs on a private GitLab copy that only the owner updates.
  The config files are committed here and travel to GitLab with the next sync.
- The SonarQube server is Enterprise Edition, so Kotlin and Swift are both analysed.
- The SonarQube project does not exist yet and the GitLab project has no `SONAR_HOST_URL` or
  `SONAR_TOKEN` variables. Both are created by DevOps. The project key is therefore unknown.
- The scan job comes from a shared CI component (`sonarqube@1.0.0`). Its internals were not
  inspected for this design; it is used exactly as the sibling repo uses it, and it is assumed to
  run the plain SonarScanner CLI against `sonar-project.properties`, as it does there.

## Approaches

**Option A, source-only (picked).** The scanner reads Kotlin and Swift files as text. No Gradle
build, no Android SDK image, no Mac runner. Two new files, nothing else changes. Fast, and
identical in shape to the sibling repo. The cost is analysis precision for Kotlin (see "Scanner
behaviour" below) and no coverage figures.

**Option B, build + Kotlin tests + coverage.** Run the Gradle build and unit tests in CI, produce
coverage with Kover or Jacoco, and analyse through the SonarScanner for Gradle, which hands the
scanner the compiled classes and dependency classpath. More precise Kotlin analysis and real
coverage numbers. It needs an Android build image, Gradle changes in this repo and a much slower
pipeline, and iOS tests still could not run without a Mac runner, so Swift coverage would stay
empty.

**Decision:** the owner picked Option A. Option B is deferred and can be a later step if coverage
or more precise Kotlin analysis is asked for.

## What gets added

### `.gitlab-ci.yml`

- The shared SonarQube component include with `stage: scan`, `needs: []` and
  `allow-failure: true`, with the same explanatory comments as the sibling.
- One stage, `scan`.
- `GIT_DEPTH: "0"`, so gitleaks sees every commit and SonarQube can attribute blame.
- A `secrets` job: image `zricethezav/gitleaks:v8.30.1` with an empty entrypoint, running
  `gitleaks git --redact --verbose .`.
- No `dependencies` job.

### `sonar-project.properties`

| Property | Value |
|---|---|
| `sonar.projectKey` | `REPLACE_WITH_SONARQUBE_PROJECT_KEY` (placeholder, see below) |
| `sonar.projectName` | `PENTANA Mobile` |
| `sonar.qualitygate.wait` | `true` |
| `sonar.sourceEncoding` | `UTF-8` |
| `sonar.sources` | `shared/src/commonMain`, `shared/src/androidMain`, `shared/src/iosMain`, `androidApp/src/main`, `iosApp/iosApp` |
| `sonar.tests` | `shared/src/commonTest`, `shared/src/iosTest`, `androidApp/src/test`, `iosApp/iosAppTests`, `iosApp/iosAppUITests` |
| `sonar.exclusions` | `**/build/**`, `**/.gradle/**`, `**/.kotlin/**`, `**/DerivedData/**`, `**/.swiftpm/**`, `**/Pods/**`, `**/xcuserdata/**`, `**/*.xcassets/**` |

All ten paths were checked against `origin/main` and each holds tracked files (116 Kotlin and 27
Swift files in total; there are no `.java` files). `sonar.sources` and `sonar.tests` take plain
paths only; wildcards are not allowed there.

The four Gradle build scripts (`*.gradle.kts`) sit outside these paths and are left out on purpose:
they are build configuration, not app code.

Calls made while writing this, open to correction:

- **The exclusions are a safety net, not a necessity.** A fresh CI clone has no build output, the
  scanner already skips files ignored by git by default (`sonar.scm.exclusions.disabled` defaults
  to `false`), and the source paths are narrow. The list mirrors `.gitignore` so a local scan run
  from a working tree with build output behaves the same.
- **`**/*.xcassets/**` is excluded.** The asset catalog holds only images and Xcode-generated
  `Contents.json` files.
- **No `sonar.kotlin.source.version`.** The project is on Kotlin 2.1, inside the supported range,
  so the default is left alone.

### The project key placeholder

The real key only exists once DevOps creates the project; the sibling's has the form
`<namespace>_<repo>_<uuid>`. The file ships with `REPLACE_WITH_SONARQUBE_PROJECT_KEY` and a comment
above it. Until it is replaced, the scan job fails. Because the job is `allow-failure: true`, that
failure shows only as a warning on the pipeline, so it is easy to miss: the key has to be filled
in before the first sync to GitLab.

## Scanner behaviour for a source-only Kotlin scan

Looked up in the SonarQube Server documentation on 2026-11-23. The pages read are labelled 2026.5;
the server runs 2026.1, so small differences are possible.

- **`sonar.java.binaries` is not needed.** It is mandatory for the Java analyser only ("Compiled
  .class files are required for Java projects with more than one Java file"; the scan fails
  otherwise). The Kotlin page does not mention it, and this repo has no `.java` files.
- **Kotlin uses `sonar.java.libraries`, and it is recommended, not required.** For scans that do
  not go through the Gradle or Maven scanner, the Kotlin page says it is "strongly recommended" to
  provide the dependency binaries "to improve the analysis accuracy". Without a build there are no
  dependency jars to point at, so it is left unset.
- **The docs do not spell out what is lost.** Neither the Kotlin page nor the analyser's README
  says which rules weaken or drop out without a classpath. The README does say that some security
  rules use semantic information. So the fair expectation is: the scan runs and reports, but
  rules that need to resolve types from libraries may miss issues. The first scan log should be
  read for warnings about this.
- **The docs advise against this scanner for Gradle projects.** The SonarScanner CLI page says not
  to use the CLI "for projects built with Maven, Gradle, or .NET" because it will "degrade the
  quality of your analysis", and to use the dedicated scanner instead. That is the precision cost
  of Option A, stated by the vendor. It does not block the scan; it is the main reason Option B
  exists as a later step.
- **Swift needs no build.** The Swift page lists no required properties and no build step. Swift
  is included from Developer Edition up, so Enterprise covers it. The project uses Swift 5, inside
  the supported range.
- **Not confirmed by the docs:** whether a path in `sonar.sources` or `sonar.tests` that does not
  exist fails the scan. All ten paths exist today, but if a source set is removed later its entry
  should be removed in the same change.

## Expected first-scan behaviour

The quality gate requires zero new issues and reviewed security hotspots. On the first scan of
code that has never been analysed, everything counts as new, so the gate is likely to fail. That
is why the job starts as `allow-failure: true`. Once the first baseline has been reviewed and
cleaned up, a later change can flip it to `false` so a failing gate fails the pipeline.

The gitleaks job is not report-only: a finding fails that job from day one. A local run over the
full history is part of validating this change, so a finding surfaces before the files are
committed, not on the first pipeline.

## Open dependencies

Nothing here can be verified end to end until all three are done:

1. DevOps creates the SonarQube project and sets `SONAR_HOST_URL` and `SONAR_TOKEN` on the GitLab
   project.
2. The owner replaces the placeholder with the real project key.
3. The owner merges and syncs to GitLab, which triggers the first pipeline.

Until then the only checks available are local: the YAML parses, the listed paths exist, and
gitleaks passes on the full history.

## Possible follow-ups

- Flip `allow-failure` to `false` after the first baseline is reviewed.
- Option B: Gradle build, Kotlin tests and coverage through the SonarScanner for Gradle.
- A dependency vulnerability audit for the Gradle dependencies.
