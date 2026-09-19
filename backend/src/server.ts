import { createServer } from "node:http";
import { readdir, readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { createApp } from "./app.js";
import { config } from "./config.js";
import { db, closeDatabase } from "./db/pool.js";

const schemaPath = fileURLToPath(
  new URL("../../database/schema.sql", import.meta.url),
);
const migrationsPath = fileURLToPath(
  new URL("../../database/migrations", import.meta.url),
);

async function ensureRuntimeSchema(): Promise<void> {
  await db.query("BEGIN");

  try {
    const baseSchema = await db.query<{ exists: boolean }>(
      "SELECT to_regclass('public.users') IS NOT NULL AS exists",
    );

    if (!baseSchema.rows[0]?.exists) {
      const schema = await readFile(schemaPath, "utf8");
      await db.query(schema);
      console.log("Applied base database schema.");
    } else {
      console.log("Base database schema already exists.");
    }

    const migrationFiles = (await readdir(migrationsPath))
      .filter((name) => /^\d+_.+\.sql$/.test(name))
      .sort();

    for (const filename of migrationFiles) {
      const migration = await readFile(
        `${migrationsPath}/${filename}`,
        "utf8",
      );
      await db.query(migration);
      console.log(`Ensured migration: ${filename}`);
    }

    await db.query("COMMIT");
    console.log("Runtime database schema check completed.");
  } catch (error) {
    await db.query("ROLLBACK");
    throw error;
  }
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
