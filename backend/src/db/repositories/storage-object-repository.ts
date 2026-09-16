import type { DbExecutor, StorageObjectRecord } from "./types.js";

export class StorageObjectRepository {
  constructor(private readonly db: DbExecutor) {}

  async findById(id: string): Promise<StorageObjectRecord | null> {
    const result = await this.db.query<StorageObjectRecord>(
      `SELECT id, provider, provider_object_key AS "providerObjectKey",
              size_bytes AS "sizeBytes", mime_type AS "mimeType", sha256, state,
              created_at AS "createdAt", modified_at AS "modifiedAt", deleted_at AS "deletedAt"
         FROM storage_objects
        WHERE id = $1`,
      [id],
    );
    return result.rows[0] ?? null;
  }

  async create(input: {
    provider: string;
    providerObjectKey: string;
    sizeBytes: string;
    mimeType?: string | null;
    sha256?: string | null;
    state?: string;
  }): Promise<StorageObjectRecord> {
    const result = await this.db.query<StorageObjectRecord>(
      `INSERT INTO storage_objects
         (provider, provider_object_key, size_bytes, mime_type, sha256, state)
       VALUES ($1, $2, $3, $4, $5, $6)
       RETURNING id, provider, provider_object_key AS "providerObjectKey",
                 size_bytes AS "sizeBytes", mime_type AS "mimeType", sha256, state,
                 created_at AS "createdAt", modified_at AS "modifiedAt", deleted_at AS "deletedAt"`,
      [input.provider, input.providerObjectKey, input.sizeBytes,
        input.mimeType ?? null, input.sha256 ?? null, input.state ?? "uploading"],
    );
    return result.rows[0]!;
  }

  async setState(id: string, state: string): Promise<void> {
    await this.db.query(
      `UPDATE storage_objects SET state = $1, modified_at = now() WHERE id = $2`,
      [state, id],
    );
  }
}
