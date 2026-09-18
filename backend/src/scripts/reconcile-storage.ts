import { closeDatabase } from "../db/pool.js";
import { getStorageProvider } from "../storage/provider.js";
import { StorageReconciliationService } from "../services/storage-reconciliation-service.js";

const limitArg = Number(process.argv[2] ?? 100);
const limit = Number.isSafeInteger(limitArg) && limitArg > 0 ? Math.min(limitArg, 500) : 100;

try {
  const result = await new StorageReconciliationService(getStorageProvider()).run(limit);
  console.log(JSON.stringify(result));
} finally {
  await closeDatabase();
}
