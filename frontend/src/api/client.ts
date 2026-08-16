import axios from "axios";
import { storage } from "@/src/utils/storage";

const BASE = process.env.EXPO_PUBLIC_BACKEND_URL;

export const TOKEN_KEY = "iptv_token";
export const USER_KEY = "iptv_user";

export function absoluteUrl(pathOrUrl: string): string {
  if (!pathOrUrl) return "";
  if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) return pathOrUrl;
  return `${BASE}${pathOrUrl}`;
}

export const api = axios.create({
  baseURL: `${BASE}/api`,
  timeout: 45000,
});

api.interceptors.request.use(async (config) => {
  const token = await storage.secureGet<string>(TOKEN_KEY, "");
  if (token) {
    config.headers = config.headers ?? {};
    (config.headers as any).Authorization = `Bearer ${token}`;
  }
  return config;
});

export async function saveSession(token: string, user: any) {
  await storage.secureSet(TOKEN_KEY, token);
  await storage.setItem(USER_KEY, user);
}

export async function clearSession() {
  await storage.secureRemove(TOKEN_KEY);
  await storage.removeItem(USER_KEY);
}

export async function getStoredToken(): Promise<string | null> {
  return await storage.secureGet<string>(TOKEN_KEY, "");
}
