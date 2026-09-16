import { Pool } from "pg";
import { config } from "../config.js";

export const db = new Pool({
  connectionString: config.databaseUrl,
  max: config.databasePoolMax,
  connectionTimeoutMillis: config.databaseConnectionTimeoutMs,
  idleTimeoutMillis: config.databaseIdleTimeoutMs,
});

db.on("error", (error) => {
  console.error("Unexpected PostgreSQL pool error", error);
});

export async function closeDatabase(): Promise<void> {
  await db.end();
}
