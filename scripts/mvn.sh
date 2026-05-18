#!/usr/bin/env bash
# Bash equivalent of scripts/mvn.ps1 for Git Bash / WSL users.
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"

docker run --rm \
    -v "${repo_root}:/work" -w /work \
    -v gene-evolution-m2:/root/.m2 \
    maven:3.9-eclipse-temurin-17 \
    mvn "$@"
