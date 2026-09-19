import { createServer } from "node:http";
import { createApp } from "./app.js";
import { config } from "./config.js";
import { db, closeDatabase } from "./db/pool.js";

async function ensureRuntimeSchema(): Promise<void> {
  await db.query(`
    CREATE TABLE IF NOT EXISTS telegram_login_attempts (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      state_hash CHAR(64) NOT NULL UNIQUE,
      code_verifier TEXT NOT NULL,
      nonce_hash CHAR(64) NOT NULL,
      exchange_code_hash CHAR(64) UNIQUE,
      user_id UUID REFERENCES users(id) ON DELETE CASCADE,
      expires_at TIMESTAMPTZ NOT NULL,
      used_at TIMESTAMPTZ,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      CONSTRAINT telegram_login_attempts_expiry_valid CHECK (expires_at > created_at)
    )
  `);

  await db.query(`
    CREATE INDEX IF NOT EXISTS telegram_login_attempts_expiry_idx
      ON telegram_login_attempts (expires_at)
  `);

  await db.query(`
    CREATE INDEX IF NOT EXISTS telegram_login_attempts_exchange_idx
      ON telegram_login_attempts (exchange_code_hash)
      WHERE exchange_code_hash IS NOT NULL AND used_at IS NULL
  `);

  await db.query(`
    CREATE INDEX IF NOT EXISTS telegram_login_attempts_cleanup_idx
      ON telegram_login_attempts (expires_at, used_at)
  `);

  console.log("Runtime database schema check completed.");
}

async function main() {
  await ensureRuntimeSchema();

  const app = createApp();
  const server = createServer(app);

  server.listen(config.port, config.host, () => {
    console.log(`DARKI Cloud backend listening on ${config.host}:${config.port}`);
  });

  function shutdown(signal: string) {
    console.log(`${signal} received; shutting down`);
    server.close(async (error) => {
      if (error) {
        console.error(error);
        process.exitCode = 1;
      }
      await closeDatabase();
      process.exit();
    });
  }

  process.on("SIGINT", () => shutdown("SIGINT"));
  process.on("SIGTERM", () => shutdown("SIGTERM"));
}

main().catch((error) => {
  console.error("Database initialization failed", error);
  process.exit(1);
});
