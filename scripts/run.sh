#!/usr/bin/env bash
set -euo pipefail

config="${1:-examples/default.json}"
repo_root="$(cd "$(dirname "$0")/.." && pwd)"

docker run --rm \
    -v "${repo_root}:/work" -w /work \
    eclipse-temurin:17-jre \
    java -jar target/gene-evolution-1.0.0.jar "${config}"
