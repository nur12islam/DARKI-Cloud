import "dotenv/config";

function optionalEnv(name: string): string | undefined {
  const value = process.env[name]?.trim();
  return value || undefined;
}

function requiredEnv(name: string): string {
  const value = optionalEnv(name);
  if (!value) throw new Error(`${name} is required`);
  return value;
}

function integerEnv(name: string, fallback: number): number {
  const value = Number(process.env[name] ?? fallback);
  if (!Number.isInteger(value) || value < 1) throw new Error(`${name} must be a positive integer`);
  return value;
}

export const config = {
  nodeEnv: process.env.NODE_ENV ?? "development",
  host: process.env.HOST ?? "0.0.0.0",
  port: integerEnv("PORT", 8080),
  databaseUrl: optionalEnv("DATABASE_URL"),
  databasePoolMax: integerEnv("DATABASE_POOL_MAX", 10),
  databaseConnectionTimeoutMs: integerEnv("DATABASE_CONNECTION_TIMEOUT_MS", 5000),
  databaseIdleTimeoutMs: integerEnv("DATABASE_IDLE_TIMEOUT_MS", 30000),
  telegramBotToken: optionalEnv("TELEGRAM_BOT_TOKEN"),
  telegramStorageChatId: optionalEnv("TELEGRAM_STORAGE_CHAT_ID"),
  telegramClientId: optionalEnv("TELEGRAM_CLIENT_ID"),
  telegramClientSecret: optionalEnv("TELEGRAM_CLIENT_SECRET"),
  telegramOidcRedirectUri: optionalEnv("TELEGRAM_OIDC_REDIRECT_URI"),
  telegramAppRedirectUri: optionalEnv("TELEGRAM_APP_REDIRECT_URI") ?? "darkicloud://auth",
  sessionSecret: optionalEnv("SESSION_SECRET"),
};

export function requireTelegramBotToken(): string {
  return config.telegramBotToken ?? requiredEnv("TELEGRAM_BOT_TOKEN");
}

export function requireTelegramStorageChatId(): string {
  return config.telegramStorageChatId ?? requiredEnv("TELEGRAM_STORAGE_CHAT_ID");
}

export function requireTelegramOidcConfig() {
  return {
    clientId: config.telegramClientId ?? requiredEnv("TELEGRAM_CLIENT_ID"),
    clientSecret: config.telegramClientSecret ?? requiredEnv("TELEGRAM_CLIENT_SECRET"),
    redirectUri: config.telegramOidcRedirectUri ?? requiredEnv("TELEGRAM_OIDC_REDIRECT_URI"),
    appRedirectUri: config.telegramAppRedirectUri,
  };
}

export function requireSessionSecret(): string {
  return config.sessionSecret ?? requiredEnv("SESSION_SECRET");
}
