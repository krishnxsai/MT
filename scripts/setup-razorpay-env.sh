#!/bin/bash

##############################################################
# RAZORPAY ENVIRONMENT SETUP - AUTOMATED DEPLOYMENT SCRIPT
##############################################################

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
PROJECT_ID=${GCP_PROJECT_ID:-"meditrack-dev"}
REGION="us-central1"

##############################################################
# FUNCTIONS
##############################################################

print_header() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

##############################################################
# SETUP: LOCAL DEVELOPMENT
##############################################################

setup_local_env() {
    print_header "Setting Up Local Environment"

    # Check if .env.test exists
    if [ -f ".env.test" ]; then
        print_warning ".env.test already exists"
        read -p "Overwrite? (y/n) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            print_info "Skipping .env.test"
            return
        fi
    fi

    # Create .env.test
    print_info "Creating .env.test..."
    cat > .env.test <<EOF
# Razorpay Test Credentials (LOCAL DEV ONLY)
# ⚠️ DO NOT COMMIT - Add to .gitignore

RAZORPAY_KEY_ID=rzp_test_1234567890abcdef
RAZORPAY_KEY_SECRET=test_secret_abcdef1234567890
ENVIRONMENT=test
DEBUG=true
EOF

    print_success ".env.test created"

    # Verify .gitignore includes .env files
    if grep -q "\.env" .gitignore 2>/dev/null; then
        print_success ".env files already in .gitignore"
    else
        print_info "Adding .env to .gitignore..."
        cat >> .gitignore <<EOF

# Environment variables
.env
.env.test
.env.local
.env.*.local
.secrets/
secrets.json
EOF
        print_success "Added to .gitignore"
    fi

    # Create env.properties for assets
    print_info "Creating env.properties..."
    mkdir -p app/src/main/assets
    cat > app/src/main/assets/env.properties <<EOF
razorpay.key.id=rzp_test_1234567890abcdef
razorpay.key.secret=test_secret_abcdef1234567890
environment=test
debug=true
EOF

    print_success "env.properties created"
}

##############################################################
# SETUP: CLOUD SECRET MANAGER
##############################################################

setup_cloud_secrets() {
    print_header "Setting Up Cloud Secret Manager"

    print_info "Project: $PROJECT_ID"

    # Verify gcloud is installed
    if ! command -v gcloud &> /dev/null; then
        print_error "gcloud CLI not found. Install from https://cloud.google.com/sdk/docs/install"
        return 1
    fi

    # Set project
    gcloud config set project $PROJECT_ID
    print_success "Project set to $PROJECT_ID"

    # Read keys
    print_info "Enter your Razorpay test credentials"
    read -p "Key ID (rzp_test_...): " KEY_ID
    read -sp "Key Secret: " KEY_SECRET
    echo

    # Create secrets
    print_info "Creating secrets in Cloud Secret Manager..."

    # Check if secrets exist
    if gcloud secrets describe razorpay-key-id > /dev/null 2>&1; then
        print_warning "razorpay-key-id already exists"
        echo -n "$KEY_ID" | gcloud secrets versions add razorpay-key-id --data-file=-
    else
        gcloud secrets create razorpay-key-id \
            --replication-policy="automatic" \
            --data-file=- <<< "$KEY_ID"
    fi
    print_success "razorpay-key-id configured"

    if gcloud secrets describe razorpay-key-secret > /dev/null 2>&1; then
        print_warning "razorpay-key-secret already exists"
        echo -n "$KEY_SECRET" | gcloud secrets versions add razorpay-key-secret --data-file=-
    else
        gcloud secrets create razorpay-key-secret \
            --replication-policy="automatic" \
            --data-file=- <<< "$KEY_SECRET"
    fi
    print_success "razorpay-key-secret configured"
}

##############################################################
# SETUP: CLOUD FUNCTION PERMISSIONS
##############################################################

setup_cf_permissions() {
    print_header "Setting Up Cloud Function Permissions"

    # Get service account
    FUNCTIONS_SA="${PROJECT_ID}@appspot.gserviceaccount.com"
    print_info "Service Account: $FUNCTIONS_SA"

    # Grant access to razorpay-key-id
    print_info "Granting access to razorpay-key-id..."
    gcloud secrets add-iam-policy-binding razorpay-key-id \
        --member=serviceAccount:${FUNCTIONS_SA} \
        --role=roles/secretmanager.secretAccessor \
        --quiet > /dev/null
    print_success "razorpay-key-id access granted"

    # Grant access to razorpay-key-secret
    print_info "Granting access to razorpay-key-secret..."
    gcloud secrets add-iam-policy-binding razorpay-key-secret \
        --member=serviceAccount:${FUNCTIONS_SA} \
        --role=roles/secretmanager.secretAccessor \
        --quiet > /dev/null
    print_success "razorpay-key-secret access granted"
}

##############################################################
# DEPLOY: CLOUD FUNCTIONS
##############################################################

deploy_functions() {
    print_header "Deploying Cloud Functions"

    if [ ! -d "functions" ]; then
        print_error "functions directory not found"
        return 1
    fi

    cd functions

    print_info "Installing dependencies..."
    npm install --quiet

    print_info "Building TypeScript..."
    npm run build --quiet

    print_info "Deploying to Firebase..."
    firebase deploy --only functions --token="${FIREBASE_TOKEN:-}"

    cd ..
    print_success "Cloud Functions deployed"
}

##############################################################
# VERIFY: ALL CONFIGURATION
##############################################################

verify_setup() {
    print_header "Verifying Configuration"

    local all_ok=true

    # Check local files
    echo ""
    print_info "Checking local files..."
    if [ -f ".env.test" ]; then
        print_success ".env.test exists"
    else
        print_error ".env.test NOT found"
        all_ok=false
    fi

    if [ -f "app/src/main/assets/env.properties" ]; then
        print_success "env.properties exists"
    else
        print_error "env.properties NOT found"
        all_ok=false
    fi

    # Check Cloud Secrets
    echo ""
    print_info "Checking Cloud Secrets..."
    if gcloud secrets describe razorpay-key-id > /dev/null 2>&1; then
        VALUE=$(gcloud secrets versions access latest --secret=razorpay-key-id)
        print_success "razorpay-key-id exists (${VALUE:0:15}...${VALUE: -5})"
    else
        print_error "razorpay-key-id NOT found"
        all_ok=false
    fi

    if gcloud secrets describe razorpay-key-secret > /dev/null 2>&1; then
        print_success "razorpay-key-secret exists"
    else
        print_error "razorpay-key-secret NOT found"
        all_ok=false
    fi

    # Check Cloud Function Permissions
    echo ""
    print_info "Checking Cloud Function Permissions..."
    if gcloud secrets get-iam-policy razorpay-key-id 2>/dev/null | grep -q "appspot.gserviceaccount.com"; then
        print_success "Cloud Functions have access to razorpay-key-id"
    else
        print_error "Cloud Functions do NOT have access to razorpay-key-id"
        all_ok=false
    fi

    if gcloud secrets get-iam-policy razorpay-key-secret 2>/dev/null | grep -q "appspot.gserviceaccount.com"; then
        print_success "Cloud Functions have access to razorpay-key-secret"
    else
        print_error "Cloud Functions do NOT have access to razorpay-key-secret"
        all_ok=false
    fi

    # Summary
    echo ""
    if [ "$all_ok" = true ]; then
        print_success "All checks passed! ✨"
        return 0
    else
        print_error "Some checks failed. Please fix the issues above."
        return 1
    fi
}

##############################################################
# MAIN
##############################################################

main() {
    print_header "Razorpay Environment Setup"

    echo ""
    echo "This script will set up:"
    echo "  1. Local development environment (.env.test)"
    echo "  2. Google Cloud Secret Manager configuration"
    echo "  3. Cloud Function permissions"
    echo "  4. Deploy Cloud Functions"
    echo "  5. Verify everything"
    echo ""

    read -p "Continue? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        print_info "Cancelled"
        exit 0
    fi

    # Run setup steps
    setup_local_env || true
    echo ""

    setup_cloud_secrets || true
    echo ""

    setup_cf_permissions || true
    echo ""

    read -p "Deploy Cloud Functions? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        deploy_functions || true
    fi
    echo ""

    verify_setup || true

    echo ""
    print_success "Setup Complete!"
    echo ""
    print_info "Next steps:"
    echo "  1. Test locally: ./gradlew installTestDebug"
    echo "  2. Manual payment test on device"
    echo "  3. Check logs: adb logcat | grep -i razorpay"
    echo "  4. Monitor function logs: firebase functions:log"
}

main "$@"
