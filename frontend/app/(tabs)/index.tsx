import { useCallback, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  ActivityIndicator,
  Dimensions,
  RefreshControl,
  Pressable,
} from "react-native";
import { Image } from "expo-image";
import { LinearGradient } from "expo-linear-gradient";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { api } from "@/src/api/client";
import { colors, radius } from "@/src/theme";
import { getActiveProfile, isKidsCategoryName, Profile } from "@/src/state/profiles";
import { getProgressList, ProgressItem, removeProgress } from "@/src/state/progress";

const { width } = Dimensions.get("window");
const HERO_H = Math.min(520, width * 1.15);
const FALLBACK_POSTER =
  "https://images.pexels.com/photos/12838778/pexels-photo-12838778.jpeg?auto=compress&cs=tinysrgb&dpr=2&h=650&w=940";

type Movie = { stream_id: number; name: string; stream_icon?: string; category_id?: string };
type Series = { series_id: number; name: string; cover?: string; category_id?: string };
type Channel = { id: string; name: string; logo?: string; group: string };

export default function Home() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const [profile, setProfile] = useState<Profile | null>(null);
  const [movies, setMovies] = useState<Movie[]>([]);
  const [series, setSeries] = useState<Series[]>([]);
  const [channels, setChannels] = useState<Channel[]>([]);
  const [progress, setProgress] = useState<ProgressItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    try {
      const p = await getActiveProfile();
      setProfile(p);

      const [mv, sr, ch, mvCats, srCats] = await Promise.all([
        api.get("/vod/streams", { params: { limit: 60 } }),
        api.get("/series/list", { params: { limit: 60 } }),
        api.get("/live/channels"),
        api.get("/vod/categories"),
        api.get("/series/categories"),
      ]);

      let mvList: Movie[] = mv.data?.streams || [];
      let srList: Series[] = sr.data?.series || [];
      let chList: Channel[] = ch.data?.channels || [];

      if (p?.isKids) {
        const kidMovieCatIds = new Set(
          (mvCats.data?.categories || [])
            .filter((c: any) => isKidsCategoryName(c.category_name))
            .map((c: any) => String(c.category_id)),
        );
        const kidSeriesCatIds = new Set(
          (srCats.data?.categories || [])
            .filter((c: any) => isKidsCategoryName(c.category_name))
            .map((c: any) => String(c.category_id)),
        );
        mvList = mvList.filter((m) => kidMovieCatIds.has(String(m.category_id)));
        srList = srList.filter((s) => kidSeriesCatIds.has(String(s.category_id)));
        chList = chList.filter((c) => isKidsCategoryName(c.group));
      }

      setMovies(mvList.slice(0, 30));
      setSeries(srList.slice(0, 30));
      setChannels(chList.slice(0, 20));

      if (p) setProgress(await getProgressList(p.id));
    } catch {
      // silent
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load]),
  );

  const featured = movies[0];

  return (
    <View style={styles.root}>
      <ScrollView
        contentContainerStyle={{ paddingBottom: 100 + insets.bottom }}
        showsVerticalScrollIndicator={false}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={() => {
              setRefreshing(true);
              load();
            }}
            tintColor={colors.primary}
          />
        }
      >
        {/* HERO */}
        <View style={{ height: HERO_H }} testID="home-hero">
          <Image
            source={featured?.stream_icon || FALLBACK_POSTER}
            style={StyleSheet.absoluteFill}
            contentFit="cover"
            transition={300}
          />
          <LinearGradient
            colors={["rgba(5,5,5,0)", "rgba(5,5,5,0.4)", "#050505"]}
            style={StyleSheet.absoluteFill}
          />
          <View style={[styles.heroHeader, { paddingTop: insets.top + 12 }]}>
            <Text style={styles.brand}>
              NEX<Text style={{ color: colors.primary }}>US</Text>
              {profile?.isKids ? <Text style={styles.kidsChip}>  · KIDS</Text> : null}
            </Text>
            <TouchableOpacity
              onPress={() => router.push("/(tabs)/profile")}
              testID="home-profile-btn"
            >
              {profile ? (
                <View style={[styles.avatar, { backgroundColor: profile.color }]}>
                  {profile.isKids ? (
                    <Ionicons name="happy" size={18} color="#fff" />
                  ) : (
                    <Text style={styles.avatarInit}>
                      {profile.name.charAt(0).toUpperCase()}
                    </Text>
                  )}
                </View>
              ) : (
                <Ionicons name="person-circle-outline" size={30} color="#fff" />
              )}
            </TouchableOpacity>
          </View>

          <View style={styles.heroBottom}>
            <Text style={styles.heroLabel}>DESTACADO</Text>
            <Text style={styles.heroTitle} numberOfLines={2}>
              {featured?.name || "Explora miles de películas y series"}
            </Text>
            <View style={styles.heroActions}>
              <Pressable
                testID="hero-play-button"
                onPress={() => featured && router.push(`/movie/${featured.stream_id}`)}
                style={({ focused }) => [styles.playBtn, focused && styles.btnFocused]}
              >
                <Ionicons name="play" size={18} color="#000" />
                <Text style={styles.playBtnText}>Reproducir</Text>
              </Pressable>
              <Pressable
                onPress={() => router.push("/(tabs)/movies")}
                style={({ focused }) => [styles.infoBtn, focused && styles.btnFocused]}
              >
                <Ionicons name="information-circle-outline" size={20} color="#fff" />
                <Text style={styles.infoBtnText}>Explorar</Text>
              </Pressable>
            </View>
          </View>
        </View>

        {loading ? (
          <ActivityIndicator color={colors.primary} style={{ marginTop: 30 }} />
        ) : (
          <>
            {progress.length > 0 && (
              <Row title="Continuar viendo" testID="row-continue">
                {progress.map((p) => {
                  const pct = p.durationMs ? Math.min(100, (p.positionMs / p.durationMs) * 100) : 0;
                  return (
                    <Pressable
                      key={p.key}
                      focusable
                      onPress={() =>
                        router.push({
                          pathname: "/player",
                          params: {
                            url: p.streamUrl,
                            title: p.title,
                            type: p.kind === "movie" ? "vod" : "series",
                            resumeKey: p.key,
                            resumeMs: String(p.positionMs),
                          },
                        })
                      }
                      onLongPress={async () => {
                        if (profile) {
                          await removeProgress(profile.id, p.key);
                          setProgress((prev) => prev.filter((x) => x.key !== p.key));
                        }
                      }}
                      testID={`continue-${p.key}`}
                      style={({ focused }) => [styles.continueCard, focused && styles.focusedTile]}
                    >
                      <Image
                        source={p.image || FALLBACK_POSTER}
                        style={styles.continueImg}
                        contentFit="cover"
                      />
                      <View style={styles.continuePlay}>
                        <Ionicons name="play" size={22} color="#fff" />
                      </View>
                      <View style={styles.progressBar}>
                        <View style={[styles.progressFill, { width: `${pct}%` }]} />
                      </View>
                      <Text style={styles.posterTitle} numberOfLines={1}>
                        {p.title}
                      </Text>
                    </Pressable>
                  );
                })}
              </Row>
            )}

            {channels.length > 0 && (
              <Row
                title={profile?.isKids ? "TV Infantil" : "En Vivo Ahora"}
                onSeeAll={() => router.push("/(tabs)/live")}
                testID="row-live"
              >
                {channels.map((c) => (
                  <Pressable
                    key={c.id}
                    focusable
                    onPress={() =>
                      router.push({
                        pathname: "/player",
                        params: { type: "live", id: c.id, title: c.name },
                      })
                    }
                    testID={`live-card-${c.id}`}
                    style={({ focused }) => [styles.liveCard, focused && styles.focusedTile]}
                  >
                    <Image
                      source={c.logo || FALLBACK_POSTER}
                      style={styles.liveImg}
                      contentFit="contain"
                    />
                    <LinearGradient
                      colors={["transparent", "rgba(0,0,0,0.9)"]}
                      style={styles.liveOverlay}
                    />
                    <Text style={styles.liveName} numberOfLines={1}>
                      {c.name}
                    </Text>
                  </Pressable>
                ))}
              </Row>
            )}

            <Row
              title={profile?.isKids ? "Películas para ti" : "Películas Populares"}
              onSeeAll={() => router.push("/(tabs)/movies")}
              testID="row-movies"
            >
              {movies.slice(1).map((m) => (
                <PosterCard
                  key={m.stream_id}
                  title={m.name}
                  image={m.stream_icon}
                  onPress={() => router.push(`/movie/${m.stream_id}`)}
                  testID={`movie-card-${m.stream_id}`}
                />
              ))}
            </Row>

            <Row
              title={profile?.isKids ? "Series divertidas" : "Series Destacadas"}
              onSeeAll={() => router.push("/(tabs)/series")}
              testID="row-series"
            >
              {series.map((s) => (
                <PosterCard
                  key={s.series_id}
                  title={s.name}
                  image={s.cover}
                  onPress={() => router.push(`/series/${s.series_id}`)}
                  testID={`series-card-${s.series_id}`}
                />
              ))}
            </Row>
          </>
        )}
      </ScrollView>
    </View>
  );
}

function Row({
  title,
  children,
  onSeeAll,
  testID,
}: {
  title: string;
  children: React.ReactNode;
  onSeeAll?: () => void;
  testID?: string;
}) {
  return (
    <View style={{ marginTop: 24 }} testID={testID}>
      <View style={styles.rowHeader}>
        <Text style={styles.rowTitle}>{title}</Text>
        {onSeeAll && (
          <TouchableOpacity onPress={onSeeAll}>
            <Text style={styles.seeAll}>Ver todo</Text>
          </TouchableOpacity>
        )}
      </View>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={{ paddingHorizontal: 16, gap: 12 }}
      >
        {children}
      </ScrollView>
    </View>
  );
}

function PosterCard({
  title,
  image,
  onPress,
  testID,
}: {
  title: string;
  image?: string;
  onPress: () => void;
  testID?: string;
}) {
  return (
    <Pressable
      focusable
      onPress={onPress}
      testID={testID}
      style={({ focused }) => [styles.posterCard, focused && styles.focusedTile]}
    >
      <Image
        source={image || FALLBACK_POSTER}
        style={styles.posterImg}
        contentFit="cover"
        transition={200}
      />
      <Text style={styles.posterTitle} numberOfLines={2}>
        {title}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bg },
  brand: { color: "#fff", fontSize: 26, fontWeight: "900", letterSpacing: 3 },
  kidsChip: { fontSize: 12, color: colors.accent, letterSpacing: 2 },
  avatar: {
    width: 34,
    height: 34,
    borderRadius: 8,
    justifyContent: "center",
    alignItems: "center",
  },
  avatarInit: { color: "#fff", fontWeight: "900", fontSize: 14 },
  heroHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingHorizontal: 16,
  },
  heroBottom: {
    position: "absolute",
    bottom: 24,
    left: 0,
    right: 0,
    paddingHorizontal: 16,
    gap: 10,
  },
  heroLabel: { color: colors.primary, fontWeight: "800", fontSize: 11, letterSpacing: 4 },
  heroTitle: { color: "#fff", fontSize: 30, fontWeight: "900", lineHeight: 34 },
  heroActions: { flexDirection: "row", gap: 10, marginTop: 8 },
  playBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    backgroundColor: "#fff",
    paddingHorizontal: 22,
    paddingVertical: 12,
    borderRadius: radius.pill,
  },
  playBtnText: { color: "#000", fontWeight: "800" },
  infoBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    backgroundColor: "rgba(255,255,255,0.15)",
    paddingHorizontal: 22,
    paddingVertical: 12,
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.2)",
  },
  infoBtnText: { color: "#fff", fontWeight: "700" },
  btnFocused: {
    borderWidth: 2,
    borderColor: colors.primary,
    shadowColor: colors.primary,
    shadowOpacity: 0.9,
    shadowRadius: 12,
    elevation: 12,
    transform: [{ scale: 1.05 }],
  },
  rowHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingHorizontal: 16,
    marginBottom: 12,
  },
  rowTitle: { color: "#fff", fontSize: 20, fontWeight: "800" },
  seeAll: { color: colors.textSecondary, fontSize: 12, fontWeight: "600" },
  posterCard: { width: 108, gap: 4, borderRadius: radius.md, borderWidth: 2, borderColor: "transparent" },
  focusedTile: {
    borderColor: colors.primary,
    shadowColor: colors.primary,
    shadowOpacity: 0.9,
    shadowRadius: 14,
    elevation: 14,
    transform: [{ scale: 1.05 }],
  },
  posterImg: {
    width: 108,
    height: 162,
    borderRadius: radius.md,
    backgroundColor: colors.surface,
  },
  posterTitle: { color: "#fff", fontSize: 11, fontWeight: "600" },
  liveCard: {
    width: 200,
    height: 112,
    borderRadius: radius.md,
    backgroundColor: colors.surface,
    overflow: "hidden",
    justifyContent: "flex-end",
    padding: 10,
  },
  liveImg: { ...StyleSheet.absoluteFillObject, opacity: 0.9 },
  liveOverlay: { ...StyleSheet.absoluteFillObject },
  liveName: { color: "#fff", fontWeight: "700", fontSize: 13 },
  continueCard: {
    width: 220,
    gap: 6,
  },
  continueImg: {
    width: 220,
    height: 125,
    borderRadius: radius.md,
    backgroundColor: colors.surface,
  },
  continuePlay: {
    position: "absolute",
    top: 42,
    left: 90,
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: "rgba(0,0,0,0.6)",
    justifyContent: "center",
    alignItems: "center",
  },
  progressBar: {
    height: 3,
    backgroundColor: "rgba(255,255,255,0.2)",
    borderRadius: 2,
    overflow: "hidden",
    marginTop: -6,
    marginHorizontal: 2,
  },
  progressFill: { height: 3, backgroundColor: colors.primary },
});
