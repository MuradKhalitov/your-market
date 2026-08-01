[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [AllowEmptyString()]
    [string]$DockerHubRepository,

    [switch]$PublishStagingAlias,

    [switch]$NoPush,

    [switch]$NoCache
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Assert-RepositoryName {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Repository
    )

    if ([string]::IsNullOrWhiteSpace($Repository)) {
        throw "DockerHubRepository must not be empty."
    }

    if ($Repository -match '\s|@|://|:') {
        throw "DockerHubRepository must be an untagged repository name without spaces, URL scheme, digest, port, or tag."
    }

    if ($Repository -notmatch '^[a-z0-9]+(?:[._-][a-z0-9]+)*/[a-z0-9]+(?:[._-][a-z0-9]+)*$') {
        throw "DockerHubRepository has an invalid format. Example: muradkhalitov/your-market"
    }
}

function Assert-CommandExists {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Command
    )

    if ($null -eq (Get-Command $Command -ErrorAction SilentlyContinue)) {
        throw "Required command '$Command' was not found in PATH."
    }
}

function Get-NativeExitCode {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Command,

        [string[]]$Arguments = @()
    )

    $savedErrorActionPreference = $ErrorActionPreference

    try {
        # Некоторые native-команды выводят служебные сообщения в stderr.
        $ErrorActionPreference = "Continue"
        $discardedOutput = & $Command @Arguments 2>&1
        return $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $savedErrorActionPreference
    }
}

$originalLocation = Get-Location
$exitCode = 0

try {
    Assert-RepositoryName -Repository $DockerHubRepository

    Assert-CommandExists -Command "git"
    Assert-CommandExists -Command "docker"

    if ((Get-NativeExitCode -Command "docker" -Arguments @("buildx", "version")) -ne 0) {
        throw "Docker Buildx is unavailable. Enable Docker Buildx in Docker Desktop."
    }

    if ((Get-NativeExitCode -Command "docker" -Arguments @("info")) -ne 0) {
        throw "Docker daemon is unavailable. Start Docker Desktop."
    }

    $repoRoot = (
        & git rev-parse --show-toplevel 2>$null |
            Select-Object -First 1
    ).Trim()

    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
        throw "Current location is not inside a Git repository."
    }

    Set-Location -LiteralPath $repoRoot

    foreach ($requiredFile in @("Dockerfile", ".dockerignore", "pom.xml")) {
        $requiredFilePath = Join-Path $repoRoot $requiredFile

        if (-not (Test-Path -LiteralPath $requiredFilePath -PathType Leaf)) {
            throw "Required file '$requiredFile' is missing from the repository root."
        }
    }

    $commit = (
        & git rev-parse --short=7 HEAD 2>$null |
            Select-Object -First 1
    ).Trim()

    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($commit)) {
        throw "Unable to determine the current Git commit."
    }

    $workingTree = @(& git status --porcelain 2>$null)

    if ($LASTEXITCODE -ne 0) {
        throw "Unable to read Git working tree status."
    }

    if ($workingTree.Count -gt 0 -and -not $NoPush) {
        throw "Working tree is not clean. Commit or stash changes before publishing an image."
    }

    if ($workingTree.Count -gt 0 -and $NoPush) {
        Write-Warning "Working tree is dirty. The local image will still be tagged with commit '$commit'."
    }

    $immutableTag = "${DockerHubRepository}:${commit}"
    $stagingTag = "${DockerHubRepository}:staging"

    $tags = @($immutableTag)

    if ($PublishStagingAlias) {
        $tags += $stagingTag
    }

    Write-Host ""
    Write-Host "YourMarket image publication"
    Write-Host "============================"
    Write-Host "Repository:   $DockerHubRepository"
    Write-Host "Git commit:   $commit"
    Write-Host "Platform:     linux/amd64"
    Write-Host "Push enabled: $(if ($NoPush) { 'no' } else { 'yes' })"
    Write-Host "No cache:     $(if ($NoCache) { 'yes' } else { 'no' })"
    Write-Host "Tags:"

    $tags | ForEach-Object {
        Write-Host "  $_"
    }

    if (-not $NoPush) {
        Write-Host ""
        Write-Host "Docker authentication must already be configured."
        Write-Host "Run 'docker login' before publishing when necessary."
    }

    $buildArguments = @(
        "buildx",
        "build",
        "--platform", "linux/amd64",
        "--file", (Join-Path $repoRoot "Dockerfile"),
        "--label", "org.opencontainers.image.title=YourMarket",
        "--label", "org.opencontainers.image.revision=$commit",
        "--tag", $immutableTag
    )

    if ($PublishStagingAlias) {
        $buildArguments += @(
            "--tag", $stagingTag
        )
    }

    if ($NoCache) {
        $buildArguments += "--no-cache"
    }

    if ($NoPush) {
        # Загружает собранный linux/amd64 image в локальный Docker.
        $buildArguments += "--load"
    }
    else {
        # Buildx сразу отправляет все теги в Docker Hub.
        $buildArguments += "--push"
    }

    $buildArguments += $repoRoot

    Write-Host ""
    Write-Host "==> Building YourMarket image"

    & docker @buildArguments

    if ($LASTEXITCODE -ne 0) {
        if ($NoPush) {
            throw "Local Docker image build failed."
        }

        throw "Docker image build or Docker Hub push failed. Run 'docker login' and verify repository permissions."
    }

    Write-Host ""

    if ($NoPush) {
        Write-Host "Image built locally. Registry push was skipped."
        Write-Host "Local image:"
        Write-Host $immutableTag
    }
    else {
        Write-Host "YourMarket image published successfully."
        Write-Host ""
        Write-Host "Immutable image:"
        Write-Host $immutableTag
        Write-Host ""
        Write-Host "Set on VPS in deploy/.env.prod:"
        Write-Host "YOURMARKET_IMAGE=$immutableTag"
        Write-Host ""
        Write-Host "Deploy command:"
        Write-Host "./deploy.sh $immutableTag"
    }

    if ($PublishStagingAlias) {
        Write-Host ""
        Write-Host "Additional staging alias:"
        Write-Host $stagingTag
    }

    # Удобно для вызова из release.ps1.
    Write-Output "RELEASE_IMAGE=$immutableTag"
}
catch {
    [Console]::Error.WriteLine("Error: $($_.Exception.Message)")
    $exitCode = 1
}
finally {
    Set-Location -LiteralPath $originalLocation
}

if ($exitCode -ne 0) {
    exit $exitCode
}