#!/bin/bash
#
# Pre-commit hook for IPTV Android App
# Runs static code analysis before allowing commits
#
# This script is called by the git pre-commit hook via BD
#

set -e

echo "🔍 Running pre-commit checks..."
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Track if any check failed
CHECKS_FAILED=0

# Function to print step
print_step() {
    echo -e "${BLUE}▶${NC} $1"
}

# Function to print success
print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

# Function to print error
print_error() {
    echo -e "${RED}✗${NC} $1"
}

# Function to print warning
print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

# Get list of staged Kotlin files
STAGED_KT_FILES=$(git diff --cached --name-only --diff-filter=ACM | grep -E '\.(kt|kts)$' || true)

if [ -z "$STAGED_KT_FILES" ]; then
    print_warning "No Kotlin files staged, skipping code analysis"
    exit 0
fi

echo "Staged Kotlin files:"
echo "$STAGED_KT_FILES" | while read -r file; do
    echo "  - $file"
done
echo ""

# ============================================
# 1. Check code formatting with Spotless
# ============================================
print_step "Checking code formatting (Spotless)..."

if ./gradlew spotlessCheck --daemon --quiet 2>&1 | grep -q "BUILD SUCCESSFUL"; then
    print_success "Code formatting check passed"
else
    print_error "Code formatting check failed"
    print_warning "Run './gradlew spotlessApply' to auto-fix formatting issues"
    CHECKS_FAILED=1
fi

echo ""

# ============================================
# 2. Run Detekt static analysis
# ============================================
print_step "Running static code analysis (Detekt)..."

# Run detekt only on changed files for faster checks
if ./gradlew detekt --daemon --quiet 2>&1 | grep -q "BUILD SUCCESSFUL"; then
    print_success "Static code analysis passed"
else
    print_error "Static code analysis found issues"
    print_warning "Check reports at: app/build/reports/detekt/detekt.html"
    print_warning "Run './gradlew detekt' to see details"
    # Don't fail on detekt issues initially (ignoreFailures = true in config)
    # CHECKS_FAILED=1
    print_warning "Detekt issues found but not blocking commit (see config)"
fi

echo ""

# ============================================
# 3. Run ktlint check (quick style check)
# ============================================
print_step "Checking Kotlin code style (ktlint)..."

if ./gradlew ktlintCheck --daemon --quiet 2>&1 | grep -q "BUILD SUCCESSFUL"; then
    print_success "Code style check passed"
else
    print_warning "Code style check found issues"
    print_warning "Run './gradlew ktlintFormat' to auto-fix style issues"
    # Don't fail on ktlint issues (we use spotless as primary formatter)
    # CHECKS_FAILED=1
    print_warning "ktlint issues found but not blocking commit"
fi

echo ""

# ============================================
# Final result
# ============================================
if [ $CHECKS_FAILED -eq 1 ]; then
    echo ""
    print_error "Pre-commit checks failed!"
    echo ""
    echo "To fix issues:"
    echo "  1. Run: ./gradlew spotlessApply"
    echo "  2. Run: ./gradlew detekt"
    echo "  3. Fix any remaining issues"
    echo "  4. Stage your changes: git add ."
    echo "  5. Try committing again"
    echo ""
    echo "To skip these checks (not recommended):"
    echo "  git commit --no-verify"
    echo ""
    exit 1
else
    echo ""
    print_success "All pre-commit checks passed! ✨"
    echo ""
    exit 0
fi
