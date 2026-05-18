#!/usr/bin/env pwsh
# Runs the packaged fat jar inside the slim eclipse-temurin:17-jre image.
# Defaults to examples/default.json; pass a different path as the first arg.

param([string]$Config = "examples/default.json")

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

docker run --rm `
    -v "${repoRoot}:/work" -w /work `
    eclipse-temurin:17-jre `
    java -jar target/gene-evolution-1.0.0.jar $Config
