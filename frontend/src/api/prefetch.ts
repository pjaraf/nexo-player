import { api } from "./client";

/**
 * Warms every big Xtream response into the backend cache in parallel so that
 * subsequent tab navigations feel instant. Fire-and-forget; failures are
 * swallowed on purpose — a failing preload should never block navigation.
 */
export function prefetchAll(): Promise<void> {
  return Promise.allSettled([
    api.get("/live/categories"),
    api.get("/live/channels"),
    api.get("/vod/categories"),
    api.get("/vod/streams", { params: { limit: 300 } }),
    api.get("/series/categories"),
    api.get("/series/list", { params: { limit: 300 } }),
  ]).then(() => undefined);
}
