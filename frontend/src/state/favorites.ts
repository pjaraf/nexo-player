import { storage } from "@/src/utils/storage";

export type FavItem = {
  id: string; // stringified stream_id/series_id/channel id
  title: string;
  image?: string;
  addedAt: number;
};

export type FavKind = "movies" | "series" | "live";

const key = (kind: FavKind, profileId: string) =>
  `favs_${kind}_${profileId}`;

export async function getFavs(kind: FavKind, profileId: string): Promise<FavItem[]> {
  const list = await storage.getItem<FavItem[]>(key(kind, profileId), []);
  return Array.isArray(list) ? list : [];
}

export async function isFav(kind: FavKind, profileId: string, id: string): Promise<boolean> {
  const list = await getFavs(kind, profileId);
  return list.some((f) => f.id === id);
}

export async function toggleFav(
  kind: FavKind,
  profileId: string,
  item: Omit<FavItem, "addedAt">,
): Promise<boolean> {
  const list = await getFavs(kind, profileId);
  const exists = list.some((f) => f.id === item.id);
  const next = exists
    ? list.filter((f) => f.id !== item.id)
    : [{ ...item, addedAt: Date.now() }, ...list];
  await storage.setItem(key(kind, profileId), next as any);
  return !exists;
}

export async function removeFav(kind: FavKind, profileId: string, id: string) {
  const list = await getFavs(kind, profileId);
  await storage.setItem(key(kind, profileId), list.filter((f) => f.id !== id) as any);
}
