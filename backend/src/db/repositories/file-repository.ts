import type { DbExecutor, FileRecord } from "./types.js";

export class FileRepository {
  constructor(private readonly db: DbExecutor) {}

  async findByIdForUser(id: string, userId: string): Promise<FileRecord | null> {
    const result = await this.db.query<FileRecord>(`SELECT id, user_id AS "userId", folder_id AS "folderId", storage_object_id AS "storageObjectId", name, mime_type AS "mimeType", size_bytes AS "sizeBytes", sha256, created_at AS "createdAt", modified_at AS "modifiedAt", deleted_at AS "deletedAt" FROM files WHERE id = $1 AND user_id = $2`, [id, userId]);
    return result.rows[0] ?? null;
  }

  async listByFolder(userId: string, folderId: string): Promise<FileRecord[]> {
    const result = await this.db.query<FileRecord>(`SELECT id, user_id AS "userId", folder_id AS "folderId", storage_object_id AS "storageObjectId", name, mime_type AS "mimeType", size_bytes AS "sizeBytes", sha256, created_at AS "createdAt", modified_at AS "modifiedAt", deleted_at AS "deletedAt" FROM files WHERE user_id = $1 AND folder_id = $2 AND deleted_at IS NULL ORDER BY lower(name), id`, [userId, folderId]);
    return result.rows;
  }

  async listDeletedByUser(userId: string): Promise<FileRecord[]> {
    const result = await this.db.query<FileRecord>(`SELECT id, user_id AS "userId", folder_id AS "folderId", storage_object_id AS "storageObjectId", name, mime_type AS "mimeType", size_bytes AS "sizeBytes", sha256, created_at AS "createdAt", modified_at AS "modifiedAt", deleted_at AS "deletedAt" FROM files WHERE user_id = $1 AND deleted_at IS NOT NULL ORDER BY deleted_at DESC, id`, [userId]);
    return result.rows;
  }

  async searchByName(userId: string, query: string, limit: number): Promise<FileRecord[]> {
    const result = await this.db.query<FileRecord>(`SELECT id, user_id AS "userId", folder_id AS "folderId", storage_object_id AS "storageObjectId", name, mime_type AS "mimeType", size_bytes AS "sizeBytes", sha256, created_at AS "createdAt", modified_at AS "modifiedAt", deleted_at AS "deletedAt" FROM files WHERE user_id = $1 AND deleted_at IS NULL AND name ILIKE $2 ORDER BY lower(name), id LIMIT $3`, [userId, `%${query}%`, limit]);
    return result.rows;
  }

  async create(input: { userId: string; folderId: string; name: string; mimeType?: string | null; sizeBytes?: string | null; sha256?: string | null; storageObjectId?: string | null }): Promise<FileRecord> {
    const result = await this.db.query<FileRecord>(`INSERT INTO files (user_id, folder_id, storage_object_id, name, mime_type, size_bytes, sha256) VALUES ($1, $2, $3, $4, $5, $6, $7) RETURNING id, user_id AS "userId", folder_id AS "folderId", storage_object_id AS "storageObjectId", name, mime_type AS "mimeType", size_bytes AS "sizeBytes", sha256, created_at AS "createdAt", modified_at AS "modifiedAt", deleted_at AS "deletedAt"`, [input.userId, input.folderId, input.storageObjectId ?? null, input.name, input.mimeType ?? null, input.sizeBytes ?? null, input.sha256 ?? null]);
    return result.rows[0]!;
  }

  async softDelete(id: string, userId: string): Promise<FileRecord | null> {
    const result = await this.db.query<FileRecord>(`UPDATE files SET deleted_at = now(), modified_at = now() WHERE id = $1 AND user_id = $2 AND deleted_at IS NULL RETURNING id, user_id AS "userId", folder_id AS "folderId", storage_object_id AS "storageObjectId", name, mime_type AS "mimeType", size_bytes AS "sizeBytes", sha256, created_at AS "createdAt", modified_at AS "modifiedAt", deleted_at AS "deletedAt"`, [id, userId]);
    return result.rows[0] ?? null;
  }

  async restore(id: string, userId: string): Promise<FileRecord | null> {
    const result = await this.db.query<FileRecord>(`UPDATE files SET deleted_at = NULL, modified_at = now() WHERE id = $1 AND user_id = $2 AND deleted_at IS NOT NULL RETURNING id, user_id AS "userId", folder_id AS "folderId", storage_object_id AS "storageObjectId", name, mime_type AS "mimeType", size_bytes AS "sizeBytes", sha256, created_at AS "createdAt", modified_at AS "modifiedAt", deleted_at AS "deletedAt"`, [id, userId]);
    return result.rows[0] ?? null;
  }

  async hardDelete(id: string, userId: string): Promise<boolean> {
    const result = await this.db.query(`DELETE FROM files WHERE id = $1 AND user_id = $2 AND deleted_at IS NOT NULL`, [id, userId]);
    return (result.rowCount ?? 0) > 0;
  }

  async rename(id: string, userId: string, name: string): Promise<FileRecord | null> {
    const result = await this.db.query<FileRecord>(`UPDATE files SET name = $1, modified_at = now() WHERE id = $2 AND user_id = $3 AND deleted_at IS NULL RETURNING id, user_id AS "userId", folder_id AS "folderId", storage_object_id AS "storageObjectId", name, mime_type AS "mimeType", size_bytes AS "sizeBytes", sha256, created_at AS "createdAt", modified_at AS "modifiedAt", deleted_at AS "deletedAt"`, [name, id, userId]);
    return result.rows[0] ?? null;
  }

  async move(id: string, userId: string, folderId: string): Promise<FileRecord | null> {
    const result = await this.db.query<FileRecord>(`UPDATE files SET folder_id = $1, modified_at = now() WHERE id = $2 AND user_id = $3 AND deleted_at IS NULL RETURNING id, user_id AS "userId", folder_id AS "folderId", storage_object_id AS "storageObjectId", name, mime_type AS "mimeType", size_bytes AS "sizeBytes", sha256, created_at AS "createdAt", modified_at AS "modifiedAt", deleted_at AS "deletedAt"`, [folderId, id, userId]);
    return result.rows[0] ?? null;
  }
}
