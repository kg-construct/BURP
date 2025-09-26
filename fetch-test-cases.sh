#!/usr/bin/env bash
set -Eeuo pipefail

# --- config ---
target_dir="target"
resources_dir="src/test/resources"
repos=(
  "rml-core"
  "rml-cc"
  "rml-io"
  "rml-fnml"
  "rml-lv"
  "rml-star"
)

trap 'echo; echo "Error occurred."; echo "• Check the repository URLs and your internet connection."; echo "• Verify you have permissions to create/write to \"$target_dir\"."; exit 1' ERR

# Check prerequisites
command -v git >/dev/null 2>&1 || { echo "git is required but not found in PATH."; exit 1; }

echo "Creating $target_dir directory..."
mkdir -p "$target_dir"

echo "Entering $target_dir..."
cd "$target_dir"

# Clone all repositories
for repo in "${repos[@]}"; do
  echo "Cloning https://github.com/kg-construct/$repo ..."
  git clone "https://github.com/kg-construct/$repo"
done

echo "All repositories cloned successfully into $target_dir"

# Copy test-cases into ../src/test/resources/<repo>
echo "Copying test-case directories to ../$resources_dir ..."
for repo in "${repos[@]}"; do
  src="$repo/test-cases"
  dest="../$resources_dir/$repo"
  if [[ -d "$src" ]]; then
    mkdir -p "$dest"
    # -a to preserve attrs; -r for recursive; --no-target-directory not needed here
    cp -a "$src/." "$dest/"
    echo "• Copied $src -> $dest"
  else
    echo "• Skipped $repo (no test-cases directory found)"
  fi
done

# Remove cloned repositories
echo "Cleaning up cloned repositories..."
for repo in "${repos[@]}"; do
  rm -rf "./$repo"
done

echo "Done."
