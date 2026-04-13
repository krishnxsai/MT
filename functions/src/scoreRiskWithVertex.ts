/**
 * Cross-project Vertex AI risk scoring bridge.
 *
 * This function runs in the core Firebase project and calls a dedicated
 * AI project endpoint so Vertex billing stays isolated to the AI project.
 */

import * as functions from "firebase-functions";
import { GoogleAuth } from "google-auth-library";

interface RiskRequestFeatures {
    bpSystolic?: number;
    bpDiastolic?: number;
    glucose?: number;
    heartRate?: number;
    temperature?: number;
    adherence?: number;
}

interface NormalizedRiskResponse {
    provider: "vertex" | "local_fallback";
    risk: "LOW" | "MODERATE" | "HIGH" | "CRITICAL";
    reason: string;
    score: number;
    fallbackUsed: boolean;
}

const DEFAULT_TIMEOUT_MS = 8000;
const googleAuth = new GoogleAuth();

function asRecord(value: unknown): Record<string, unknown> | null {
    return value && typeof value === "object" && !Array.isArray(value)
        ? (value as Record<string, unknown>)
        : null;
}

function asString(value: unknown): string {
    return typeof value === "string" ? value : "";
}

function toOptionalNumber(value: unknown): number | undefined {
    if (typeof value === "number" && Number.isFinite(value)) {
        return value;
    }

    if (typeof value === "string" && value.trim().length > 0) {
        const parsed = Number(value);
        if (Number.isFinite(parsed)) {
            return parsed;
        }
    }

    return undefined;
}

function toPositiveIntOrDefault(value: unknown, fallback: number): number {
    const num = toOptionalNumber(value);
    if (num === undefined) {
        return fallback;
    }

    const floored = Math.floor(num);
    return floored > 0 ? floored : fallback;
}

function normalizeRiskLabel(value: string): "LOW" | "MODERATE" | "HIGH" | "CRITICAL" {
    const normalized = value.trim().toUpperCase();
    switch (normalized) {
        case "LOW":
            return "LOW";
        case "MEDIUM":
        case "MODERATE":
            return "MODERATE";
        case "HIGH":
            return "HIGH";
        case "CRITICAL":
        case "VERY_HIGH":
            return "CRITICAL";
        default:
            return "MODERATE";
    }
}

function parseFeatures(data: unknown): RiskRequestFeatures {
    const obj = asRecord(data) || {};

    const adherenceRaw = toOptionalNumber(obj.adherence);
    const adherence = adherenceRaw === undefined
        ? undefined
        : Math.min(100, Math.max(0, adherenceRaw));

    return {
        bpSystolic: toOptionalNumber(obj.bpSystolic),
        bpDiastolic: toOptionalNumber(obj.bpDiastolic),
        glucose: toOptionalNumber(obj.glucose),
        heartRate: toOptionalNumber(obj.heartRate),
        temperature: toOptionalNumber(obj.temperature),
        adherence,
    };
}

function buildFallbackResponse(
    features: RiskRequestFeatures,
    fallbackReason: string
): NormalizedRiskResponse {
    const reasons: string[] = [];
    let score = 0;

    const systolic = features.bpSystolic;
    const diastolic = features.bpDiastolic;
    const glucose = features.glucose;
    const heartRate = features.heartRate;
    const temperature = features.temperature;
    const adherence = features.adherence;

    if ((systolic !== undefined && systolic >= 180) || (diastolic !== undefined && diastolic >= 120)) {
        score += 40;
        reasons.push("critical blood pressure");
    } else if ((systolic !== undefined && systolic >= 160) || (diastolic !== undefined && diastolic >= 100)) {
        score += 30;
        reasons.push("very high blood pressure");
    } else if ((systolic !== undefined && systolic >= 140) || (diastolic !== undefined && diastolic >= 90)) {
        score += 20;
        reasons.push("elevated blood pressure");
    }

    if (glucose !== undefined) {
        if (glucose >= 300) {
            score += 35;
            reasons.push("critical glucose");
        } else if (glucose >= 200) {
            score += 25;
            reasons.push("high glucose");
        } else if (glucose >= 126) {
            score += 12;
            reasons.push("borderline high glucose");
        }
    }

    if (adherence !== undefined) {
        if (adherence < 50) {
            score += 25;
            reasons.push("low adherence");
        } else if (adherence < 75) {
            score += 12;
            reasons.push("suboptimal adherence");
        }
    }

    if (heartRate !== undefined) {
        if (heartRate < 45 || heartRate > 130) {
            score += 20;
            reasons.push("abnormal heart rate");
        } else if (heartRate < 55 || heartRate > 110) {
            score += 10;
            reasons.push("borderline heart rate");
        }
    }

    if (temperature !== undefined) {
        if (temperature >= 39.0 || temperature < 35.0) {
            score += 20;
            reasons.push("critical temperature");
        } else if (temperature >= 38.0 || temperature < 36.0) {
            score += 10;
            reasons.push("abnormal temperature");
        }
    }

    score = Math.max(0, Math.min(100, score));

    let risk: "LOW" | "MODERATE" | "HIGH" | "CRITICAL" = "LOW";
    if (score >= 80) {
        risk = "CRITICAL";
    } else if (score >= 60) {
        risk = "HIGH";
    } else if (score >= 30) {
        risk = "MODERATE";
    }

    const reason = reasons.length > 0
        ? `${fallbackReason}; factors: ${reasons.join(", ")}`
        : `${fallbackReason}; insufficient high-risk factors detected`;

    return {
        provider: "local_fallback",
        risk,
        reason,
        score,
        fallbackUsed: true,
    };
}

function normalizeVertexResponse(payload: unknown): Omit<NormalizedRiskResponse, "provider" | "fallbackUsed"> {
    const body = asRecord(payload) || {};
    const predictionFromField = asRecord(body.prediction);

    const predictions = Array.isArray(body.predictions)
        ? body.predictions
        : null;
    const predictionFromArray = predictions && predictions.length > 0
        ? asRecord(predictions[0])
        : null;

    const selected = predictionFromField || predictionFromArray || body;

    const riskRaw = asString(
        selected.risk ??
        selected.category ??
        selected.label
    );

    const reason = asString(
        selected.reason ??
        selected.explanation ??
        selected.summary
    ) || "Vertex AI risk scoring result";

    const scoreRaw = toOptionalNumber(
        selected.score ??
        selected.riskScore ??
        selected.confidence
    );

    const risk = normalizeRiskLabel(riskRaw || "MODERATE");
    const score = Math.max(0, Math.min(100, Math.round(scoreRaw ?? 50)));

    return {
        risk,
        reason,
        score,
    };
}

async function fetchIdTokenForAudience(audience: string): Promise<string> {
    const idTokenClient = await googleAuth.getIdTokenClient(audience);
    const headers = await idTokenClient.getRequestHeaders();

    const authHeaderValue = headers.Authorization || headers.authorization;
    if (!authHeaderValue || typeof authHeaderValue !== "string") {
        throw new Error("Unable to obtain identity token for cross-project Vertex call");
    }

    return authHeaderValue.replace(/^Bearer\s+/i, "");
}

export const scoreRiskWithVertex = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError(
            "unauthenticated",
            "User must be authenticated to request AI risk scoring"
        );
    }

    const features = parseFeatures(data);

    const runtimeConfig = functions.config();
    const inferenceUrl =
        process.env.VERTEX_INFERENCE_URL ||
        runtimeConfig.vertex?.inference_url;

    const audience =
        process.env.VERTEX_AUDIENCE ||
        runtimeConfig.vertex?.audience ||
        inferenceUrl;

    const fallbackEnabledRaw =
        process.env.VERTEX_ALLOW_FALLBACK || runtimeConfig.vertex?.allow_fallback || "true";
    const fallbackEnabled = String(fallbackEnabledRaw).toLowerCase() !== "false";

    if (!inferenceUrl || !audience) {
        if (fallbackEnabled) {
            return buildFallbackResponse(features, "Vertex endpoint is not configured");
        }

        throw new functions.https.HttpsError(
            "failed-precondition",
            "Vertex endpoint configuration missing"
        );
    }

    const timeoutMs = toPositiveIntOrDefault(
        process.env.VERTEX_TIMEOUT_MS || runtimeConfig.vertex?.timeout_ms,
        DEFAULT_TIMEOUT_MS
    );

    try {
        const idToken = await fetchIdTokenForAudience(audience);

        const abortController = new AbortController();
        const timeoutHandle = setTimeout(() => {
            abortController.abort();
        }, timeoutMs);

        const requestBody = {
            userId: context.auth.uid,
            sourceProjectId: process.env.GCLOUD_PROJECT || process.env.GCP_PROJECT || "unknown",
            features,
            requestedAt: new Date().toISOString(),
        };

        const vertexResponse = await fetch(inferenceUrl, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Authorization": `Bearer ${idToken}`,
            },
            body: JSON.stringify(requestBody),
            signal: abortController.signal,
        });

        clearTimeout(timeoutHandle);

        const responseText = await vertexResponse.text();
        let parsedPayload: unknown = {};

        if (responseText.trim().length > 0) {
            try {
                parsedPayload = JSON.parse(responseText);
            } catch (parseError: unknown) {
                const err = parseError as { message?: string };
                console.warn("scoreRiskWithVertex: non-JSON response", err.message);
            }
        }

        if (!vertexResponse.ok) {
            throw new Error(
                `Vertex endpoint call failed (${vertexResponse.status}): ${responseText.slice(0, 280)}`
            );
        }

        const normalized = normalizeVertexResponse(parsedPayload);

        return {
            provider: "vertex",
            risk: normalized.risk,
            reason: normalized.reason,
            score: normalized.score,
            fallbackUsed: false,
        } as NormalizedRiskResponse;
    } catch (error: unknown) {
        const err = error as { message?: string };
        console.error("scoreRiskWithVertex failed", err.message, error);

        if (fallbackEnabled) {
            return buildFallbackResponse(
                features,
                "Vertex call failed; using local fallback"
            );
        }

        throw new functions.https.HttpsError(
            "unavailable",
            "Cloud AI scoring unavailable"
        );
    }
});
