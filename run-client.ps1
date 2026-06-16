#!/usr/bin/env pwsh
# Launches RuneLite locally with this plugin sideloaded (com.banktaglayouts.ExamplePluginTest).
# JDK is pinned to 11 via gradle.properties (org.gradle.java.home), so no JAVA_HOME juggling needed.
#
# Usage:
#   .\run-client.ps1            # launch the client
#   .\run-client.ps1 -Info      # launch with Gradle --info (verbose) output
param(
    [switch]$Info
)

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

$gradleArgs = @('runClient', '--console=plain')
if ($Info) { $gradleArgs += '--info' }

# Pre-flight: Jagex-account dev login relies on ~/.runelite/credentials.properties, which RuneLite
# writes when launched via the Jagex launcher with the --insecure-write-credentials client arg.
# The dev client reads it automatically; warn (don't block) if it's missing so login isn't a mystery.
$credFile = Join-Path $HOME '.runelite\credentials.properties'
if (Test-Path $credFile) {
    Write-Host "Jagex dev credentials found ($credFile) - client should auto-login." -ForegroundColor Green
} else {
    Write-Host "No Jagex dev credentials at $credFile - you'll see the login screen." -ForegroundColor Yellow
    Write-Host "  To enable auto-login (one-time): Start menu > 'RuneLite (configure)' >" -ForegroundColor Yellow
    Write-Host "  Client arguments: --insecure-write-credentials > Save, then launch RuneLite" -ForegroundColor Yellow
    Write-Host "  once via the Jagex launcher. Do NOT commit/share that credentials file." -ForegroundColor Yellow
}

Write-Host "Launching RuneLite (com.banktaglayouts.ExamplePluginTest)..." -ForegroundColor Cyan
& "$PSScriptRoot\gradlew.bat" @gradleArgs
exit $LASTEXITCODE
