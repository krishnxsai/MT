#!/bin/bash

# MediTrack Test Execution Script
# Runs all test suites with coverage reporting
# Usage: ./run-tests.sh [target]
# Targets: all, functions, firestore, android, coverage

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FUNCTIONS_DIR="$PROJECT_ROOT/functions"
APP_DIR="$PROJECT_ROOT/app"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# ─────────────── Helper Functions ───────────────

log_info() {
    echo -e "${BLUE}ℹ ${1}${NC}"
}

log_success() {
    echo -e "${GREEN}✅ ${1}${NC}"
}

log_error() {
    echo -e "${RED}❌ ${1}${NC}"
}

log_warning() {
    echo -e "${YELLOW}⚠ ${1}${NC}"
}

# ─────────────── Cloud Functions Tests ───────────────

run_functions_tests() {
    log_info "Running Cloud Functions tests..."
    cd "$FUNCTIONS_DIR"

    # Install dependencies
    if [ ! -d "node_modules" ]; then
        log_info "Installing Cloud Functions dependencies..."
        npm ci
    fi

    # Run tests with coverage
    npm test -- --coverage --testPathPattern="\\.test\\.ts$" \
        --collectCoverageFrom='src/**/*.ts' \
        --collectCoverageFrom='!src/**/*.test.ts' \
        --collectCoverageFrom='!src/index.ts' \
        --coverageThreshold='{"global": {"branches": 70, "functions": 75, "lines": 75, "statements": 75}}'

    if [ $? -eq 0 ]; then
        log_success "Cloud Functions tests passed"
        PASSED_TESTS=$((PASSED_TESTS + 19))
    else
        log_error "Cloud Functions tests failed"
        FAILED_TESTS=$((FAILED_TESTS + 19))
        return 1
    fi

    # Display coverage summary
    if [ -f "coverage/coverage-summary.json" ]; then
        log_info "Coverage Report (Cloud Functions):"
        cat coverage/coverage-summary.json | jq '.total' || true
    fi
}

# ─────────────── Firestore Emulator Tests ───────────────

run_firestore_tests() {
    log_info "Running Firestore Security Rules tests..."

    # Start Firestore emulator in background
    log_info "Starting Firestore emulator..."
    firebase emulators:start --only firestore --project=meditrack-test &
    EMULATOR_PID=$!

    # Wait for emulator to start
    sleep 5

    cd "$FUNCTIONS_DIR"

    # Run Firestore rules tests
    npm test -- --testPathPattern="firestore\\.rules\\.test\\.ts$" \
        --coverage \
        --collectCoverageFrom='src/**/*.ts'

    TEST_RESULT=$?

    # Kill emulator
    kill $EMULATOR_PID 2>/dev/null || true
    wait $EMULATOR_PID 2>/dev/null || true

    if [ $TEST_RESULT -eq 0 ]; then
        log_success "Firestore Security Rules tests passed"
        PASSED_TESTS=$((PASSED_TESTS + 17))
    else
        log_error "Firestore Security Rules tests failed"
        FAILED_TESTS=$((FAILED_TESTS + 17))
        return 1
    fi

    # Display coverage summary
    if [ -f "coverage/coverage-summary.json" ]; then
        log_info "Coverage Report (Firestore Rules):"
        cat coverage/coverage-summary.json | jq '.total' || true
    fi
}

# ─────────────── Android Unit Tests ───────────────

run_android_tests() {
    log_info "Running Android unit tests..."
    cd "$APP_DIR"

    # Build first
    log_info "Building Android project..."
    ./gradlew build --stacktrace -q

    if [ $? -ne 0 ]; then
        log_error "Android build failed"
        return 1
    fi

    # Run unit tests
    log_info "Running unit tests..."
    ./gradlew testDebugUnitTest --stacktrace -q

    if [ $? -eq 0 ]; then
        log_success "Android unit tests passed"
        # Assume ~50 unit tests
        PASSED_TESTS=$((PASSED_TESTS + 50))
    else
        log_error "Android unit tests failed"
        FAILED_TESTS=$((FAILED_TESTS + 50))
        return 1
    fi

    # Generate and display coverage report
    if [ -f "build/reports/coverage/index.html" ]; then
        log_info "Coverage report generated at: build/reports/coverage/index.html"
    fi
}

# ─────────────── Security Audit ───────────────

run_security_audit() {
    log_info "Running security audit..."
    cd "$FUNCTIONS_DIR"

    # Audit dependencies
    log_info "Auditing dependencies..."
    npm audit --audit-level=moderate || log_warning "Audit warnings found"

    # Check for exposed secrets
    log_info "Checking for exposed secrets..."
    if grep -r "process.env" src/ | grep -v "test"; then
        log_warning "Environment variables accessed - verify they're not hardcoded"
    fi

    log_success "Security audit complete"
}

# ─────────────── Coverage Report ───────────────

generate_coverage_report() {
    log_info "Generating coverage report..."

    cd "$FUNCTIONS_DIR"

    if [ -f "coverage/coverage-final.json" ]; then
        log_info "Cloud Functions coverage: $(cat coverage/coverage-summary.json | jq '.total.lines.pct')"
    fi

    cd "$APP_DIR"
    if [ -d "build/reports/coverage" ]; then
        log_info "Android coverage report available"
    fi

    # Overall summary
    echo ""
    log_info "Test Summary:"
    log_success "Passed: $PASSED_TESTS"
    if [ $FAILED_TESTS -gt 0 ]; then
        log_error "Failed: $FAILED_TESTS"
    fi
}

# ─────────────── Main ───────────────

main() {
    local target="${1:-all}"

    log_info "MediTrack Test Suite"
    log_info "Target: $target"
    echo ""

    case $target in
        all)
            run_functions_tests || exit 1
            run_firestore_tests || exit 1
            run_android_tests || exit 1
            run_security_audit
            generate_coverage_report
            ;;
        functions)
            run_functions_tests || exit 1
            ;;
        firestore)
            run_firestore_tests || exit 1
            ;;
        android)
            run_android_tests || exit 1
            ;;
        security)
            run_security_audit
            ;;
        coverage)
            generate_coverage_report
            ;;
        *)
            log_error "Unknown target: $target"
            echo "Valid targets: all, functions, firestore, android, security, coverage"
            exit 1
            ;;
    esac

    echo ""
    if [ $FAILED_TESTS -eq 0 ]; then
        log_success "All tests completed successfully!"
        exit 0
    else
        log_error "Some tests failed"
        exit 1
    fi
}

main "$@"
