# Automated Release Process for Gradle Java Projects on GitHub

This document outlines the setup for an automated release process using GitHub Actions and the `net.researchgate.release` Gradle plugin. This process will handle versioning, Git tagging, committing changes, pushing to your repository, building artifacts, and creating a draft GitHub Release.

## 1. Project Setup and Permissions

To enable the automated release process, ensure your GitHub repository is correctly configured.

### A. GitHub Repository Setup

*   **Create a Repository**: If you haven't already, create a new GitHub repository for your Gradle Java project.
*   **Main Branch**: Ensure your primary development branch is named `main` (or `master`). This is the branch from which releases will be triggered.

### B. GitHub Actions Permissions

For the GitHub Actions workflow to perform necessary operations (like pushing commits, creating tags, creating GitHub Releases, and creating Pull Requests), it requires specific permissions.

1.  **Navigate to Repository Settings**: In your GitHub repository, go to `Settings` > `Actions` > `General`.
2.  **Workflow Permissions**:
    *   Under "Workflow permissions", select "**Read and write permissions**".
    *   Ensure "**Allow GitHub Actions to create and approve pull requests**" is checked.
3.  **Save Changes**: Click "Save" at the bottom of the page.

### C. Branch Protection Rules (Recommended)

To prevent accidental direct pushes and ensure changes go through the workflow, set up branch protection for your `main` branch.

1.  **Navigate to Branch Settings**: In your GitHub repository, go to `Settings` > `Branches`.
2.  **Add Rule**: Click "Add branch protection rule".
3.  **Branch Name Pattern**: Enter `main` (or your primary branch name).
4.  **Enable the following**:
    *   `Require a pull request before merging`: This ensures all code changes go through a review.
    *   `Require status checks to pass before merging`: Add your CI build workflow here.
    *   `Require linear history`: Prevents merge commits.
    *   `Do not allow bypassing the above settings`: For strict enforcement.
5.  **Create**: Click "Create".

## 2. Gradle Project Configuration (`gradle.properties` and `build.gradle.kts`)

The `net.researchgate.release` plugin is central to automating the versioning and Git operations within your Gradle project.

### A. `gradle.properties`

Ensure your project's version is managed in `gradle.properties` (in your project root), as this is where the `net.researchgate.release` plugin will read and update the version.

```properties
# gradle.properties
version=0.0.1-SNAPSHOT
group=com.example.playground-gradle-release-manual-actions
```

### B. `build.gradle.kts`

Add the `net.researchgate.release` plugin and configure it.

```kotlin
// build.gradle.kts

plugins {
    java
    application
    id("org.cyclonedx.bom") version "2.3.1"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("net.researchgate.release") version "3.1.0" // Check for the latest version on Gradle Plugin Portal
}

group = project.group
version = project.version

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("info.picocli:picocli:4.7.0")
}

application {
    mainClass.set("com.example.demo.DemoApplication")
}

tasks.jar {
    manifest {
        attributes("Implementation-Version" to project.version)
        attributes("Main-Class" to "com.example.demo.DemoApplication")
    }
}

// Configuration for the net.researchgate.release plugin
release {
    git {
        requireBranch.set("main")
        // Do not push the post-release commit (next snapshot version) directly.
        // This will be handled by a separate PR in GitHub Actions.
        pushPostReleaseVersion.set(false)
        // Optional: Customize commit messages and tag names
        releaseCommitMessage.set("Release: v${project.version}")
        tagName.set("v${project.version}")
    }

    buildTasks.set(listOf("clean", "build"))
}

tasks.withType<JavaCompile> {
    options.isDeprecation = true
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
    }
}

// Disable the default cyclonedxBom task: https://github.com/CycloneDX/cyclonedx-gradle-plugin/issues/596
tasks.named("cyclonedxBom") {
    enabled = false
}

// Example: gradle sbom; vk-sbom-diff sbom-1.json sbom.json
tasks.register("sbom", org.cyclonedx.gradle.CycloneDxTask::class) {
    setIncludeConfigs(listOf("runtimeClasspath"))
    setProjectType("application")
    setSchemaVersion("1.6")
    setDestination(project.file("."))
    setOutputName("sbom")
    setOutputFormat("json")
    setIncludeBomSerialNumber(false)
    setIncludeLicenseText(false)
}
```

## 3. GitHub Actions Workflow (`.github/workflows/release.yml`)

This workflow will be triggered manually and will execute the Gradle release process.

```yaml
# .github/workflows/release.yml
name: Automated Release

on:
  workflow_dispatch:
    inputs:
      release_version:
        description: 'The version number for the new release (e.g., 1.0.0)'
        required: true
        type: string
      next_snapshot_version:
        description: 'The next development snapshot version (e.g., 1.0.1-SNAPSHOT)'
        required: true
        type: string

jobs:
  release:
    runs-on: ubuntu-latest
    permissions:
      contents: write # Required to push commits, create tags, and create GitHub Releases
      pull-requests: write # Required to create pull requests

    steps:
    - name: Checkout code
      uses: actions/checkout@v4
      with:
        fetch-depth: 0 # Important: Fetches full history, needed by Gradle release plugin

    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        distribution: 'temurin'
        java-version: '17'

    - name: Grant execute permission for gradlew
      run: chmod +x gradlew

    - name: Configure Git User and Email
      # This ensures commits made by the action use the initiator's details
      run: |
        git config user.name "github-actions[bot]"
        git config user.email "github-actions[bot]@users.noreply.github.com"

    - name: Run Gradle Release Plugin
      # This will update the version to release_version, commit, tag, and push.
      # It will also update to next_snapshot_version locally but NOT push it.
      run: ./gradlew release -PreleaseVersion=${{ github.event.inputs.release_version }} -PpostReleaseVersion=${{ github.event.inputs.next_snapshot_version }}
      env:
        GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

    - name: Build and Package Artifacts
      # This step builds your project after the version has been updated to the release version
      # and before the GitHub Release is created.
      run: ./gradlew clean build

    - name: Get Release Tag
      id: get_release_tag
      run: echo "TAG=$(git describe --tags --abbrev=0)" >> $GITHUB_OUTPUT

    - name: Create Draft GitHub Release
      # Uses the softprops/action-gh-release action to create a GitHub Release.
      # It will be a draft, allowing manual review before publishing.
      uses: softprops/action-gh-release@v2
      if: steps.get_release_tag.outputs.TAG != '' # Only run if a tag was found
      with:
        tag_name: ${{ steps.get_release_tag.outputs.TAG }}
        name: Release ${{ steps.get_release_tag.outputs.TAG }}
        body: |
          ## Release Notes for ${{ steps.get_release_tag.outputs.TAG }}

          This is an automated draft release. Please review the changes and publish when ready.

          **Key Changes:**
          * (Add your changelog content here, or use a separate action to generate it)
          * See commit history for detailed changes.
        draft: true # Creates a draft release
        prerelease: false
        files: |
          build/libs/*.jar # Adjust this path to where your JARs are located
          # Add other artifacts here, e.g., build/distributions/*.zip
      env:
        GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }} # Required for creating the GitHub Release

    - name: Create Pull Request for Next Snapshot Version
      uses: peter-evans/create-pull-request@v6
      with:
        token: ${{ secrets.GITHUB_TOKEN }}
        commit-message: "Prepare for next development iteration (v${{ github.event.inputs.next_snapshot_version }})"
        title: "Prepare for next development iteration (v${{ github.event.inputs.next_snapshot_version }})"
        body: "This PR updates the project version to the next snapshot version after a release."
        branch: "feature/next-snapshot-version-${{ github.event.inputs.next_snapshot_version }}"
        delete-branch: true
        base: "main"
```

## 4. How to Make a Release

This section explains how to trigger the automated release process.

### A. Trigger via GitHub Actions UI

1.  **Navigate to Actions Tab**: In your GitHub repository, click on the "Actions" tab.
2.  **Select Workflow**: In the left sidebar, click on "Automated Release" (the name of the workflow you created).
3.  **Run Workflow Manually**: On the right side, click the "Run workflow" dropdown button.
4.  **Provide Inputs**:
    *   `release_version`: Enter the desired version number for the release (e.g., `1.0.0`).
    *   `next_snapshot_version`: Enter the version number for the next development cycle, typically incremented from the release version and appended with `-SNAPSHOT` (e.g., `1.0.1-SNAPSHOT`).
5.  **Run Workflow**: Click the "Run workflow" button.

The workflow will now start. You can monitor its progress in the "Actions" tab. Once completed:

*   A new commit for the release version and a Git tag (`vX.Y.Z`) will be pushed to your `main` branch.
*   A draft GitHub Release will be created under the "Releases" section of your repository (accessible from the "Code" tab on the right sidebar). This draft release will include your built JAR artifacts. You can then review and publish it.
*   A new Pull Request will be created, proposing the change to the `next_snapshot_version` in `gradle.properties`. Review and merge this PR to update your `main` branch for continued development.

### B. Trigger via GitHub CLI

You can also trigger and monitor your release workflow using the GitHub CLI (`gh`).

1.  **Install GitHub CLI**:
    *   On macOS with Homebrew: `brew install gh`
    *   On Debian/Ubuntu: `sudo apt update && sudo apt install gh`
    *   For other OS, refer to [https://cli.github.com/manual/installation](https://cli.github.com/manual/installation)
2.  **Authenticate GitHub CLI**:
    *   Log in to your GitHub account: `gh auth login`
    *   Follow the prompts to authenticate your CLI with your GitHub account.
3.  **Trigger the Release Workflow**:
    *   Navigate to your project's root directory in your terminal. Then, use the `gh workflow run` command:
        ```bash
        gh workflow run release.yml -f release_version="1.0.0" -f next_snapshot_version="1.0.1-SNAPSHOT"
        ```
    *   Replace `release.yml` with the actual filename of your workflow if it's different.
    *   Adjust `1.0.0` and `1.0.1-SNAPSHOT` to your desired versions.
4.  **Monitor the Workflow**:
    *   You can list your workflow runs: `gh run list`
    *   To view the details and logs of a specific run: `gh run view <run_id>`
        *   Replace `<run_id>` with the ID of the workflow run you want to inspect (e.g., `gh run view 123456789`).

## 5. Automated Release Process Details

This automated process leverages the following:

*   **`net.researchgate.release` Gradle Plugin**: This plugin handles the core logic of version management and Git operations directly within your Gradle project. When triggered, it performs the following sequence:
    1.  **Version Update (Release)**: Changes the project version in `gradle.properties` to the specified `release_version`.
    2.  **Release Commit**: Creates a Git commit with a message like "Release: vX.Y.Z".
    3.  **Git Tagging**: Creates a lightweight Git tag (e.g., `v1.0.0`) at the release commit.
    4.  **Push Release**: Pushes both the release commit and the new tag to your remote GitHub repository.
    5.  **Version Update (Next Snapshot)**: Changes the project version to the `next_snapshot_version` (e.g., `1.0.1-SNAPSHOT`) **locally**.
*   **GitHub Actions**: Orchestrates the entire process. It checks out your code, sets up the Java environment, executes the Gradle release tasks, builds your project, and then uses:
    *   `softprops/action-gh-release` to create a formal GitHub Release entry.
    *   `peter-evans/create-pull-request` to create a Pull Request for the next snapshot version change.
*   **Draft GitHub Release**: The `softprops/action-gh-release` action is configured to create a draft release. This gives you an opportunity to review the release notes, attached artifacts, and overall release before making it public.
*   **Pull Request for Next Snapshot**: Instead of directly pushing the next snapshot version, a Pull Request is created. This allows for review and ensures that the `main` branch remains protected and changes are properly integrated.
