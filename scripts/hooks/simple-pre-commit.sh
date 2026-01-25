#!/bin/bash
#
# Simple pre-commit hook for IPTV Android App
# Runs code quality checks before commit
#
# This can be installed manually or via Gradle:
#   cp scripts/hooks/simple-pre-commit.sh .git/hooks/pre-commit-quality
#   chmod +x .git/hooks/pre-commit-quality
#
# Then modify .git/hooks/pre-commit to call this script after BD hooks
#

echo "🔍 Running code quality checks..."

# Only run on Kotlin file changes
STAGED_KT_FILES=$(git diff --cached --name-only --diff-filter=ACM | grep -E '\.(kt|kts)$' || true)

if [ -z "$STAGED_KT_FILES" ]; then
    echo "✓ No Kotlin files changed, skipping quality checks"
    exit 0
fi

# Run Spotless check (quick formatting check)
echo "  Checking code formatting..."
./gradlew spotlessCheck --console=plain --quiet 2>&1 > /dev/null

if [ $? -ne 0 ]; then
    echo ""
    echo "❌ Code formatting issues found!"
    echo ""
    echo "Fix with: ./gradlew spotlessApply"
    echo "Or skip with: git commit --no-verify"
    echo ""
    exit 1
fi

echo "✓ Code quality checks passed"
exit 0
