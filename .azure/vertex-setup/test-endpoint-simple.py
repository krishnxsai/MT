#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Simple Vertex AI endpoint test - Windows compatible.
No gcloud dependency needed.
"""

import json
import os
from google.auth.transport.requests import Request
from google.oauth2 import service_account
import requests

def load_config(config_file="vertex-ai-config.env"):
    """Load configuration."""
    config = {}
    if not os.path.exists(config_file):
        print(f"ERROR: Config file not found: {config_file}")
        return None

    with open(config_file, 'r') as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith('#'):
                key, value = line.split('=', 1)
                config[key.strip()] = value.strip().strip('"\'')

    return config

def get_token_from_service_account(service_account_file):
    """Get identity token using service account key."""
    credentials = service_account.Credentials.from_service_account_file(
        service_account_file,
        scopes=["https://www.googleapis.com/auth/cloud-platform"]
    )

    # Refresh to get token
    credentials.refresh(Request())
    return credentials.token

def test_endpoint(config, test_data):
    """Test Vertex endpoint."""
    endpoint_url = config.get("VERTEX_INFERENCE_URL")

    if not endpoint_url:
        print("ERROR: VERTEX_INFERENCE_URL not found in config")
        return False

    # Try to find service account key
    service_account_file = os.path.expanduser("~/Downloads/meditrack-492209-32db749837f5.json")

    if not os.path.exists(service_account_file):
        # Try alternative paths
        possible_paths = [
            "meditrack-492209-32db749837f5.json",
            "../../../Downloads/meditrack-492209-32db749837f5.json",
            os.path.expanduser("~/.config/gcloud/legacy_credentials/*/adc.json")
        ]

        for path in possible_paths:
            if os.path.exists(path):
                service_account_file = path
                break
        else:
            print("ERROR: Service account key file not found")
            print("  Expected: ~/Downloads/meditrack-492209-32db749837f5.json")
            print("  Make sure you have authenticated with gcloud first")
            return False

    print("[OK] Service account found")

    try:
        token = get_token_from_service_account(service_account_file)
        print("[OK] Got identity token")
    except Exception as e:
        print(f"ERROR: Failed to get token: {e}")
        return False

    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {token}"
    }

    payload = {
        "instances": [test_data]
    }

    print(f"\n[TEST] Calling endpoint...")
    print(f"  URL: {endpoint_url}")
    print(f"  Data: {json.dumps(test_data)}")

    try:
        response = requests.post(endpoint_url, json=payload, headers=headers, timeout=15)

        print(f"\n[RESPONSE] Status: {response.status_code}")

        if response.status_code == 200:
            result = response.json()
            print("[OK] Endpoint returned successfully!")
            print(f"\nResponse:")
            print(json.dumps(result, indent=2))

            # Extract prediction
            if "predictions" in result and result["predictions"]:
                pred = result["predictions"][0]

                # Handle different response formats
                if isinstance(pred, dict) and "value" in pred:
                    score = pred["value"]
                elif isinstance(pred, list) and len(pred) > 0:
                    score = pred[0]
                else:
                    score = pred

                print(f"\n[RESULT] Predicted Risk Score: {float(score):.2f}")

                # Interpret risk level
                risk_score = float(score)
                if risk_score < 25:
                    risk_level = "LOW"
                elif risk_score < 50:
                    risk_level = "MODERATE"
                elif risk_score < 75:
                    risk_level = "HIGH"
                else:
                    risk_level = "CRITICAL"

                print(f"[INTERPRETATION] Risk Level: {risk_level}")
                return True
        else:
            print(f"[ERROR] Status {response.status_code}")
            print(response.text)
            return False

    except requests.exceptions.Timeout:
        print("[ERROR] Request timeout - endpoint may not be ready yet")
        print("         Please wait a few minutes and try again")
        return False
    except Exception as e:
        print(f"[ERROR] Request failed: {e}")
        return False

def main():
    print("=" * 60)
    print("VERTEX AI ENDPOINT TEST - SIMPLE")
    print("=" * 60)

    # Load config
    print("\n[*] Loading configuration...")
    config = load_config()

    if not config:
        print("ERROR: Failed to load config")
        return

    print(f"[OK] Config loaded")
    print(f"  AI Project: {config.get('VERTEX_PROJECT_ID')}")
    print(f"  Endpoint ID: {config.get('VERTEX_ENDPOINT_ID')}")

    # Test data - patient with hypertension + high glucose (moderate risk)
    test_data = {
        "bp_systolic": 140.0,
        "bp_diastolic": 90.0,
        "glucose": 126.0,
        "heart_rate": 85.0,
        "temperature": 37.2,
        "adherence": 80.0
    }

    print(f"\n[*] Test patient data:")
    print(f"  BP: 140/90 mmHg (Stage 2 hypertension)")
    print(f"  Glucose: 126 mg/dL (Diabetic range)")
    print(f"  Heart Rate: 85 bpm")
    print(f"  Temperature: 37.2°C")
    print(f"  Adherence: 80%")

    # Test endpoint
    print("\n[*] Testing endpoint...")
    print("  (This may take 10-15 seconds...)")

    success = test_endpoint(config, test_data)

    print("\n" + "=" * 60)
    if success:
        print("[SUCCESS] Endpoint is working!")
        print("\nNext steps:")
        print("  1. Configure Firebase:")
        print("     firebase functions:config:set \\")
        print(f"       vertex.inference_url=\"{config.get('VERTEX_INFERENCE_URL')}\" \\")
        print("       --project=meditrack-635e2")
        print("\n  2. Deploy functions:")
        print("     cd ../../functions")
        print("     npm run build")
        print("     firebase deploy --only functions --project=meditrack-635e2")
    else:
        print("[FAILED] Could not reach endpoint")
        print("\nPossible reasons:")
        print("  1. Endpoint is still deploying (wait 10-15 minutes)")
        print("  2. Service account lacks permissions")
        print("  3. Check Google Cloud Console for errors")
        print("  4. Verify endpoint is in 'DEPLOYED' state")

    print("=" * 60)

if __name__ == "__main__":
    main()
