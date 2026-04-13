# Vertex AI Model Setup - Complete Guide

## 📋 Overview

This directory contains everything needed to create and deploy a machine learning model in Google Cloud Vertex AI for MediTrack's health risk scoring system.

### Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  MediTrack Project (meditrack-635e2) - Core Firebase App       │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  Android App                                            │  │
│  │  - Uses local risk scoring by default                  │  │
│  │  - Calls scoreRiskWithVertex() when enabled via flag   │  │
│  └──────────────────┬──────────────────────────────────────┘  │
│                     │ (Firebase Callable)                       │
│  ┌──────────────────▼──────────────────┐                       │
│  │  Firebase Cloud Function             │                      │
│  │  scoreRiskWithVertex.ts              │                      │
│  │  - Validates input                   │                      │
│  │  - Creates identity token            │                      │
│  │  - Calls AI project endpoint         │                      │
│  │  - Returns normalized score          │                      │
│  └──────────────────┬──────────────────┘                       │
│                     │ (HTTP POST)                                │
└─────────────────────┼────────────────────────────────────────────┘
                      │
        ┌─────────────▼─────────────────────────┐
        │  SEPARATE BILLING ACCOUNT             │
        │                                       │
        │  Vertex AI Project (meditrack-492209) │
        │                                       │
        │  ┌─────────────────────────────────┐ │
        │  │  API Gateway                    │ │
        │  │  health-risk-gateway            │ │
        │  │  /predict                       │ │
        │  └────────────┬────────────────────┘ │
        │               │                      │
        │  ┌────────────▼────────────────────┐ │
        │  │  Vertex AI Endpoint             │ │
        │  │  health-risk-endpoint           │ │
        │  │  (AutoML Tabular Model)         │ │
        │  │  INPUT: health metrics          │ │
        │  │  OUTPUT: 0-100 risk score       │ │
        │  └─────────────────────────────────┘ │
        │                                       │
        │  BigQuery Dataset: health_risk       │
        │  - health_metrics (training data)    │
        │                                       │
        └───────────────────────────────────────┘
```

## 📁 Files in This Directory

| File | Purpose |
|------|---------|
| `00-create-dataset.py` | Generate 1000 synthetic health records for training |
| `01-deploy-vertex-ai.sh` | Deploy everything to Vertex AI (main script) |
| `quickstart.sh` | Bash quick-start wrapper |
| `quickstart.ps1` | PowerShell quick-start wrapper (Windows) |
| `test-endpoint.py` | Test the deployed endpoint |
| `VERTEX_AI_SETUP.md` | Detailed setup documentation |
| `vertex-ai-config.env` | Generated configuration (created after deployment) |
| `api-gateway-config.yaml` | Generated API Gateway config |

## 🚀 Quick Start

### Option 1: Bash (Linux/Mac/WSL)

```bash
cd .azure/vertex-setup
chmod +x quickstart.sh
./quickstart.sh
```

### Option 2: PowerShell (Windows)

```powershell
cd .azure\vertex-setup
.\quickstart.ps1
```

### Option 3: Manual (Step by Step)

```bash
cd .azure/vertex-setup

# 1. Generate dataset
python3 00-create-dataset.py

# 2. Deploy to Vertex AI
bash 01-deploy-vertex-ai.sh

# 3. (Wait 30-60 minutes for training)

# 4. Test endpoint
python3 test-endpoint.py
```

## ⏱️ Timeline

| Step | Time | Action |
|------|------|--------|
| 1 | 1 min | Generate synthetic dataset |
| 2 | 5 min | Setup BigQuery and Vertex AI |
| 3 | 30-60 min | **Train AutoML model** (run in background) |
| 4 | 10 min | Deploy model to endpoint |
| 5 | 5 min | Setup API Gateway and IAM |
| **Total** | **~1 hour** | Production-ready endpoint |

**Note:** Model training (step 3) is the longest step. You can monitor progress in Google Cloud Console while it runs.

## 📊 Dataset Details

The synthetic dataset includes:

- **1000 patient records** with realistic health metrics
- **Features**: Based on medical guidelines
  - Blood Pressure (systolic & diastolic)
  - Glucose level
  - Heart Rate
  - Body Temperature
  - Medication Adherence

- **Target**: Risk Score (0-100)
  - Calculated using medical risk factors
  - Includes realistic variance to simulate real data

**Example Records:**

| BP Sys | BP Dias | Glucose | HR  | Temp | Adherence | Risk Score |
|--------|---------|---------|-----|------|-----------|------------|
| 120    | 80      | 100     | 75  | 37.0 | 90        | 15.2       |
| 160    | 100     | 150     | 95  | 37.5 | 70        | 75.8       |
| 140    | 90      | 126     | 85  | 37.2 | 80        | 48.3       |

## 🔧 What Gets Created

After running the deployment script:

### In Vertex AI (meditrack-492209):
- ✅ BigQuery dataset: `health_risk`
- ✅ BigQuery table: `health_risk.health_metrics`
- ✅ Vertex Dataset: `Health Risk Dataset`
- ✅ AutoML Model: `health_risk_regression`
- ✅ Endpoint: `health-risk-endpoint`
- ✅ API Gateway: `health-risk-gateway`
- ✅ Service: `health-risk-api-meditrack-492209`

### IAM Permissions:
- ✅ Core project service account (`meditrack-635e2@appspot.gserviceaccount.com`) can invoke the endpoint
- ✅ Core project can call API Gateway

### Configuration File:
- ✅ `vertex-ai-config.env` - Contains URLs and IDs for Firebase functions

## 🧪 Testing

### Test Endpoint Directly

```bash
python3 test-endpoint.py
```

Output:
```
🧪 Testing API Gateway
==================================================
📍 URL: https://health-risk-api-...
📤 Request: {...}
✅ Success!
📨 Response: {...}
🎯 Predicted Risk Score: 48.32
```

### Test with Custom Values

```bash
python3 test-endpoint.py \
  --bp-systolic 160 \
  --bp-diastolic 100 \
  --glucose 150 \
  --heart-rate 95 \
  --temperature 37.5 \
  --adherence 70
```

### Curl Test

```bash
curl -X POST "https://health-risk-api-meditrack-492209.us-central1.run.app/predict" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $(gcloud auth application-default print-identity-token)" \
  -d '{
    "userId": "test-user",
    "features": {
      "bp_systolic": 140,
      "bp_diastolic": 90,
      "glucose": 126,
      "heart_rate": 85,
      "temperature": 37.2,
      "adherence": 80
    }
  }'
```

## 📝 Firebase Integration

### Step 1: Extract Configuration

After deployment, extract from `vertex-ai-config.env`:

```bash
grep VERTEX_INFERENCE_URL vertex-ai-config.env
grep API_GATEWAY_AUDIENCE vertex-ai-config.env
```

### Step 2: Set Firebase Config

```bash
firebase functions:config:set vertex.inference_url="https://..." \
  vertex.audience="https://..." \
  vertex.allow_fallback="true" \
  vertex.timeout_ms="8000" \
  --project=meditrack-635e2
```

### Step 3: Deploy Functions

```bash
cd ../../functions
npm run build
firebase deploy --only functions --project=meditrack-635e2
```

### Step 4: Enable Feature Flag

In Firebase Console → Remote Config:
1. Add parameter: `cloud_risk_scoring_enabled`
2. Set default value: `false` (start in shadow mode)
3. Publish

## 📈 Performance Metrics

| Metric | Expected Value |
|--------|-----------------|
| Endpoint latency | < 1 second |
| Model accuracy (MAE) | ~8 risk points |
| Concurrent predictions | 100+ per second |
| API Gateway latency | < 100ms |
| Cost per 1000 predictions | ~$0.05 |

## 🐛 Troubleshooting

### Model Training Not Starting

```bash
# Check training jobs
gcloud ai training-jobs list --project=meditrack-492209

# View job details
gcloud ai training-jobs describe <JOB_ID> --project=meditrack-492209
```

### IAM Permission Errors

```bash
# Debug permissions
gcloud ai endpoints describe <ENDPOINT_ID> \
  --region=us-central1 \
  --project=meditrack-492209 \
  --format=json | grep iam

# Re-grant if needed
gcloud ai endpoints add-iam-policy-binding <ENDPOINT_ID> \
  --region=us-central1 \
  --member="serviceAccount:meditrack-635e2@appspot.gserviceaccount.com" \
  --role="roles/aiplatform.user" \
  --project=meditrack-492209
```

### Model Prediction Errors

Check API Gateway logs:
```bash
gcloud logging read \
  "resource.type=api" AND "resource.labels.service=health-risk-api-meditrack-492209" \
  --limit=50 \
  --project=meditrack-492209
```

## 📚 Documentation

- [Vertex AI AutoML Tabular](https://cloud.google.com/vertex-ai/docs/tabular-data/overview)
- [Vertex AI Endpoints](https://cloud.google.com/vertex-ai/docs/general/deploy-model-api)
- [API Gateway](https://cloud.google.com/api-gateway/docs)
- [Cloud Identity Tokens](https://cloud.google.com/docs/authentication#service_accounts_and_access_tokens)

## 🔐 Security Notes

- ✅ Cross-project communication uses Identity tokens
- ✅ API Gateway validates request authentication
- ✅ Endpoint requires service account with roles/aiplatform.user
- ✅ Training data stored in controlled BigQuery dataset
- ✅ Model predictions do NOT store patient identifiable information (PII)

## 💰 Cost Estimation

| Component | Monthly Cost | Notes |
|-----------|--------------|-------|
| AutoML Training | $10-50 | One-time, can retrain on schedule |
| Vertex Endpoint | $30/month | Minimum (1 node, always-on) |
| API Gateway | Included | First 2M calls free |
| BigQuery | $0-10 | 1GB free per month |
| **Total** | **~$50-100** | Per month when deployed |

**Savings with separate billing:** Isolates ML costs from core Firebase, makes optimization transparent.

## 🎯 Next Steps

1. Run the setup script and wait for model training to complete
2. Test the endpoint with sample data
3. Integrate into Firebase functions
4. Enable feature flag for gradual rollout
5. Collect real patient data for model retraining
6. Set up continuous monitoring for model performance

## 📞 Support

If you encounter issues:

1. Check [VERTEX_AI_SETUP.md](VERTEX_AI_SETUP.md) for detailed troubleshooting
2. Review Google Cloud logs in the Console
3. Verify IAM permissions are correctly granted
4. Ensure gcloud is authenticated: `gcloud auth application-default login`

---

**Ready to start?** Run one of the quickstart commands above! 🚀
