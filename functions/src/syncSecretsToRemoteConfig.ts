/**
 * Cloud Secret Manager Sync to Firebase Remote Config
 *
 * Syncs sensitive configuration from Google Cloud Secret Manager to Firebase Remote Config.
 * This allows:
 * - Server-side secret management
 * - Automatic secret rotation to all clients
 * - Audit trail via Cloud Secret Manager
 * - Zero-downtime configuration updates
 *
 * Triggered by:
 * 1. Cloud Scheduler (periodic sync)
 * 2. Manual HTTP endpoint call
 */

import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import { SecretManagerServiceClient } from "@google-cloud/secret-manager";

const remoteConfig = admin.remoteConfig();
const secretManager = new SecretManagerServiceClient();

interface SecretConfig {
  secretName: string;
  remoteConfigKey: string;
  description: string;
}

interface SecretsMap {
  [key: string]: SecretConfig;
}

/**
 * Configuration mapping Cloud Secret Manager secrets to Firebase Remote Config keys.
 */
const SECRETS_CONFIG: SecretsMap = {
  razorpay_key_id: {
    secretName: "razorpay-key-id",
    remoteConfigKey: "razorpay_key_id",
    description: "Razorpay API Key ID from Cloud Secret Manager",
  },
};

/**
 * HTTP Cloud Function to sync secrets from Cloud Secret Manager to Firebase Remote Config.
 *
 * Can be called manually or via Cloud Scheduler.
 *
 * Usage:
 * 1. Cloud Scheduler: Set cron "0 0,6,12,18 * * *" and post to this endpoint
 * 2. Manual HTTP: curl -X POST https://YOUR-REGION-YOUR-PROJECT.cloudfunctions.net/syncSecretsFromCloudSecretManager
 * 3. Accessible only from within GCP or with proper authentication
 */
export const syncSecretsFromCloudSecretManager = functions.https.onRequest(
  async (req, res) => {
    try {
      if (req.method !== "POST") {
        res.status(405).json({ error: "Method not allowed. Use POST." });
        return;
      }

      console.log("Starting sync: Cloud Secret Manager -> Firebase Remote Config");

      const projectId = admin.app().options.projectId;
      if (!projectId) {
        throw new Error("Unable to determine Google Cloud Project ID");
      }

      // Step 1: Fetch all secrets
      const fetchedSecrets: { [key: string]: string } = {};

      for (const [configKey, config] of Object.entries(SECRETS_CONFIG)) {
        try {
          console.log(`Fetching secret: ${config.secretName}`);

          // Construct the full secret path
          const secretPath = `projects/${projectId}/secrets/${config.secretName}/versions/latest`;

          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const [response] = await (secretManager as any).accessSecretVersion({
            name: secretPath,
          });

          const secretValue = response.payload?.data?.toString("utf8") || "";

          if (!secretValue) {
            console.warn(`Warning: Secret ${config.secretName} is empty`);
            fetchedSecrets[configKey] = "";
          } else {
            fetchedSecrets[configKey] = secretValue;
            console.log(`Success: Secret ${config.secretName} fetched (${secretValue.length} bytes)`);
          }
        } catch (error: unknown) {
          const err = error as { message?: string };
          console.error(`Error fetching ${config.secretName}: ${err.message}`);
          fetchedSecrets[configKey] = "";
        }
      }

      // Step 2: Get current Remote Config template
      console.log("Fetching Firebase Remote Config template...");
      const template = await remoteConfig.getTemplate();

      // Step 3: Update parameters
      let updatedCount = 0;
      const params = template.parameters || {};

      for (const [configKey, secretValue] of Object.entries(fetchedSecrets)) {
        const config = SECRETS_CONFIG[configKey];

        if (!secretValue) {
          console.warn(`Skipping ${configKey} - secret empty`);
          continue;
        }

        // Get current value
        const currentParam = params[config.remoteConfigKey];
        let currentValue = "";

        if (currentParam && "defaultValue" in currentParam) {
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const defaultVal = (currentParam.defaultValue as any);
          currentValue = (defaultVal && defaultVal.value) ? String(defaultVal.value) : "";
        }

        if (currentValue !== secretValue) {
          console.log(`Updating ${config.remoteConfigKey} in Remote Config`);

          params[config.remoteConfigKey] = {
            defaultValue: {
              value: secretValue,
            },
            description: config.description,
          };

          updatedCount++;
        } else {
          console.log(`No changes for ${config.remoteConfigKey}`);
        }
      }

      // Step 4: Publish if updated
      if (updatedCount > 0) {
        console.log(`Publishing Remote Config with ${updatedCount} updates...`);

        const updatedTemplate = {
          ...template,
          parameters: params,
        };

        const result = await remoteConfig.publishTemplate(updatedTemplate);

        console.log(`Successfully published Remote Config`);

        res.status(200).json({
          status: "success",
          message: `Synced ${updatedCount} secrets`,
          updatedSecrets: Object.keys(SECRETS_CONFIG)
            .filter((key) => fetchedSecrets[key])
            .map((key) => SECRETS_CONFIG[key].remoteConfigKey),
          timestamp: new Date().toISOString(),
        });
      } else {
        console.log("No secrets needed updating");

        res.status(200).json({
          status: "success",
          message: "No updates needed",
          timestamp: new Date().toISOString(),
        });
      }
    } catch (error: unknown) {
      const err = error as { message?: string };
      console.error(`Sync failed: ${err.message}`);

      res.status(500).json({
        status: "error",
        message: err.message || "Unknown error",
      });
    }
  }
);

/**
 * Cloud Scheduler trigger to sync secrets periodically.
 *
 * Deploy with Cloud Scheduler:
 * Use cron pattern: 0 0,6,12,18 * * * (every 6 hours at UTC)
 *
 * This will run the sync every 6 hours at UTC times: 0:00, 6:00, 12:00, 18:00
 */
export const onSyncSecretsScheduled = functions.pubsub
  .topic("sync-secrets")
  .onPublish(async (message, context) => {
    console.log("Scheduled secret sync triggered at", context.timestamp);

    try {
      const projectId = admin.app().options.projectId;
      if (!projectId) {
        throw new Error("Unable to determine project ID");
      }

      console.log("Starting scheduled sync: Cloud Secret Manager -> Firebase Remote Config");

      const fetchedSecrets: { [key: string]: string } = {};

      // Fetch secrets
      for (const [configKey, config] of Object.entries(SECRETS_CONFIG)) {
        try {
          const secretPath = `projects/${projectId}/secrets/${config.secretName}/versions/latest`;

          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const [response] = await (secretManager as any).accessSecretVersion({
            name: secretPath,
          });

          const secretValue = response.payload?.data?.toString("utf8") || "";
          fetchedSecrets[configKey] = secretValue;

          console.log(`Scheduled sync: Fetched ${config.secretName} (${secretValue.length} bytes)`);
        } catch (error: unknown) {
          const err = error as { message?: string };
          console.error(`Scheduled sync: Failed to fetch ${config.secretName}: ${err.message}`);
          fetchedSecrets[configKey] = "";
        }
      }

      // Update Remote Config
      const template = await remoteConfig.getTemplate();
      let updatedCount = 0;
      const params = template.parameters || {};

      for (const [configKey, secretValue] of Object.entries(fetchedSecrets)) {
        const config = SECRETS_CONFIG[configKey];

        if (!secretValue) continue;

        const currentParam = params[config.remoteConfigKey];
        let currentValue = "";

        if (currentParam && "defaultValue" in currentParam) {
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const defaultVal = (currentParam.defaultValue as any);
          currentValue = (defaultVal && defaultVal.value) ? String(defaultVal.value) : "";
        }

        if (currentValue !== secretValue) {
          params[config.remoteConfigKey] = {
            defaultValue: {
              value: secretValue,
            },
            description: config.description,
          };
          updatedCount++;
        }
      }

      if (updatedCount > 0) {
        const updatedTemplate = {
          ...template,
          parameters: params,
        };

        await remoteConfig.publishTemplate(updatedTemplate);

        console.log(`Scheduled sync completed: ${updatedCount} secrets updated`);
        return { success: true, updated: updatedCount };
      } else {
        console.log("Scheduled sync: No updates needed");
        return { success: true, updated: 0 };
      }
    } catch (error: unknown) {
      const err = error as { message?: string };
      console.error(`Scheduled sync failed: ${err.message}`);
      throw error;
    }
  });

/**
 * Manual trigger function to verify sync configuration.
 */
export const verifySyncConfiguration = functions.https.onRequest(
  async (req, res) => {
    try {
      if (req.method !== "GET") {
        res.status(405).json({ error: "Method not allowed. Use GET." });
        return;
      }

      const projectId = admin.app().options.projectId;
      const config = {
        projectId,
        secretsConfigured: Object.entries(SECRETS_CONFIG).map(
          ([key, cfg]) => ({
            key,
            secretName: cfg.secretName,
            remoteConfigKey: cfg.remoteConfigKey,
            description: cfg.description,
          })
        ),
      };

      res.status(200).json({
        status: "configured",
        configuration: config,
      });
    } catch (error: unknown) {
      const err = error as { message?: string };
      res.status(500).json({
        status: "error",
        message: err.message,
      });
    }
  }
);
