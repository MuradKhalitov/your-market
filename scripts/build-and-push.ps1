param(
    [Parameter(Mandatory = $true)]
    [string]$DockerHubUsername,

    [string]$ImageName = "your-market",

    [string]$Version = "1.0.0",

    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"

$fullImageName = "$DockerHubUsername/$ImageName"
$versionImage = "${fullImageName}:${Version}"
$latestImage = "${fullImageName}:latest"

try {
    Write-Host "==> Checking Java"

    $javaVersion = cmd.exe /c "java -version 2>&1" | Out-String

    Write-Host $javaVersion.Trim()

    if ($javaVersion -notmatch '"21\.') {
        throw "Java 21 is required."
    }

    Write-Host "==> Checking Docker"

    docker info

    if ($LASTEXITCODE -ne 0) {
        throw "Docker daemon is not available."
    }

    if ($SkipTests) {
        Write-Host "==> Building JAR without tests"

        .\mvnw.cmd clean package -DskipTests
    }
    else {
        Write-Host "==> Running tests and building JAR"

        .\mvnw.cmd clean verify
    }

    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed."
    }

    Write-Host "==> Building Docker image: $versionImage"

    docker build -t $versionImage .

    if ($LASTEXITCODE -ne 0) {
        throw "Docker build failed."
    }

    Write-Host "==> Tagging latest"

    docker tag $versionImage $latestImage

    if (-not [string]::IsNullOrWhiteSpace($env:DOCKERHUB_TOKEN)) {
        Write-Host "==> Login to Docker Hub"

        $env:DOCKERHUB_TOKEN |
            docker login `
                --username $DockerHubUsername `
                --password-stdin
    }
    else {
        Write-Host "==> Interactive Docker Hub login"

        docker login --username $DockerHubUsername
    }

    if ($LASTEXITCODE -ne 0) {
        throw "Docker Hub login failed."
    }

    Write-Host "==> Pushing version tag"

    docker push $versionImage

    if ($LASTEXITCODE -ne 0) {
        throw "Push failed: $versionImage"
    }

    Write-Host "==> Pushing latest tag"

    docker push $latestImage

    if ($LASTEXITCODE -ne 0) {
        throw "Push failed: $latestImage"
    }

    Write-Host ""
    Write-Host "Release completed successfully."
    Write-Host "Version image: $versionImage"
    Write-Host "Latest image:  $latestImage"
}
catch {
    Write-Error "Release stopped: $($_.Exception.Message)"
    exit 1
}