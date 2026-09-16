import { requireTelegramBotToken, requireTelegramStorageChatId } from "../config.js";
import { TelegramBotClient } from "./telegram-bot-client.js";
import { TelegramBotStorage } from "./telegram-bot-storage.js";
import type { StorageProvider } from "./types.js";

let provider: StorageProvider | undefined;

export function getStorageProvider(): StorageProvider {
  if (!provider) {
    provider = new TelegramBotStorage(
      new TelegramBotClient(requireTelegramBotToken(), requireTelegramStorageChatId()),
    );
  }
  return provider;
}
