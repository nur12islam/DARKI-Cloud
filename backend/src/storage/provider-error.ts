export type StorageErrorCode =
  | "AUTHENTICATION"
  | "RATE_LIMITED"
  | "OBJECT_NOT_FOUND"
  | "OBJECT_TOO_LARGE"
  | "NETWORK"
  | "TEMPORARY"
  | "UNKNOWN";

export class StorageProviderError extends Error {
  constructor(
    message: string,
    readonly code: StorageErrorCode,
    readonly cause?: unknown,
  ) {
    super(message);
    this.name = "StorageProviderError";
  }
}
