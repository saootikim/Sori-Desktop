# Sori Desktop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the SimpMusic fork as "Sori" for Windows: a user-scope MSI built on GitHub Actions, whose in-app update check points at saootikim/Sori-Desktop.

**Architecture:** All Sori changes live in the main repo. The `core` submodule stays pristine. The update check is replaced with a Koin override (`SoriUpdateRepository`) registered after core's modules. Packaging uses Compose Desktop's jpackage MSI with libmpv bundled through `appResourcesRootDir`. Upstream's Conveyor/msix pipeline is left in place but its workflows are disabled.

**Tech Stack:** Kotlin Multiplatform / Compose Desktop, Koin 4.2, kotlinx.serialization, java.net.http, jpackage + WiX (via Compose Gradle plugin), GitHub Actions `windows-latest`.

**Spec:** `SORI.md` (repo root)

## Global Constraints

- Never modify files under `core/`. It is a submodule pointing at maxrave-dev/core.
- `version-name` format: `X.Y.Z-sori.R`. MSI version: `X.Y.(Z*100+R)`.
- Release tag: `v<version-name>`. MSI asset name: `Sori-<msiVersion>.msi`.
- Update repo: `saootikim/Sori-Desktop`. Download link: `https://github.com/saootikim/Sori-Desktop/releases/latest`.
- Fixed MSI `upgradeUuid`: `6f1f2a4e-5b1d-4c7e-9a3b-50a1c0de5071`.
- Build with JDK 21 from an ASCII path (`C:\dev\sori-desktop`).
- Commit messages end with `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.

---

### Task 1: Rebrand to Sori (name, icons, packaging)

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/app_name.xml`
- Modify: `composeApp/src/jvmMain/kotlin/com/maxrave/simpmusic/CrashDialog.kt:73`
- Replace: `composeApp/icon/circle_app_icon.png`, `composeApp/icon/circle_app_icon.ico`
- Replace: `composeApp/src/commonMain/composeResources/drawable/circle_app_icon.png`, `.../drawable/app_icon.png`
- Modify: `desktopApp/build.gradle.kts` (`nativeDistributions` block)
- Modify: `gradle/libs.versions.toml` (`version-name`)

**Interfaces:**
- Produces: `soriMsiVersion: String` (build script value), `packageName = "Sori"`, which yields MSI file `Sori-<soriMsiVersion>.msi`.

- [ ] **Step 1: App name and crash dialog title**

`app_name.xml`: `<string name="app_name" translatable="false">Sori</string>`
`CrashDialog.kt:73`: `title = "Sori - Unexpected Error"`

- [ ] **Step 2: Render icons with PIL** (the same design as Android Sori)

- 1024px `circle_app_icon.png`: a circle with a 45° gradient from `#5B2BD6` (bottom-left) to `#FF6B6B` (top-right), plus five white rounded bars.
  - Bar geometry on a 108 grid, as (x, y1, y2): (32,49,59) (43,41,67) (54,35,73) (65,43,65) (76,48,60). Bar width 7, fully rounded.
- `.ico` with sizes 16/24/32/48/64/128/256.
- Copy the PNG to both drawable files.

- [ ] **Step 3: Versioning and MSI settings** in `desktopApp/build.gradle.kts`

Add this above `compose.desktop {`:
```kotlin
// Sori: version-name is "X.Y.Z-sori.R"; MSI only accepts numeric MAJOR.MINOR.BUILD.
val soriMsiVersion: String =
    libs.versions.version.name.get().let { v ->
        val m = Regex("""^(\d+)\.(\d+)\.(\d+)-sori\.(\d+)$""").matchEntire(v)
            ?: error("version-name must look like 2.1.0-sori.1, got '$v'")
        val (major, minor, patch, rev) = m.destructured
        "$major.$minor.${patch.toInt() * 100 + rev.toInt()}"
    }
```
In `nativeDistributions`: set `packageName = "Sori"` and `vendor = "saootikim"`. Change the `windows { }` block to:
```kotlin
windows {
    includeAllModules = true
    packageVersion = soriMsiVersion
    msiPackageVersion = soriMsiVersion
    upgradeUuid = "6f1f2a4e-5b1d-4c7e-9a3b-50a1c0de5071"
    perUserInstall = true
    menu = true
    menuGroup = "Sori"
    shortcut = true
    dirChooser = false
    iconFile.set(rootDir.resolve("composeApp/icon/circle_app_icon.ico"))
}
```
`libs.versions.toml`: `version-name = "2.1.0-sori.1"`

- [ ] **Step 4: Stage mpv and run the app**

Run: `./gradlew :composeApp:mpvSetupAll`, then `./gradlew :desktopApp:run`
Expected: a window titled "Sori" with the Sori icon; searching and playing a song works (audio plays).

- [ ] **Step 5: Commit** — `feat: rebrand desktop app as Sori`

### Task 2: Point the update check at Sori-Desktop releases

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/com/maxrave/simpmusic/sori/SoriUpdateRepository.kt`
- Create: `composeApp/src/jvmTest/kotlin/com/maxrave/simpmusic/sori/SoriUpdateRepositoryTest.kt`
- Modify: `composeApp/src/jvmMain/kotlin/com/maxrave/simpmusic/DesktopApp.kt` (inside `startKoin { }`, after `loadKoinModules(viewModelModule)`)
- Modify: `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/App.kt:756`

**Interfaces:**
- Consumes: `com.maxrave.domain.repository.UpdateRepository`, `UpdateData(tagName, releaseTime, body)`, `Resource.Success/Error`
- Produces: `class SoriUpdateRepository : UpdateRepository`, `internal fun parseLatestRelease(json: String): UpdateData`, `val soriUpdateModule: org.koin.core.module.Module`

- [ ] **Step 1: Write the failing test**
```kotlin
class SoriUpdateRepositoryTest {
    @Test
    fun parsesGithubLatestRelease() {
        val json = """{"tag_name":"v2.1.0-sori.2","published_at":"2026-09-21T12:00:00Z","body":"notes","assets":[]}"""
        val data = parseLatestRelease(json)
        assertEquals("v2.1.0-sori.2", data.tagName)
        assertEquals("2026-09-21T12:00:00Z", data.releaseTime)
        assertEquals("notes", data.body)
    }

    @Test
    fun toleratesNullBody() {
        val data = parseLatestRelease("""{"tag_name":"v1","published_at":null,"body":null}""")
        assertEquals("v1", data.tagName)
        assertEquals("", data.body)
    }
}
```
- [ ] **Step 2: Run the test and confirm it fails.** Run `./gradlew :composeApp:jvmTest --tests "*SoriUpdateRepositoryTest*"`. Expected: compilation failure, because `parseLatestRelease` does not exist yet.
- [ ] **Step 3: Implement**
```kotlin
package com.maxrave.simpmusic.sori

internal const val SORI_RELEASES_API = "https://api.github.com/repos/saootikim/Sori-Desktop/releases/latest"
const val SORI_RELEASES_PAGE = "https://github.com/saootikim/Sori-Desktop/releases/latest"

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val body: String? = null,
)

private val json = Json { ignoreUnknownKeys = true }

internal fun parseLatestRelease(body: String): UpdateData =
    json.decodeFromString<GithubRelease>(body).let {
        UpdateData(tagName = it.tagName.orEmpty(), releaseTime = it.publishedAt, body = it.body.orEmpty())
    }

class SoriUpdateRepository(
    private val client: HttpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build(),
) : UpdateRepository {
    override fun checkForGithubReleaseUpdate(): Flow<Resource<UpdateData>> =
        flow {
            val result = runCatching {
                val request = HttpRequest.newBuilder(URI(SORI_RELEASES_API))
                    .header("Accept", "application/vnd.github+json").GET().build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                check(response.statusCode() == 200) { "GitHub returned ${response.statusCode()}" }
                parseLatestRelease(response.body())
            }
            emit(result.fold({ Resource.Success(it) }, { Resource.Error(it.message ?: "Update check failed") }))
        }.flowOn(Dispatchers.IO)

    // Sori is not on F-Droid; route both channels to GitHub releases.
    override fun checkForFdroidUpdate(): Flow<Resource<UpdateData>> = checkForGithubReleaseUpdate()
}

val soriUpdateModule = module { single<UpdateRepository> { SoriUpdateRepository() } }
```
In `DesktopApp.kt`, after `loadKoinModules(viewModelModule)`, add `loadKoinModules(soriUpdateModule)` with the comment: `// Sori: overrides core's UpdateRepository (upstream checks maxrave-dev/SimpMusic)`.
In `App.kt:756`, change `openUrl("https://simpmusic.org/download")` to `openUrl("https://github.com/saootikim/Sori-Desktop/releases/latest")`. App.kt is commonMain, so the URL is inlined there.
- [ ] **Step 4: Run the test and confirm it passes.** Same command as step 2. Expected: PASS.
- [ ] **Step 5: Commit** — `feat: check saootikim/Sori-Desktop for desktop updates`

### Task 3: MSI build and release workflow

**Files:**
- Create: `.github/workflows/sori-desktop-release.yml`

**Interfaces:**
- Consumes: `soriMsiVersion` naming from Task 1 (MSI at `desktopApp/build/compose/binaries/main-release/msi/Sori-*.msi`)

- [ ] **Step 1: Build the MSI locally.** Run `./gradlew :desktopApp:packageReleaseMsi`. Expected: `Sori-2.1.1.msi` exists.
- [ ] **Step 2: Install and run it.** Install the MSI (it installs per user under `%LOCALAPPDATA%`), launch "Sori" from the Start menu, and confirm that playback works. This proves libmpv is loaded from `compose.application.resources.dir`.
- [ ] **Step 3: Write the workflow.**
  - Triggers: `workflow_dispatch`, plus a push to `dev` that touches `gradle/libs.versions.toml`.
  - Job `check-version` (`ubuntu-latest`): compare `version-name` between HEAD and HEAD^. On `workflow_dispatch`, always release.
  - Job `build` (`windows-latest`): checkout with submodules, set up JDK 21 (temurin), `gradle/actions/setup-gradle`, `./gradlew :composeApp:mpvSetupAll`, `./gradlew :desktopApp:packageReleaseMsi`, upload the MSI artifact.
  - Job `release` (`ubuntu-latest`, `contents: write`): run `gh release create v<version-name> --title <version-name> --latest <msi>`.
- [ ] **Step 4: Commit** — `ci: build and release Sori MSI on windows-latest`

### Task 4: Publish and verify end to end

- [ ] **Step 1: Push and disable upstream workflows.** Push `dev` to origin. Then disable the four upstream workflows with `gh workflow disable`: `android.yml`, `android-release.yml`, `desktop-package.yml`, `pr-triage.yml`.
- [ ] **Step 2: First release.** Run `gh workflow run sori-desktop-release.yml`, then watch it. Expected: release `v2.1.0-sori.1` with `Sori-2.1.1.msi` attached.
- [ ] **Step 3: Update-path check.** The locally installed build (`2.1.0-sori.1`) should show no update prompt, because the tag equals `v2.1.0-sori.1`. The prompt itself gets verified with the next revision bump.
- [ ] **Step 4: Commit docs.** Commit `SORI.md` and this plan with the message `docs: Sori desktop fork notes and plan`.
