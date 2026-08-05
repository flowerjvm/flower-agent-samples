$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$workspaceRoot = Split-Path -Parent $repositoryRoot
$mavenWrapper = Join-Path $workspaceRoot "flower-agent\mvnw.cmd"

$projects = @(
    "flower",
    "flower-agent",
    "flower-ai-harness",
    "flower-action-runtime"
)

if (-not (Test-Path -LiteralPath $mavenWrapper)) {
    throw "Maven wrapper not found: $mavenWrapper"
}

foreach ($project in $projects) {
    $projectDirectory = Join-Path $workspaceRoot $project
    $pom = Join-Path $projectDirectory "pom.xml"
    if (-not (Test-Path -LiteralPath $pom)) {
        throw "Required sibling checkout not found: $pom"
    }
    Write-Host "Installing $project snapshot"
    Push-Location -LiteralPath $projectDirectory
    try {
        & $mavenWrapper -q -DskipTests install
        if ($LASTEXITCODE -ne 0) {
            throw "Snapshot install failed for $project"
        }
    } finally {
        Pop-Location
    }
}

Write-Host "Flower integration snapshots are ready in Maven Local."
