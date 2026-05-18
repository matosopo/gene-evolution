#!/usr/bin/env bash
set -euo pipefail

port="${1:-8080}"
repo_root="$(cd "$(dirname "$0")/.." && pwd)"

docker run --rm -it \
    -v "${repo_root}:/work" -w /work \
    -p "${port}:8080" \
    eclipse-temurin:17-jre \
    java -jar target/gene-evolution-1.0.0.jar --serve 8080
