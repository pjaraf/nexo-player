import { useEffect, useState } from "react";
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
  "https://images.pexels.com/photos/12838778/pexels-photo-12838778.jpeg?auto=compress&cs=tinysrgb&dpr=2&h=650&w=940";

export default function MovieDetail() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const [info, setInfo] = useState<any>(null);
  const [movieData, setMovieData] = useState<any>(null);
  const [streamUrl, setStreamUrl] = useState<string>("");
  const [loading, setLoading] = useState(true);
  const [fav, setFav] = useState(false);
  const [profileId, setProfileId] = useState<string>("");

  useEffect(() => {
    (async () => {
      try {
        const pid = await getActiveProfileId();
        setProfileId(pid);
        const { data } = await api.get(`/vod/info/${id}`);
        setInfo(data.info || {});
        setMovieData(data.movie_data || {});
        setStreamUrl(absoluteUrl(data.stream_url || ""));
        if (pid && id) setFav(await isFav("movies", pid, String(id)));
      } catch {
      } finally {
        setLoading(false);
      }
    })();
  }, [id]);

  const title = movieData?.name || info?.name || "Película";
  const cover = info?.movie_image || info?.backdrop_path?.[0] || movieData?.stream_icon;
  const desc = info?.plot || info?.description || "Sin descripción disponible.";

  const onFav = async () => {
    if (!profileId || !id) return;
    const next = await toggleFav("movies", profileId, {
      id: String(id),
      title,
      image: cover,
    });
    setFav(next);
  };

  if (loading) {
    return (
      <View style={[styles.root, { justifyContent: "center", alignItems: "center" }]}>
        <ActivityIndicator color={colors.primary} />
      </View>
    );
  }

  return (
    <View style={styles.root}>
      <ScrollView contentContainerStyle={{ paddingBottom: 60 }} showsVerticalScrollIndicator={false}>
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
              testID="movie-back-button"
            >
              <Ionicons name="chevron-back" size={22} color="#fff" />
            </TouchableOpacity>
            <TouchableOpacity
              onPress={onFav}
              style={styles.backBtn}
              testID="movie-fav-button"
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
          <Text style={styles.title} testID="movie-title">
            {title}
          </Text>
          <View style={styles.metaRow}>
            {info?.releasedate ? <Meta text={String(info.releasedate).slice(0, 4)} /> : null}
            {info?.rating ? (
              <Meta text={`★ ${info.rating}`} color="#FFD700" />
            ) : null}
            {info?.duration ? <Meta text={info.duration} /> : null}
            {info?.genre ? <Meta text={String(info.genre).split(",")[0]} /> : null}
          </View>

          <Pressable
            testID="movie-play-button"
            onPress={() =>
              router.push({
                pathname: "/player",
                params: {
                  url: streamUrl,
                  title,
                  type: "vod",
                  kind: "movie",
                  contentId: String(id),
                  image: cover || "",
                },
              })
            }
            style={({ focused }) => [styles.playBtn, focused && styles.playBtnFocused]}
          >
            <Ionicons name="play" size={20} color="#000" />
            <Text style={styles.playText}>Reproducir</Text>
          </Pressable>

          <Text style={styles.section}>Sinopsis</Text>
          <Text style={styles.desc}>{desc}</Text>

          {info?.cast ? (
            <>
              <Text style={styles.section}>Reparto</Text>
              <Text style={styles.desc}>{info.cast}</Text>
            </>
          ) : null}

          {info?.director ? (
            <>
              <Text style={styles.section}>Director</Text>
              <Text style={styles.desc}>{info.director}</Text>
            </>
          ) : null}
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
  heroWrap: { height: 480, width: "100%" },
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
  title: { color: "#fff", fontSize: 30, fontWeight: "900", letterSpacing: -1 },
  metaRow: { flexDirection: "row", gap: 8, flexWrap: "wrap", marginTop: 10 },
  metaChip: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    backgroundColor: "rgba(255,255,255,0.08)",
    borderRadius: radius.sm,
  },
  metaText: { color: colors.textSecondary, fontSize: 11, fontWeight: "700" },
  playBtn: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    backgroundColor: "#fff",
    borderRadius: radius.pill,
    minHeight: 52,
    marginTop: 20,
    borderWidth: 2,
    borderColor: "transparent",
  },
  playBtnFocused: {
    borderColor: colors.primary,
    shadowColor: colors.primary,
    shadowOpacity: 0.9,
    shadowRadius: 14,
    elevation: 14,
    transform: [{ scale: 1.04 }],
  },
  playText: { color: "#000", fontWeight: "900", letterSpacing: 2 },
  section: {
    color: colors.textSecondary,
    fontSize: 11,
    fontWeight: "800",
    letterSpacing: 3,
    marginTop: 24,
    marginBottom: 8,
  },
  desc: { color: "#fff", fontSize: 14, lineHeight: 22 },
});
