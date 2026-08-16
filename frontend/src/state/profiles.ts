import { storage } from "@/src/utils/storage";

export type Profile = {
  id: string;
  name: string;
  color: string; // avatar tint
  isKids: boolean;
};

const PROFILES_KEY = "profiles_v1";
const ACTIVE_KEY = "active_profile_v1";
const PIN_KEY = "adult_pin_v1"; // secure

export const AVATAR_COLORS = [
  "#E50914", "#00E5FF", "#F59E0B", "#22C55E",
  "#A855F7", "#EC4899", "#3B82F6", "#F97316",
];

export function randomId() {
  return `${Date.now()}_${Math.floor(Math.random() * 9999)}`;
}

export async function getProfiles(): Promise<Profile[]> {
  const list = await storage.getItem<Profile[]>(PROFILES_KEY, []);
  return Array.isArray(list) ? list : [];
}

export async function saveProfiles(list: Profile[]) {
  await storage.setItem(PROFILES_KEY, list as any);
}

export async function ensureDefaultProfile(username: string): Promise<Profile[]> {
  const list = await getProfiles();
  if (list.length > 0) return list;
  const def: Profile = {
    id: randomId(),
    name: username || "Principal",
    color: AVATAR_COLORS[0],
    isKids: false,
  };
  await saveProfiles([def]);
  return [def];
}

export async function addProfile(p: Omit<Profile, "id">): Promise<Profile> {
  const list = await getProfiles();
  const created: Profile = { ...p, id: randomId() };
  await saveProfiles([...list, created]);
  return created;
}

export async function updateProfile(id: string, patch: Partial<Profile>) {
  const list = await getProfiles();
  const next = list.map((p) => (p.id === id ? { ...p, ...patch } : p));
  await saveProfiles(next);
}

export async function deleteProfile(id: string) {
  const list = await getProfiles();
  await saveProfiles(list.filter((p) => p.id !== id));
  const active = await getActiveProfileId();
  if (active === id) await setActiveProfileId("");
}

export async function getActiveProfileId(): Promise<string> {
  const v = await storage.getItem<string>(ACTIVE_KEY, "");
  return v || "";
}

export async function setActiveProfileId(id: string) {
  await storage.setItem(ACTIVE_KEY, id);
}

export async function getActiveProfile(): Promise<Profile | null> {
  const id = await getActiveProfileId();
  if (!id) return null;
  const list = await getProfiles();
  return list.find((p) => p.id === id) || null;
}

// -------- PIN --------
export async function getPin(): Promise<string> {
  return (await storage.secureGet<string>(PIN_KEY, "")) || "";
}
export async function setPin(pin: string) {
  await storage.secureSet(PIN_KEY, pin);
}
export async function clearPin() {
  await storage.secureRemove(PIN_KEY);
}

// -------- Kids category filter --------
const KIDS_KEYWORDS = [
  "infantil", "niños", "ninos", "kids", "dibujo", "cartoon", "disney jr",
  "disney junior", "nick jr", "nickelodeon", "baby", "senpai", "anim",
];

export function isKidsCategoryName(name?: string) {
  if (!name) return false;
  const s = name.toLowerCase();
  return KIDS_KEYWORDS.some((k) => s.includes(k));
}
