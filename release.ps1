<#
.SYNOPSIS
  Builds Herald for each loader against each supported Minecraft series and collects the jars into
  releases/.

.DESCRIPTION
  One jar is cut per Minecraft series (26.1, 26.2, 26.3, ...) per loader (Fabric, NeoForge). For each
  series the newest game version in that series is compiled against (26.1 -> 26.1.2, a not-yet-released
  series -> its latest RC/snapshot). The matching fabric-api, NeoForge and NeoForm (vanilla, used to
  compile the shared common/ code) builds are looked up from their maven metadata. All of them are
  overridden per run via Gradle -P properties, so the tracked gradle.properties (the everyday dev target)
  is never modified. The build itself derives the "+mc<series>-<loader>" version suffix, each loader's
  minecraft range and the src/versions/<series>/ overlay from minecraft_version.

  NeoForge only publishes builds for full releases, so a series that is still in RC/snapshot gets a
  Fabric jar only.

.PARAMETER Versions
  Exact Minecraft versions to build against, one per series. Defaults to the supported set.

.PARAMETER Loaders
  Which loaders to build: fabric, neoforge, or both (the default).

.EXAMPLE
  .\release-matrix.ps1
  .\release-matrix.ps1 -Mod cascade
  .\release-matrix.ps1 -Mod cascade -Versions 26.2, 26.3
  .\release-matrix.ps1 -Loaders neoforge
#>
param(
    [string[]]$Versions = @("26.1.2", "26.2", "26.3"),
    [ValidateSet("fabric", "neoforge")]
    [string[]]$Loaders = @("fabric", "neoforge")
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$mods = @("herald")

function Get-MavenVersions([string]$url) {
    Write-Host "Fetching $url" -ForegroundColor DarkGray
    [xml]$metadata = Invoke-RestMethod $url
    return $metadata.metadata.versioning.versions.version
}

$allFabricApiVersions = Get-MavenVersions "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml"
$allNeoForgeVersions = Get-MavenVersions "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml"
$allNeoFormVersions = Get-MavenVersions "https://maven.neoforged.net/releases/net/neoforged/neoform/maven-metadata.xml"

$results = @()

foreach ($mcVersion in $Versions) {
    # 26.1.2 -> 26.1, 26.3-rc-3 -> 26.3.
    $series = ($mcVersion -replace '^(\d+\.\d+).*$', '$1')

    # fabric-api tags its builds with the series or an exact version inside it (+26.1.2, +26.3), so match either.
    $fabricApiVersion = $allFabricApiVersions |
        Where-Object { $_ -match ('\+' + [regex]::Escape($series) + '(\.\d+)?$') } | Select-Object -Last 1
    # NeoForge versions are the game version padded to three parts plus a build number: 26.2 -> 26.2.0.88.
    $neoPrefix = if ($mcVersion -match '^\d+\.\d+$') { "$mcVersion.0" } else { $mcVersion }
    $neoForgeVersion = $allNeoForgeVersions |
        Where-Object { $_ -match ('^' + [regex]::Escape($neoPrefix) + '\.\d+(-beta)?$') } | Select-Object -Last 1
    # NeoForm versions are the exact game version plus a build number: 26.2 -> 26.2-2.
    $neoFormVersion = $allNeoFormVersions |
        Where-Object { $_ -match ('^' + [regex]::Escape($mcVersion) + '-\d+$') } | Select-Object -Last 1

    if (-not $neoFormVersion) {
        Write-Warning "No NeoForm build published for $mcVersion (needed to compile common/) - skipping"
        foreach ($m in $mods) { $results += [pscustomobject]@{ Mod = $m; Minecraft = $mcVersion; Loader = "*"; Status = "no neoform build" } }
        continue
    }

    $tasks = @()
    $gradleProps = @("-Pminecraft_version=$mcVersion", "-Pneo_form_version=$neoFormVersion")
    foreach ($loader in $Loaders) {
        $loaderVersion = if ($loader -eq "fabric") { $fabricApiVersion } else { $neoForgeVersion }
        if (-not $loaderVersion) {
            Write-Warning "No $loader build published for $mcVersion - skipping $loader"
            foreach ($m in $mods) { $results += [pscustomobject]@{ Mod = $m; Minecraft = $mcVersion; Loader = $loader; Status = "no $loader build" } }
            continue
        }
        $tasks += ":${loader}:build"
        $gradleProps += if ($loader -eq "fabric") { "-Pfabric_api_version=$fabricApiVersion" } else { "-Pneoforge_version=$neoForgeVersion" }
    }
    if (-not $tasks) { continue }
    $builtLoaders = $tasks | ForEach-Object { $_.Split(':')[1] }

    foreach ($m in $mods) {
        Write-Host ""
        Write-Host "=== $m : Minecraft $mcVersion (series $series; fabric-api $fabricApiVersion; neoforge $neoForgeVersion; neoform $neoFormVersion) ===" -ForegroundColor Cyan
        $modDir = $root
        $outDir = Join-Path $root "releases"
        New-Item -ItemType Directory -Force -Path $outDir | Out-Null

        Push-Location $modDir
        try {
            # Windows PowerShell 5.1 wraps every native stderr line (the JVM prints warnings there) in an
            # ErrorRecord, which $ErrorActionPreference = Stop would turn into a terminating error. Relax it
            # for this one call and flatten everything to plain strings; $LASTEXITCODE is what matters.
            $prevEap = $ErrorActionPreference
            $ErrorActionPreference = "Continue"
            $buildLog = & .\gradlew.bat clean @tasks @gradleProps --console=plain 2>&1 |
                ForEach-Object { "$_" }
            $ErrorActionPreference = $prevEap

            if ($LASTEXITCODE -ne 0) {
                Write-Host "  FAILED" -ForegroundColor Red
                $failLog = Join-Path $outDir "build-$mcVersion.log"
                $buildLog | Out-File $failLog -Encoding utf8
                Write-Host "    full log: $failLog" -ForegroundColor Red
                $buildLog | Select-String -Pattern "error:|FAILURE|What went wrong" | Select-Object -First 8 |
                    ForEach-Object { Write-Host "    $_" -ForegroundColor Red }
                foreach ($loader in $builtLoaders) {
                    $results += [pscustomobject]@{ Mod = $m; Minecraft = $mcVersion; Loader = $loader; Status = "build failed" }
                }
                continue
            }

            # Each jar is already named <mod>-<mod_version>+mc<series>-<loader>.jar by the build.
            foreach ($loader in $builtLoaders) {
                $jar = Get-ChildItem (Join-Path $modDir "$loader\build\libs\*.jar") |
                    Where-Object { $_.Name -notlike "*-sources.jar" } | Select-Object -First 1
                Copy-Item $jar.FullName (Join-Path $outDir $jar.Name) -Force
                Write-Host "  -> releases\$($jar.Name)" -ForegroundColor Green
                $results += [pscustomobject]@{ Mod = $m; Minecraft = $mcVersion; Loader = $loader; Status = "ok"; Jar = $jar.Name }
            }
        } finally {
            Pop-Location
        }
    }
}

Write-Host ""
Write-Host "=== Summary ===" -ForegroundColor Cyan
$results | Format-Table -AutoSize
if ($results | Where-Object { $_.Status -ne "ok" }) { exit 1 }
