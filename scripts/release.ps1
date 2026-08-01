param(
    [Parameter(Mandatory = $true)][string]$DockerHubUsername,
    [Parameter(Mandatory = $true)][string]$VpsHost,
    [string]$VpsUser = "root",
    [string]$ImageName = "your-market",
    [string]$Version,
    [string]$RemoteDirectory = "/opt/yourmarket",
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

try {
    if (-not (Get-Command ssh -ErrorAction SilentlyContinue)) { throw "ssh was not found in PATH." }
    if ([string]::IsNullOrWhiteSpace($Version)) {
        $commit = (& git -C (Join-Path $PSScriptRoot '..') rev-parse --short=7 HEAD 2>$null | Select-Object -First 1)
        $Version = (Get-Date -Format 'yyyyMMdd-HHmmss') + $(if ($commit) { "-$($commit.Trim())" } else { '' })
    }
    $buildScript = Join-Path $PSScriptRoot 'build-and-push.ps1'
    $arguments = @{ DockerHubUsername = $DockerHubUsername; ImageName = $ImageName; Version = $Version }
    if ($SkipTests) { $arguments.SkipTests = $true }
    & $buildScript @arguments
    if ($LASTEXITCODE -ne 0) { throw "Build and push failed." }

    $image = "docker.io/$DockerHubUsername/$ImageName`:$Version"
    $remote = "cd '$RemoteDirectory' && ./deploy.sh '$image'"
    Write-Host "==> Deploying $image to $VpsUser@$VpsHost"
    & ssh "$VpsUser@$VpsHost" $remote
    if ($LASTEXITCODE -ne 0) { throw "Remote deployment failed with exit code $LASTEXITCODE." }
} catch {
    Write-Error "Release failed: $($_.Exception.Message)"
    exit 1
}
