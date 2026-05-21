#!/usr/bin/env pwsh
# Runs the packaged fat jar in --serve mode inside the slim eclipse-temurin:17-jre image.
# Browser → http://localhost:<Port> (default 8080).
# Stop with Ctrl+C (-it keeps Jetty's shutdown clean).

param([int]$Port = 8080)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

docker run --rm -it `
    -v "${repoRoot}:/work" -w /work `
    -p "${Port}:8080" `
    eclipse-temurin:17-jre `
    java -jar target/gene-evolution-1.0.0.jar --serve 8080
