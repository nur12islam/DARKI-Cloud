import "dotenv/config";

function optionalEnv(name: string): string | undefined {
  const value = process.env[name]?.trim();
  return value || undefined;
}

function integerEnv(name: string, fallback: number): number {
  const value = Number(process.env[name] ?? fallback);
  if (!Number.isInteger(value) || value < 1) {
    throw new Error(`${name} must be a positive integer`);
  }
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
};
