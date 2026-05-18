#!/usr/bin/env pwsh
# Runs Maven inside the official maven:3.9-eclipse-temurin-17 image.
# The repo is bind-mounted at /work and Maven's local cache is kept in the
# named volume gene-evolution-m2 so dependencies persist across runs.

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

docker run --rm `
    -v "${repoRoot}:/work" -w /work `
    -v gene-evolution-m2:/root/.m2 `
    maven:3.9-eclipse-temurin-17 `
    mvn @args
