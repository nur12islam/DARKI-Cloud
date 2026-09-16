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
  await db.query(schema);

  for (const filename of migrationFiles) {
    const migration = await readFile(`${migrationsPath}/${filename}`, "utf8");
    await db.query(migration);
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
