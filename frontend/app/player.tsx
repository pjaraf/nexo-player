import { useCallback, useEffect, useRef, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ActivityIndicator,
  Pressable,
  Platform,
} from "react-native";
import { useLocalSearchParams, useRouter } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { useVideoPlayer, VideoView } from "expo-video";
import { useEventListener } from "expo";
import { StatusBar } from "expo-status-bar";
import { api, absoluteUrl } from "@/src/api/client";
import { colors, radius } from "@/src/theme";
import { getActiveProfileId } from "@/src/state/profiles";
import { upsertProgress, ProgressItem } from "@/src/state/progress";

type LiveChannel = { id: string; name: string; logo?: string; group: string };

export default function Player() {
  const router = useRouter();
  const params = useLocalSearchParams<{
    type: string;
    id?: string;
    url?: string;
    title?: string;
    kind?: string;
    contentId?: string;
    image?: string;
    resumeMs?: string;
    category?: string;
    nextUrl?: string;
    nextTitle?: string;
    nextContentId?: string;
    nextEpImage?: string;
  }>();

  const { type, id, url, title, kind, contentId, image, resumeMs,
    category, nextUrl, nextTitle, nextContentId, nextEpImage } = params;

  const [videoUrl, setVideoUrl] = useState<string | null>(
    url ? absoluteUrl(String(url)) : null,
  );
  const [currentTitle, setCurrentTitle] = useState<string>(String(title || ""));
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [profileId, setProfileId] = useState<string>("");
  const [liveList, setLiveList] = useState<LiveChannel[]>([]);
  const [liveIdx, setLiveIdx] = useState<number>(-1);
  const [showControls, setShowControls] = useState(true);

  const lastSavedRef = useRef<number>(0);
  const resumedRef = useRef<boolean>(false);
  const advancedRef = useRef<boolean>(false);
  const controlsTimer = useRef<any>(null);

  const isLive = type === "live";

  const armControlsHide = useCallback(() => {
    setShowControls(true);
    if (controlsTimer.current) clearTimeout(controlsTimer.current);
    controlsTimer.current = setTimeout(() => setShowControls(false), 4000);
  }, []);

  useEffect(() => {
    armControlsHide();
    return () => {
      if (controlsTimer.current) clearTimeout(controlsTimer.current);
    };
  }, [armControlsHide]);

  // Initial load
  useEffect(() => {
    (async () => {
      setProfileId(await getActiveProfileId());
      try {
        if (isLive && id) {
          const { data } = await api.get("/live/play", { params: { id } });
          setVideoUrl(absoluteUrl(data.url));
          setCurrentTitle(data.name || String(title || ""));
          // Preload the category list for prev/next navigation
          if (category) {
            const list = await api.get("/live/channels", {
              params: { category, limit: 500 },
            });
            setLiveList(list.data?.channels || []);
            const found = (list.data?.channels || []).findIndex(
              (c: LiveChannel) => String(c.id) === String(id),
            );
            setLiveIdx(found);
          }
        } else if (url) {
          setVideoUrl(absoluteUrl(String(url)));
        }
      } catch {
        setError("No se pudo cargar el stream");
      } finally {
        setLoading(false);
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLive, id, url, category]);

  const player = useVideoPlayer(videoUrl || "", (p) => {
    p.loop = false;
    p.play();
  });

  // Replace source when switching channels
  useEffect(() => {
    if (!videoUrl) return;
    try {
      player.replace(videoUrl);
      player.play();
    } catch {}
    resumedRef.current = false;
    advancedRef.current = false;
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [videoUrl]);

  useEventListener(player, "statusChange", ({ status, error: playerError }) => {
    if (status === "error" || playerError) {
      setError("Este contenido no se puede reproducir en este dispositivo");
    }
    if (status === "readyToPlay" && !resumedRef.current && resumeMs) {
      const ms = Number(resumeMs);
      if (ms > 5000) {
        try {
          player.currentTime = ms / 1000;
        } catch {}
      }
      resumedRef.current = true;
    }
  });

  // Save progress every ~5s
  useEventListener(player, "timeUpdate", ({ currentTime }) => {
    if (!profileId || !contentId || isLive || !kind) return;
    const now = Date.now();
    if (now - lastSavedRef.current < 5000) return;
    const positionMs = Math.floor((currentTime || 0) * 1000);
    const durationMs = Math.floor((player.duration || 0) * 1000);
    if (positionMs < 3000 || durationMs < 10000) return;
    if (positionMs / durationMs > 0.95) return;
    lastSavedRef.current = now;
    const item: ProgressItem = {
      key: `${kind}:${contentId}`,
      kind: kind === "series" ? "series" : "movie",
      id: String(contentId),
      title: String(currentTitle || "Contenido"),
      image: image ? String(image) : undefined,
      streamUrl: String(videoUrl || url || ""),
      positionMs,
      durationMs,
      updatedAt: now,
    };
    upsertProgress(profileId, item).catch(() => {});
  });

  // Auto-play next episode / navigate to next when playback ends
  useEventListener(player, "playToEnd", () => {
    if (advancedRef.current) return;
    if (nextUrl) {
      advancedRef.current = true;
      router.replace({
        pathname: "/player",
        params: {
          url: String(nextUrl),
          title: String(nextTitle || "Siguiente"),
          type: "series",
          kind: "series",
          contentId: String(nextContentId || ""),
          image: nextEpImage ? String(nextEpImage) : (image ? String(image) : ""),
        },
      });
    }
  });

  const jumpChannel = useCallback((delta: number) => {
    if (!isLive || liveList.length === 0 || liveIdx < 0) return;
    const nextIdx = (liveIdx + delta + liveList.length) % liveList.length;
    const target = liveList[nextIdx];
    if (!target) return;
    setLoading(true);
    setError(null);
    (async () => {
      try {
        const { data } = await api.get("/live/play", { params: { id: target.id } });
        setVideoUrl(absoluteUrl(data.url));
        setCurrentTitle(data.name || target.name);
        setLiveIdx(nextIdx);
      } catch {
        setError("No se pudo cambiar de canal");
      } finally {
        setLoading(false);
        armControlsHide();
      }
    })();
  }, [isLive, liveList, liveIdx, armControlsHide]);

  // Global key listener for Web / Android TV Webview environments
  useEffect(() => {
    if (Platform.OS !== "web") return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (!isLive) return;
      if (e.key === "ArrowUp" || e.key === "ChannelUp") {
        e.preventDefault();
        jumpChannel(1);
      } else if (e.key === "ArrowDown" || e.key === "ChannelDown") {
        e.preventDefault();
        jumpChannel(-1);
      } else if (e.key === "ArrowLeft") {
        e.preventDefault();
        jumpChannel(-1);
      } else if (e.key === "ArrowRight") {
        e.preventDefault();
        jumpChannel(1);
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isLive, jumpChannel]);

  return (
    <Pressable
      style={styles.root}
      onPress={armControlsHide}
      testID="player-screen"
      focusable
      hasTVPreferredFocus
      onKeyPress={(e) => {
        if (!isLive) return;
        const key = e.nativeEvent.key;
        if (key === "ArrowUp" || key === "Up" || key === "ChannelUp") {
          jumpChannel(1);
        } else if (key === "ArrowDown" || key === "Down" || key === "ChannelDown") {
          jumpChannel(-1);
        } else if (key === "ArrowLeft" || key === "Left") {
          jumpChannel(-1);
        } else if (key === "ArrowRight" || key === "Right") {
          jumpChannel(1);
        }
      }}
    >
      <StatusBar hidden />
      {videoUrl ? (
        <VideoView
          player={player}
          style={StyleSheet.absoluteFill}
          contentFit="contain"
          nativeControls
          allowsFullscreen
          allowsPictureInPicture
        />
      ) : null}

      {loading && (
        <View style={styles.overlay}>
          <ActivityIndicator color="#fff" size="large" />
        </View>
      )}

      {error && (
        <View style={styles.overlay}>
          <Ionicons name="alert-circle" size={40} color={colors.primary} />
          <Text style={styles.errorText}>{error}</Text>
          <TouchableOpacity style={styles.retry} onPress={() => router.back()}>
            <Text style={styles.retryText}>Volver</Text>
          </TouchableOpacity>
        </View>
      )}

      {showControls && (
        <>
          <Pressable
            style={styles.closeBtn}
            onPress={() => router.back()}
            testID="player-close-button"
            focusable
          >
            {({ focused }) => (
              <View
                style={[
                  styles.circleBtn,
                  focused && styles.circleBtnFocused,
                ]}
              >
                <Ionicons name="close" size={22} color="#fff" />
              </View>
            )}
          </Pressable>

          {currentTitle && !error ? (
            <View style={styles.titleBar} pointerEvents="none">
              {isLive && (
                <View style={styles.liveDot}>
                  <View style={styles.liveDotInner} />
                  <Text style={styles.liveText}>EN VIVO</Text>
                </View>
              )}
              <Text style={styles.titleText} numberOfLines={1}>
                {currentTitle}
              </Text>
              {isLive && liveIdx >= 0 && liveList.length > 0 && (
                <Text style={styles.channelHint}>
                  {liveIdx + 1} / {liveList.length}
                </Text>
              )}
            </View>
          ) : null}

          {isLive && liveList.length > 1 && (
            <>
              <Pressable
                style={[styles.chBtn, styles.chBtnLeft]}
                onPress={() => jumpChannel(-1)}
                testID="player-prev-channel"
                focusable
              >
                {({ focused }) => (
                  <View style={[styles.pillBtn, focused && styles.pillBtnFocused]}>
                    <Ionicons name="chevron-back" size={20} color="#fff" />
                    <Text style={styles.pillText}>Canal -</Text>
                  </View>
                )}
              </Pressable>
              <Pressable
                style={[styles.chBtn, styles.chBtnRight]}
                onPress={() => jumpChannel(1)}
                testID="player-next-channel"
                focusable
              >
                {({ focused }) => (
                  <View style={[styles.pillBtn, focused && styles.pillBtnFocused]}>
                    <Text style={styles.pillText}>Canal +</Text>
                    <Ionicons name="chevron-forward" size={20} color="#fff" />
                  </View>
                )}
              </Pressable>
            </>
          )}
        </>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: "#000" },
  overlay: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: "center",
    alignItems: "center",
    backgroundColor: "rgba(0,0,0,0.85)",
    gap: 12,
    padding: 20,
  },
  errorText: { color: "#fff", fontSize: 15, textAlign: "center" },
  retry: {
    marginTop: 10,
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 999,
    backgroundColor: colors.primary,
  },
  retryText: { color: "#fff", fontWeight: "800", letterSpacing: 2 },
  closeBtn: { position: "absolute", top: 40, right: 16, zIndex: 10 },
  circleBtn: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: "rgba(0,0,0,0.6)",
    justifyContent: "center",
    alignItems: "center",
    borderWidth: 2,
    borderColor: "transparent",
  },
  circleBtnFocused: {
    borderColor: colors.primary,
    shadowColor: colors.primary,
    shadowOpacity: 0.9,
    shadowRadius: 12,
    elevation: 12,
  },
  titleBar: {
    position: "absolute",
    top: 44,
    left: 72,
    right: 72,
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  titleText: {
    color: "#fff",
    fontSize: 14,
    fontWeight: "700",
    flex: 1,
    textAlign: "center",
  },
  liveDot: {
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 4,
    backgroundColor: colors.primary,
  },
  liveDotInner: { width: 5, height: 5, borderRadius: 3, backgroundColor: "#fff" },
  liveText: { color: "#fff", fontSize: 9, fontWeight: "900", letterSpacing: 1 },
  channelHint: {
    color: "#fff",
    opacity: 0.6,
    fontSize: 11,
    fontWeight: "700",
  },
  chBtn: {
    position: "absolute",
    top: "50%",
    marginTop: -22,
    zIndex: 10,
  },
  chBtnLeft: { left: 12 },
  chBtnRight: { right: 12 },
  pillBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: radius.pill,
    backgroundColor: "rgba(0,0,0,0.7)",
    borderWidth: 2,
    borderColor: "transparent",
  },
  pillBtnFocused: {
    borderColor: colors.primary,
    backgroundColor: "rgba(229,9,20,0.35)",
    shadowColor: colors.primary,
    shadowOpacity: 0.9,
    shadowRadius: 12,
    elevation: 12,
    transform: [{ scale: 1.05 }],
  },
  pillText: { color: "#fff", fontWeight: "800", fontSize: 12, letterSpacing: 1 },
});