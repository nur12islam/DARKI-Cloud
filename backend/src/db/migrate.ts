import { readdir, readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { db, closeDatabase } from "./pool.js";

const schemaPath = fileURLToPath(
  new URL("../../../database/schema.sql", import.meta.url),
);
const migrationsPath = fileURLToPath(
  new URL("../../../database/migrations", import.meta.url),
);

const schema = await readFile(schemaPath, "utf8");
const migrationFiles = (await readdir(migrationsPath))
  .filter((name) => /^\d+_.+\.sql$/.test(name))
  .sort();

try {
  await db.query("BEGIN");

  const baseSchemaExists = await db.query<{ exists: boolean }>(
    "SELECT to_regclass('public.users') IS NOT NULL AS exists",
  );

  if (!baseSchemaExists.rows[0]?.exists) {
    await db.query(schema);
    console.log("Applied base database schema.");
  } else {
    console.log("Base database schema already exists.");
  }

  await db.query(`
    CREATE TABLE IF NOT EXISTS schema_migrations (
      version TEXT PRIMARY KEY,
      applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
    )
  `);

  for (const filename of migrationFiles) {
    const alreadyApplied = await db.query(
      "SELECT 1 FROM schema_migrations WHERE version = $1",
      [filename],
    );

    if (alreadyApplied.rowCount) {
      console.log(`Skipped migration: ${filename}`);
      continue;
    }

    const migration = await readFile(`${migrationsPath}/${filename}`, "utf8");
    await db.query(migration);
    await db.query(
      "INSERT INTO schema_migrations (version) VALUES ($1)",
      [filename],
    );
    console.log(`Applied migration: ${filename}`);
  }

  await db.query("COMMIT");
  console.log("Database schema and migrations applied successfully.");
} catch (error) {
  await db.query("ROLLBACK");
  throw error;
} finally {
  await closeDatabase();
}
