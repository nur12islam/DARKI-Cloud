export type DbExecutor = {
  query<T = unknown>(text: string, values?: readonly unknown[]): Promise<{
    rows: T[];
    rowCount: number | null;
  }>;
};

export type UserRecord = {
  id: string;
  telegramUserId: string;
  displayName: string;
  createdAt: Date;
};

export type DeviceRecord = {
  id: string;
  userId: string;
  deviceName: string;
  platform: string;
  lastSeenAt: Date | null;
  syncCursor: string;
  createdAt: Date;
};

export type FolderRecord = {
  id: string;
  userId: string;
  parentId: string | null;
  name: string;
  createdAt: Date;
  modifiedAt: Date;
  deletedAt: Date | null;
};

export type FileRecord = {
  id: string;
  userId: string;
  folderId: string;
  storageObjectId: string | null;
  name: string;
  mimeType: string | null;
  sizeBytes: string | null;
  sha256: string | null;
  createdAt: Date;
  modifiedAt: Date;
  deletedAt: Date | null;
};

export type StorageObjectRecord = {
  id: string;
  userId: string;
  provider: string;
  providerObjectKey: string;
  sizeBytes: string;
  mimeType: string | null;
  sha256: string | null;
  state: string;
  createdAt: Date;
  modifiedAt: Date;
  deletedAt: Date | null;
};

export type SyncChangeRecord = {
  sequence: string;
  userId: string;
  deviceId: string | null;
  operationId: string;
  entityType: string;
  entityId: string;
  operation: string;
  payload: Record<string, unknown>;
  createdAt: Date;
};
