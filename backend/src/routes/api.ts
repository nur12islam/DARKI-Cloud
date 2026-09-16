import { Router } from "express";
import { requireTelegramBotToken } from "../config.js";
import { AuthService } from "../auth/auth-service.js";
import { requireAuth, type AuthenticatedRequest } from "../auth/middleware.js";
import type { TelegramLoginPayload } from "../auth/types.js";
import { FilesystemService } from "../services/filesystem-service.js";
import { FileService } from "../services/file-service.js";
import { FileLifecycleService } from "../services/file-lifecycle-service.js";
import { getStorageProvider } from "../storage/provider.js";

const authService = new AuthService();
const filesystemService = new FilesystemService();
const storage = getStorageProvider();
const fileService = new FileService(storage);
const fileLifecycleService = new FileLifecycleService(storage);

export function createApiRouter(): Router {
  const router = Router();
  const deviceId = (req: AuthenticatedRequest) => req.header("x-device-id") || null;

  router.post("/auth/telegram", async (req, res, next) => {
    try { const user = await authService.loginWithTelegram(req.body as TelegramLoginPayload, requireTelegramBotToken()); res.status(200).json({ user, token: await authService.createSession(user.id), tokenType: "Bearer" }); }
    catch (error) { next(error); }
  });
  router.post("/auth/logout", requireAuth, async (req: AuthenticatedRequest, res, next) => { try { if (req.sessionToken) await authService.revokeSession(req.sessionToken); res.status(204).send(); } catch (error) { next(error); } });
  router.get("/me", requireAuth, async (req: AuthenticatedRequest, res, next) => { try { res.json({ user: await authService.authenticateUser(req.userId!) }); } catch (error) { next(error); } });

  router.post("/devices", requireAuth, async (req: AuthenticatedRequest, res, next) => {
    try { const { deviceName, platform } = req.body as { deviceName?: string; platform?: string }; if (!deviceName?.trim() || !platform?.trim()) { res.status(400).json({ error: { code: "INVALID_INPUT", message: "deviceName and platform are required" } }); return; } res.status(201).json({ device: await filesystemService.registerDevice(req.userId!, deviceName, platform) }); }
    catch (error) { next(error); }
  });
  router.get("/storage/capabilities", requireAuth, async (_req, res, next) => { try { res.json({ provider: fileService.storageName, capabilities: await fileService.capabilities() }); } catch (error) { next(error); } });
  router.get("/storage/health", requireAuth, async (_req, res, next) => { try { res.json(await fileService.healthCheck()); } catch (error) { next(error); } });

  router.get("/folders/:folderId", requireAuth, async (req: AuthenticatedRequest, res, next) => { try { res.json(await filesystemService.listFolder(req.userId!, req.params.folderId)); } catch (error) { next(error); } });
  router.post("/folders", requireAuth, async (req: AuthenticatedRequest, res, next) => { try { const { parentId, name } = req.body as { parentId?: string; name?: string }; if (!parentId || !name) { res.status(400).json({ error: { code: "INVALID_INPUT", message: "parentId and name are required" } }); return; } res.status(201).json({ folder: await filesystemService.createFolder(req.userId!, parentId, name, deviceId(req)) }); } catch (error) { next(error); } });
  router.patch("/folders/:folderId", requireAuth, async (req: AuthenticatedRequest, res, next) => { try { const { name } = req.body as { name?: string }; if (!name) { res.status(400).json({ error: { code: "INVALID_INPUT", message: "name is required" } }); return; } res.json({ folder: await filesystemService.renameFolder(req.userId!, req.params.folderId, name, deviceId(req)) }); } catch (error) { next(error); } });
  router.post("/folders/:folderId/move", requireAuth, async (req: AuthenticatedRequest, res, next) => { try { const { parentId } = req.body as { parentId?: string }; if (!parentId) { res.status(400).json({ error: { code: "INVALID_INPUT", message: "parentId is required" } }); return; } res.json({ folder: await filesystemService.moveFolder(req.userId!, req.params.folderId, parentId, deviceId(req)) }); } catch (error) { next(error); } });

  router.post("/files", requireAuth, async (req: AuthenticatedRequest, res, next) => { try { const folderId = typeof req.query.folderId === "string" ? req.query.folderId : undefined; const name = typeof req.query.name === "string" ? req.query.name : undefined; const mimeType = req.header("content-type")?.split(";", 1)[0]?.trim() || null; const sizeBytes = Number(req.header("content-length")); if (!folderId || !name || !Number.isSafeInteger(sizeBytes) || sizeBytes < 0) { res.status(400).json({ error: { code: "INVALID_INPUT", message: "folderId, name, and a valid Content-Length are required" } }); return; } res.status(201).json({ file: await fileService.upload({ userId: req.userId!, folderId, name, mimeType, sizeBytes, body: req, deviceId: deviceId(req) }) }); } catch (error) { next(error); } });
  router.get("/files/:fileId/content", requireAuth, async (req: AuthenticatedRequest, res, next) => { try { const result = await fileService.getDownload(req.userId!, req.params.fileId); res.setHeader("Content-Type", result.file.mimeType ?? "application/octet-stream"); if (result.file.sizeBytes !== null) res.setHeader("Content-Length", result.file.sizeBytes); res.setHeader("Content-Disposition", `attachment; filename*=UTF-8''${encodeURIComponent(result.file.name)}`); result.body.pipe(res); } catch (error) { next(error); } });
  router.patch("/files/:fileId", requireAuth, async (req: AuthenticatedRequest, res, next) => { try { const { name } = req.body as { name?: string }; if (!name) { res.status(400).json({ error: { code: "INVALID_INPUT", message: "name is required" } }); return; } res.json({ file: await filesystemService.renameFile(req.userId!, req.params.fileId, name, deviceId(req)) }); } catch (error) { next(error); } });
  router.post("/files/:fileId/move", requireAuth, async (req: AuthenticatedRequest, res, next) => { try { const { folderId } = req.body as { folderId?: string }; if (!folderId) { res.status(400).json({ error: { code: "INVALID_INPUT", message: "folderId is required" } }); return; } res.json({ file: await filesystemService.moveFile(req.userId!, req.params.fileId, folderId, deviceId(req)) }); } catch (error) { next(error); } });
  router.delete("/files/:fileId", requireAuth, async (req: AuthenticatedRequest, res, next) => { try { res.json(await fileLifecycleService.delete(req.userId!, req.params.fileId, deviceId(req))); } catch (error) { next(error); } });
  router.post("/files/:fileId/restore", requireAuth, async (req: AuthenticatedRequest, res, next) => { try { res.json({ file: await fileLifecycleService.restore(req.userId!, req.params.fileId, deviceId(req)) }); } catch (error) { next(error); } });

  return router;
}
