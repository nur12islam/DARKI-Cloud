import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { db, closeDatabase } from "./pool.js";

const schemaPath = fileURLToPath(
  new URL("../../../database/schema.sql", import.meta.url),
);

const schema = await readFile(schemaPath, "utf8");

try {
  await db.query(schema);
  console.log("Database schema applied successfully.");
} finally {
  await closeDatabase();
}
