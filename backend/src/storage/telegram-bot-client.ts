import { Readable } from "node:stream";
import { StorageProviderError } from "./provider-error.js";

const API_BASE = "https://api.telegram.org";
const FILE_BASE = "https://api.telegram.org/file";

type TelegramResponse<T> = {
  ok: boolean;
  result?: T;
  description?: string;
  error_code?: number;
  parameters?: { retry_after?: number };
};

type TelegramFile = {
  file_id: string;
  file_size?: number;
  file_path?: string;
};

type TelegramMessage = {
  message_id: number;
  document?: {
    file_id: string;
    file_unique_id: string;
    file_size?: number;
    file_name?: string;
    mime_type?: string;
  };
};

function classify(errorCode?: number, description?: string): StorageProviderError["code"] {
  if (errorCode === 401) return "AUTHENTICATION";
  if (errorCode === 429) return "RATE_LIMITED";
  const text = description?.toLowerCase() ?? "";
  if (text.includes("not found") || text.includes("file is too big")) {
    return text.includes("too big") ? "OBJECT_TOO_LARGE" : "OBJECT_NOT_FOUND";
  }
  if (errorCode !== undefined && errorCode >= 500) return "TEMPORARY";
  return "UNKNOWN";
}

export class TelegramBotClient {
  constructor(
    private readonly token: string,
    private readonly storageChatId: string,
  ) {}

  private apiUrl(method: string): string {
    return `${API_BASE}/bot${this.token}/${method}`;
  }

  private async api<T>(method: string, body?: BodyInit, headers?: HeadersInit): Promise<T> {
    let response: Response;
    try {
      response = await fetch(this.apiUrl(method), {
        method: body === undefined ? "GET" : "POST",
        body,
        headers,
      });
    } catch (error) {
      throw new StorageProviderError("Telegram network request failed", "NETWORK", error);
    }

    let payload: TelegramResponse<T>;
    try {
      payload = (await response.json()) as TelegramResponse<T>;
    } catch (error) {
      throw new StorageProviderError("Telegram returned invalid JSON", "NETWORK", error);
    }

    if (!response.ok || !payload.ok || payload.result === undefined) {
      throw new StorageProviderError(
        payload.description ?? `Telegram API request failed (${response.status})`,
        classify(payload.error_code, payload.description),
      );
    }

    return payload.result;
  }

  async sendDocument(input: {
    body: Readable;
    filename: string;
    mimeType: string | null;
    sizeBytes: number;
    caption?: string;
  }): Promise<TelegramMessage> {
    const form = new FormData();
    form.set("chat_id", this.storageChatId);
    form.set("document", new Blob([await Readable.toWeb(input.body).arrayBuffer()], {
      type: input.mimeType ?? "application/octet-stream",
    }), input.filename);
    if (input.caption) form.set("caption", input.caption);
    return this.api<TelegramMessage>("sendDocument", form);
  }

  async getFile(fileId: string): Promise<TelegramFile> {
    return this.api<TelegramFile>("getFile", JSON.stringify({ file_id: fileId }), {
      "content-type": "application/json",
    });
  }

  async downloadFile(filePath: string): Promise<Readable> {
    let response: Response;
    try {
      response = await fetch(`${FILE_BASE}/bot${this.token}/${filePath}`);
    } catch (error) {
      throw new StorageProviderError("Telegram file download failed", "NETWORK", error);
    }
    if (!response.ok || !response.body) {
      throw new StorageProviderError(
        `Telegram file download failed (${response.status})`,
        response.status >= 500 ? "TEMPORARY" : "OBJECT_NOT_FOUND",
      );
    }
    return Readable.fromWeb(response.body as import("node:stream/web").ReadableStream);
  }

  async deleteMessage(messageId: number): Promise<void> {
    await this.api<boolean>("deleteMessage", JSON.stringify({
      chat_id: this.storageChatId,
      message_id: messageId,
    }), { "content-type": "application/json" });
  }

  async getMe(): Promise<{ id: number; username?: string }> {
    return this.api("getMe");
  }
}
