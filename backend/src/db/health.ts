import { db } from "./pool.js";

export type DatabaseHealth = {
  ok: boolean;
  latencyMs: number;
};

export async function checkDatabaseHealth(): Promise<DatabaseHealth> {
  const started = performance.now();
  await db.query("SELECT 1");

  return {
    ok: true,
    latencyMs: Math.round(performance.now() - started),
  };
}
