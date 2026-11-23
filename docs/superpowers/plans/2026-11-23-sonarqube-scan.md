# SonarQube Scan (Source-Only) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the two config files that let the GitLab copy of this repo run a source-only SonarQube analysis and a gitleaks secrets scan.

**Architecture:** No application code changes. `sonar-project.properties` tells the scanner which Kotlin and Swift directories are source and which are tests. `.gitlab-ci.yml` includes the shared SonarQube CI component and adds one gitleaks job, both in a single `scan` stage.

**Tech Stack:** GitLab CI (shared component `sonarqube@1.0.0`), SonarScanner CLI properties file, gitleaks v8.30.1.

**Spec:** `docs/superpowers/specs/2026-11-23-sonarqube-scan-design.md`

## Global Constraints

- The GitHub repo is public: no internal hostnames, colleague names, ticket numbers or tokens in file contents, commit messages or PR text. `$CI_SERVER_FQDN` is allowed.
- `sonar.projectKey` is the literal placeholder `<REPLACE_WITH_SONARQUBE_PROJECT_KEY>`, angle brackets included. Do not invent a key.
- Never push to the `gitlab` remote. Never read, print, create or change the `SONAR_TOKEN` / `SONAR_HOST_URL` CI variables. Do not create the SonarQube project.
- Work on branch `chore/sonarqube-scan` off `origin/main`. Open a PR with `gh`; do not merge it.
- No single-letter variable names in any script or command written for this work.
- Commit messages end with `Co-Authored-By: Claude Fable 5.2 <noreply@anthropic.com>`.
- If gitleaks reports anything, stop and report to the owner. Do not commit around a finding.

## Review Focus

These cannot be exercised by a local test, so each is covered by a comment in the file, a line in the PR description, or a local check as noted.

1. **Placeholder key never replaced.** The scan job fails, but `allow-failure: true` turns that into a warning. Covered by a comment above the key (Task 1) and step 2 of the owner's list in the PR (Task 4).
2. **CI variables or SonarQube project missing on first sync.** Same symptom as above. Covered by the header comment in `.gitlab-ci.yml` (Task 2) and the PR description (Task 4).
3. **A listed source or test path is removed later.** Whether the scanner fails on a missing path is not documented. Covered by the path check in Task 1 step 2, which can be re-run at any time.
4. **A secret already in git history.** The `secrets` job is not report-only and would fail the first pipeline. Covered by the local full-history run in Task 3.
5. **First quality gate fails on never-scanned code.** Expected, and the reason for `allow-failure: true`. Covered by the comment in `.gitlab-ci.yml` (Task 2) and the PR description (Task 4).

---

### Task 1: `sonar-project.properties`

**Files:**
- Create: `sonar-project.properties`

- [ ] **Step 1: Create the file with exactly this content**

```properties
# PLACEHOLDER: the real key exists only once the SonarQube project has been created.
# Replace the whole value, angle brackets included, before the first sync to GitLab.
# The brackets are deliberate: they are outside the characters a project key accepts, so the
# placeholder cannot pass for a real key.
sonar.projectKey=<REPLACE_WITH_SONARQUBE_PROJECT_KEY>
sonar.projectName=PENTANA Mobile
sonar.qualitygate.wait=true

# Source-only scan: Kotlin and Swift are read as source, with no build and no compiled classes.
# Paths only; wildcards are not allowed in sonar.sources and sonar.tests.
sonar.sources=shared/src/commonMain,shared/src/androidMain,shared/src/iosMain,androidApp/src/main,iosApp/iosApp
sonar.tests=shared/src/commonTest,shared/src/iosTest,androidApp/src/test,iosApp/iosAppTests,iosApp/iosAppUITests

# Build output and generated files. A fresh CI clone has none of these and the scanner already
# skips git-ignored files; this keeps a scan run from a local working tree equivalent.
sonar.exclusions=**/build/**,**/.gradle/**,**/.kotlin/**,**/DerivedData/**,**/.swiftpm/**,**/Pods/**,**/xcuserdata/**,**/*.xcassets/**
sonar.sourceEncoding=UTF-8
```

- [ ] **Step 2: Verify every listed path holds tracked files**

Run:

```bash
for property_name in sonar.sources sonar.tests; do
  grep "^${property_name}=" sonar-project.properties | cut -d= -f2 | tr ',' '\n' | while read -r scan_path; do
    file_count=$(git ls-tree -r --name-only origin/main -- "$scan_path" | wc -l | tr -d ' ')
    echo "$property_name $scan_path $file_count"
  done
done
```

Expected: ten lines, five per property, every count greater than zero.

- [ ] **Step 3: Verify no path is listed as both source and test**

Run: `grep -E '^sonar\.(sources|tests)=' sonar-project.properties | cut -d= -f2 | tr ',' '\n' | sort | uniq -d`
Expected: no output.

- [ ] **Step 4: Commit**

```bash
git add sonar-project.properties
git commit -m "chore: add SonarQube project properties (source-only)"
```

### Task 2: `.gitlab-ci.yml`

**Files:**
- Create: `.gitlab-ci.yml`

- [ ] **Step 1: Create the file with exactly this content**

```yaml
# Security scanning only. This file is used by the GitLab copy of the
# repository; development (PRs, reviews) happens on GitHub.
#
# Required CI/CD variables (Settings > CI/CD > Variables, masked):
#   SONAR_HOST_URL  e.g. https://sonarqube.example.internal
#   SONAR_TOKEN     project analysis token from SonarQube

# ── SonarQube Analysis ───────────────────────────────────────────────
# needs: [] is deliberate — this is a source-only scan. No build runs and
# no coverage report is produced in this pipeline, so there is nothing for
# the job to wait on or import.
include:
  - component: $CI_SERVER_FQDN/silentmode/components/sonarqube@1.0.0
    inputs:
      stage: scan
      needs: []
      # Report-only until the first baseline is reviewed; flip to false to
      # make a failing quality gate fail the pipeline.
      allow-failure: true

stages:
  - scan

# Full history so gitleaks checks every commit and Sonar can attribute blame.
variables:
  GIT_DEPTH: "0"

secrets:
  stage: scan
  image:
    name: zricethezav/gitleaks:v8.30.1
    entrypoint: [""]
  script:
    - gitleaks git --redact --verbose .
```

- [ ] **Step 2: Verify the YAML parses and has the expected shape**

Run:

```bash
python3 - <<'PYTHON'
import yaml

with open(".gitlab-ci.yml", encoding="utf-8") as pipeline_file:
    pipeline = yaml.safe_load(pipeline_file)

component_include = pipeline["include"][0]
assert component_include["component"] == "$CI_SERVER_FQDN/silentmode/components/sonarqube@1.0.0"
assert component_include["inputs"] == {"stage": "scan", "needs": [], "allow-failure": True}
assert pipeline["stages"] == ["scan"]
assert pipeline["variables"] == {"GIT_DEPTH": "0"}
assert pipeline["secrets"]["image"] == {"name": "zricethezav/gitleaks:v8.30.1", "entrypoint": [""]}
assert pipeline["secrets"]["script"] == ["gitleaks git --redact --verbose ."]
assert "dependencies" not in pipeline
print("pipeline shape OK:", sorted(pipeline))
PYTHON
```

Expected: `pipeline shape OK: ['include', 'secrets', 'stages', 'variables']`

- [ ] **Step 3: Commit**

```bash
git add .gitlab-ci.yml
git commit -m "ci: add SonarQube and gitleaks scan pipeline for the GitLab copy"
```

### Task 3: Local secrets scan and public-repo check

**Files:** none changed.

- [ ] **Step 1: Run gitleaks over the full history of this branch and every `origin` branch**

Run: `gitleaks git --redact --verbose --log-opts="chore/sonarqube-scan --remotes=origin" .`
Expected: `no leaks found`. On any finding: stop, report it to the owner, change nothing.

- [ ] **Step 2: Run gitleaks over the local snapshot of the GitLab copy's branches**

The GitLab copy has its own commit hashes, so this is the history the CI job reads. Read-only: do not fetch from or push to that remote.

Run: `gitleaks git --redact --verbose --log-opts="--remotes=gitlab" .`
Expected: `no leaks found`. Same rule on any finding.

- [ ] **Step 3: Check the two config files for internal details**

Run: `git diff origin/main...HEAD -- .gitlab-ci.yml sonar-project.properties | grep -nEi 'https?://|\.net|\.internal|token'`
Expected: exactly two lines, the generic `sonarqube.example.internal` example and the `SONAR_TOKEN` variable name in the CI header comment. Read the two docs by eye for the same.

### Task 4: Pull request

- [ ] **Step 1: Push the branch to `origin` only**

Run: `git push -u origin chore/sonarqube-scan`

- [ ] **Step 2: Open the PR with `gh pr create --base main`**

The description must contain: what the two files do; that this is source-only (no build, tests or coverage) and why; that the project key is a placeholder; the documented precision cost for Kotlin without a classpath; what was validated locally (YAML shape, path check, gitleaks result); a plain statement that the pipeline itself has not run and cannot be verified until the owner's steps are done; the expected first-scan gate failure; and the three remaining owner steps:

1. Ask DevOps to create the SonarQube project and set the two CI variables on the GitLab project.
2. Put the real project key into `sonar-project.properties`.
3. Merge the PR, then sync to GitLab so the first pipeline runs.

End the description with `🤖 Generated with [Claude Code](https://claude.com/claude-code)`. Do not merge.
