import { storage } from "@/src/utils/storage";

export type ProgressItem = {
  key: string;              // unique: `${kind}:${id}`
  kind: "movie" | "series";
  id: string;               // stream_id (movie) or series_id
  episodeId?: string;       // episode id if series
  title: string;
  image?: string;
  streamUrl: string;
  positionMs: number;
  durationMs: number;
  updatedAt: number;
};

const KEY = (profileId: string) => `progress_${profileId}`;
const MAX_ITEMS = 30;

export async function getProgressList(profileId: string): Promise<ProgressItem[]> {
  const list = await storage.getItem<ProgressItem[]>(KEY(profileId), []);
  return (Array.isArray(list) ? list : []).sort((a, b) => b.updatedAt - a.updatedAt);
}

export async function upsertProgress(profileId: string, item: ProgressItem) {
  const list = await getProgressList(profileId);
  const rest = list.filter((p) => p.key !== item.key);
  const next = [{ ...item, updatedAt: Date.now() }, ...rest].slice(0, MAX_ITEMS);
  await storage.setItem(KEY(profileId), next as any);
}

export async function removeProgress(profileId: string, k: string) {
  const list = await getProgressList(profileId);
  await storage.setItem(KEY(profileId), list.filter((p) => p.key !== k) as any);
}

export async function getProgressFor(
  profileId: string,
  k: string,
): Promise<ProgressItem | null> {
  const list = await getProgressList(profileId);
  return list.find((p) => p.key === k) || null;
}
