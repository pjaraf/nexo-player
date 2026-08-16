import { useEffect, useMemo, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  ActivityIndicator,
  Pressable,
} from "react-native";
import { Image } from "expo-image";
import { LinearGradient } from "expo-linear-gradient";
import { Ionicons } from "@expo/vector-icons";
import { useLocalSearchParams, useRouter } from "expo-router";
import { SafeAreaView } from "react-native-safe-area-context";
import { api, absoluteUrl } from "@/src/api/client";
import { colors, radius } from "@/src/theme";
import { getActiveProfileId } from "@/src/state/profiles";
import { isFav, toggleFav } from "@/src/state/favorites";

const FALLBACK =
  "https://images.pexels.com/photos/3137890/pexels-photo-3137890.jpeg?auto=compress&cs=tinysrgb&dpr=2&h=650&w=940";

type Episode = {
  id: string | number;
  title?: string;
  episode_num?: number;
  season?: number;
  stream_url: string;
  info?: { plot?: string; movie_image?: string; duration?: string };
};

export default function SeriesDetail() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const [info, setInfo] = useState<any>(null);
  const [episodes, setEpisodes] = useState<Record<string, Episode[]>>({});
  const [selectedSeason, setSelectedSeason] = useState<string>("");
  const [loading, setLoading] = useState(true);
  const [fav, setFav] = useState(false);
  const [profileId, setProfileId] = useState<string>("");

  useEffect(() => {
    (async () => {
      try {
        const pid = await getActiveProfileId();
        setProfileId(pid);
        const { data } = await api.get(`/series/info/${id}`);
        setInfo(data.info || {});
        setEpisodes(data.episodes || {});
        const first = Object.keys(data.episodes || {})[0];
        if (first) setSelectedSeason(first);
        if (pid && id) setFav(await isFav("series", pid, String(id)));
      } catch {
      } finally {
        setLoading(false);
      }
    })();
  }, [id]);

  const seasons = useMemo(() => Object.keys(episodes).sort((a, b) => Number(a) - Number(b)), [episodes]);
  const eps = episodes[selectedSeason] || [];

  if (loading) {
    return (
      <View style={[styles.root, { justifyContent: "center", alignItems: "center" }]}>
        <ActivityIndicator color={colors.primary} />
      </View>
    );
  }

  const title = info?.name || "Serie";
  const cover = info?.cover || info?.backdrop_path?.[0];

  const onFav = async () => {
    if (!profileId || !id) return;
    const next = await toggleFav("series", profileId, {
      id: String(id),
      title,
      image: cover,
    });
    setFav(next);
  };

  return (
    <View style={styles.root}>
      <ScrollView contentContainerStyle={{ paddingBottom: 80 }} showsVerticalScrollIndicator={false}>
        <View style={styles.heroWrap}>
          <Image
            source={cover || FALLBACK}
            style={StyleSheet.absoluteFill}
            contentFit="cover"
            transition={300}
          />
          <LinearGradient
            colors={["rgba(5,5,5,0.2)", "rgba(5,5,5,0.7)", "#050505"]}
            style={StyleSheet.absoluteFill}
          />
          <SafeAreaView edges={["top"]} style={styles.topBar}>
            <TouchableOpacity
              onPress={() => router.back()}
              style={styles.backBtn}
              testID="series-back-button"
            >
              <Ionicons name="chevron-back" size={22} color="#fff" />
            </TouchableOpacity>
            <TouchableOpacity
              onPress={onFav}
              style={styles.backBtn}
              testID="series-fav-button"
            >
              <Ionicons
                name={fav ? "heart" : "heart-outline"}
                size={22}
                color={fav ? colors.primary : "#fff"}
              />
            </TouchableOpacity>
          </SafeAreaView>
        </View>

        <View style={styles.body}>
          <Text style={styles.title} testID="series-title">
            {title}
          </Text>
          <View style={styles.metaRow}>
            {info?.releaseDate ? <Meta text={String(info.releaseDate).slice(0, 4)} /> : null}
            {info?.rating ? <Meta text={`★ ${info.rating}`} color="#FFD700" /> : null}
            {info?.genre ? <Meta text={String(info.genre).split(",")[0]} /> : null}
          </View>

          {info?.plot ? <Text style={styles.desc}>{info.plot}</Text> : null}

          {seasons.length > 0 && (
            <>
              <Text style={styles.section}>Temporadas</Text>
              <ScrollView
                horizontal
                showsHorizontalScrollIndicator={false}
                contentContainerStyle={{ gap: 8, paddingBottom: 4 }}
              >
                {seasons.map((s) => {
                  const active = selectedSeason === s;
                  return (
                    <Pressable
                      key={s}
                      focusable
                      onPress={() => setSelectedSeason(s)}
                      testID={`season-chip-${s}`}
                      style={({ focused }) => [
                        styles.seasonChip,
                        active && styles.seasonActive,
                        focused && styles.seasonFocused,
                      ]}
                    >
                      <Text style={[styles.seasonText, active && { color: "#000" }]}>
                        Temporada {s}
                      </Text>
                    </Pressable>
                  );
                })}
              </ScrollView>

              <Text style={styles.section}>Episodios · {eps.length}</Text>
              <View style={{ gap: 10 }}>
                {eps.map((ep, idx) => {
                  const nextEp = eps[idx + 1];
                  return (
                    <Pressable
                      key={String(ep.id)}
                      focusable
                      testID={`episode-${ep.id}`}
                      onPress={() =>
                        router.push({
                          pathname: "/player",
                          params: {
                            url: absoluteUrl(ep.stream_url),
                            title: `${title} · E${ep.episode_num}`,
                            type: "series",
                            kind: "series",
                            contentId: `${id}_${ep.id}`,
                            image: ep.info?.movie_image || cover || "",
                            nextUrl: nextEp ? absoluteUrl(nextEp.stream_url) : "",
                            nextTitle: nextEp
                              ? `${title} · E${nextEp.episode_num}`
                              : "",
                            nextContentId: nextEp ? `${id}_${nextEp.id}` : "",
                            nextEpImage: nextEp?.info?.movie_image || cover || "",
                          },
                        })
                      }
                      style={({ focused }) => [
                        styles.epCard,
                        focused && styles.epCardFocused,
                      ]}
                    >
                    <Image
                      source={ep.info?.movie_image || cover || FALLBACK}
                      style={styles.epThumb}
                      contentFit="cover"
                    />
                    <View style={{ flex: 1 }}>
                      <Text style={styles.epNumber}>Ep {ep.episode_num}</Text>
                      <Text style={styles.epTitle} numberOfLines={2}>
                        {ep.title || `Episodio ${ep.episode_num}`}
                      </Text>
                      {ep.info?.duration ? (
                        <Text style={styles.epDuration}>{ep.info.duration}</Text>
                      ) : null}
                    </View>
                    <View style={styles.epPlay}>
                      <Ionicons name="play" size={16} color="#fff" />
                    </View>
                    </Pressable>
                  );
                })}
              </View>
            </>
          )}
        </View>
      </ScrollView>
    </View>
  );
}

function Meta({ text, color }: { text: string; color?: string }) {
  return (
    <View style={styles.metaChip}>
      <Text style={[styles.metaText, color && { color }]}>{text}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bg },
  heroWrap: { height: 420 },
  topBar: {
    paddingHorizontal: 16,
    paddingTop: 8,
    flexDirection: "row",
    justifyContent: "space-between",
  },
  backBtn: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: "rgba(0,0,0,0.6)",
    justifyContent: "center",
    alignItems: "center",
  },
  body: { padding: 20, marginTop: -80 },
  title: { color: "#fff", fontSize: 28, fontWeight: "900", letterSpacing: -1 },
  metaRow: { flexDirection: "row", gap: 8, marginTop: 10, marginBottom: 12, flexWrap: "wrap" },
  metaChip: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    backgroundColor: "rgba(255,255,255,0.08)",
    borderRadius: radius.sm,
  },
  metaText: { color: colors.textSecondary, fontSize: 11, fontWeight: "700" },
  desc: { color: "#fff", fontSize: 14, lineHeight: 22, marginTop: 8 },
  section: {
    color: colors.textSecondary,
    fontSize: 11,
    fontWeight: "800",
    letterSpacing: 3,
    marginTop: 24,
    marginBottom: 10,
  },
  seasonChip: {
    height: 36,
    paddingHorizontal: 16,
    borderRadius: radius.pill,
    backgroundColor: colors.surface,
    justifyContent: "center",
    borderWidth: 1,
    borderColor: colors.border,
    flexShrink: 0,
  },
  seasonActive: { backgroundColor: "#fff", borderColor: "#fff" },
  seasonFocused: {
    borderColor: colors.primary,
    borderWidth: 2,
    shadowColor: colors.primary,
    shadowOpacity: 0.9,
    shadowRadius: 10,
    elevation: 10,
    transform: [{ scale: 1.06 }],
  },
  seasonText: { color: colors.textSecondary, fontWeight: "700", fontSize: 12 },
  epCard: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    backgroundColor: colors.surface,
    borderRadius: radius.md,
    padding: 10,
    borderWidth: 2,
    borderColor: colors.border,
  },
  epCardFocused: {
    borderColor: colors.primary,
    shadowColor: colors.primary,
    shadowOpacity: 0.9,
    shadowRadius: 12,
    elevation: 12,
    transform: [{ scale: 1.02 }],
  },
  epThumb: { width: 120, height: 70, borderRadius: radius.sm, backgroundColor: colors.bg },
  epNumber: { color: colors.primary, fontSize: 10, fontWeight: "900", letterSpacing: 2 },
  epTitle: { color: "#fff", fontSize: 13, fontWeight: "700", marginTop: 2 },
  epDuration: { color: colors.textSecondary, fontSize: 11, marginTop: 4 },
  epPlay: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: colors.primary,
    justifyContent: "center",
    alignItems: "center",
  },
});
