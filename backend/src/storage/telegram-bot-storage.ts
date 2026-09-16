import { createHash } from "node:crypto";
import { Readable } from "node:stream";
import { TelegramBotClient } from "./telegram-bot-client.js";
import { StorageProviderError } from "./provider-error.js";
import type {
  DeleteObjectInput,
  ExistsInput,
  GetMetadataInput,
  GetObjectInput,
  PutObjectInput,
  StorageCapabilities,
  StorageHealth,
  StorageProvider,
  StoredObject,
  StoredObjectMetadata,
} from "./types.js";

const HOSTED_BOT_UPLOAD_LIMIT = 50 * 1024 * 1024;
const HOSTED_BOT_DOWNLOAD_LIMIT = 20 * 1024 * 1024;

type ObjectKey = { messageId: number; fileId: string };

function encodeKey(value: ObjectKey): string {
  return Buffer.from(JSON.stringify(value), "utf8").toString("base64url");
}

function decodeKey(value: string): ObjectKey {
  try {
    const parsed = JSON.parse(Buffer.from(value, "base64url").toString("utf8")) as ObjectKey;
    if (!Number.isInteger(parsed.messageId) || typeof parsed.fileId !== "string" || !parsed.fileId) {
      throw new Error("invalid key");
    }
    return parsed;
  } catch {
    throw new StorageProviderError("Invalid Telegram object key", "OBJECT_NOT_FOUND");
  }
}

async function readAtMost(stream: Readable, maxBytes: number): Promise<Buffer> {
  const chunks: Buffer[] = [];
  let total = 0;
  for await (const chunk of stream) {
    const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    total += buffer.length;
    if (total > maxBytes) throw new StorageProviderError("Object exceeds Telegram Bot API upload limit", "OBJECT_TOO_LARGE");
    chunks.push(buffer);
  }
  return Buffer.concat(chunks, total);
}

export class TelegramBotStorage implements StorageProvider {
  readonly name = "telegram";

  constructor(private readonly client: TelegramBotClient) {}

  async capabilities(): Promise<StorageCapabilities> {
    return {
      maxUploadBytes: HOSTED_BOT_UPLOAD_LIMIT,
      maxDownloadBytes: HOSTED_BOT_DOWNLOAD_LIMIT,
      supportsStreamingUpload: false,
      supportsStreamingDownload: true,
      supportsRangeDownload: false,
      supportsServerSideCopy: false,
    };
  }

  async putObject(input: PutObjectInput): Promise<StoredObject> {
    if (input.sizeBytes > HOSTED_BOT_UPLOAD_LIMIT) {
      throw new StorageProviderError("Object exceeds Telegram Bot API upload limit", "OBJECT_TOO_LARGE");
    }

    const body = await readAtMost(input.body, HOSTED_BOT_UPLOAD_LIMIT);
    if (body.length !== input.sizeBytes) {
      throw new StorageProviderError("Upload size does not match declared size", "UNKNOWN");
    }

    const sha256 = input.sha256 ?? createHash("sha256").update(body).digest("hex");
    const message = await this.client.sendDocument({
      body: Readable.from(body),
      filename: input.filename,
      mimeType: input.mimeType,
      sizeBytes: input.sizeBytes,
      caption: `darki:${input.idempotencyKey}`,
    });

    if (!message.document) {
      throw new StorageProviderError("Telegram did not return a document", "UNKNOWN");
    }

    return {
      provider: this.name,
      providerObjectKey: encodeKey({ messageId: message.message_id, fileId: message.document.file_id }),
      sizeBytes: message.document.file_size ?? input.sizeBytes,
      mimeType: message.document.mime_type ?? input.mimeType,
      sha256,
      createdAt: new Date(),
    };
  }

  async getObject(input: GetObjectInput): Promise<Readable> {
    const key = decodeKey(input.providerObjectKey);
    const file = await this.client.getFile(key.fileId);
    if (!file.file_path) throw new StorageProviderError("Telegram file path is unavailable", "OBJECT_NOT_FOUND");
    if ((file.file_size ?? 0) > HOSTED_BOT_DOWNLOAD_LIMIT) {
      throw new StorageProviderError("Object exceeds Telegram Bot API download limit", "OBJECT_TOO_LARGE");
    }
    return this.client.downloadFile(file.file_path);
  }

  async getMetadata(input: GetMetadataInput): Promise<StoredObjectMetadata | null> {
    const key = decodeKey(input.providerObjectKey);
    try {
      const file = await this.client.getFile(key.fileId);
      return {
        providerObjectKey: input.providerObjectKey,
        sizeBytes: file.file_size ?? 0,
        mimeType: null,
        filename: null,
        sha256: null,
      };
    } catch (error) {
      if (error instanceof StorageProviderError && error.code === "OBJECT_NOT_FOUND") return null;
      throw error;
    }
  }

  async exists(input: ExistsInput): Promise<boolean> {
    return (await this.getMetadata(input)) !== null;
  }

  async deleteObject(input: DeleteObjectInput): Promise<void> {
    const key = decodeKey(input.providerObjectKey);
    try {
      await this.client.deleteMessage(key.messageId);
    } catch (error) {
      if (error instanceof StorageProviderError && error.code === "OBJECT_NOT_FOUND") return;
      throw error;
    }
  }

  async healthCheck(): Promise<StorageHealth> {
    const started = performance.now();
    await this.client.getMe();
    return { ok: true, latencyMs: Math.round(performance.now() - started) };
  }
}
