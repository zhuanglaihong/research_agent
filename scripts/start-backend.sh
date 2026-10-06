#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="$project_root/.env"

# Read simple KEY=value assignments without executing the file as shell code.
# Values may contain '='; quotes and variable interpolation are not supported.
if [[ -f "$env_file" ]]; then
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    [[ "$line" =~ ^[[:space:]]*$ || "$line" =~ ^[[:space:]]*# ]] && continue
    if [[ "$line" =~ ^([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]]; then
      export "${BASH_REMATCH[1]}=${BASH_REMATCH[2]}"
    else
      printf 'Invalid .env assignment. Use unquoted KEY=value lines.\n' >&2
      exit 1
    fi
  done < "$env_file"
fi

if ! command -v java >/dev/null 2>&1; then
  printf 'JDK 21 is required. Set JAVA_HOME and add its bin directory to PATH.\n' >&2
  exit 1
fi

cd -- "$project_root"
exec bash "$project_root/mvnw" -B -ntp spring-boot:run
