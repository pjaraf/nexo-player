import { useCallback, useEffect, useMemo, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  ActivityIndicator,
  TextInput,
  ScrollView,
  Pressable,
} from "react-native";
import { Image } from "expo-image";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";
import { SafeAreaView, useSafeAreaInsets } from "react-native-safe-area-context";
import { api } from "@/src/api/client";
import { colors, radius } from "@/src/theme";
import { getActiveProfile, isKidsCategoryName, Profile } from "@/src/state/profiles";

type Movie = {
  stream_id: number;
  name: string;
  stream_icon?: string;
  rating?: string | number;
  category_id?: string;
};
type Category = { category_id: string; category_name: string };

const FALLBACK =
  "https://images.pexels.com/photos/12838778/pexels-photo-12838778.jpeg?auto=compress&cs=tinysrgb&dpr=2&h=650&w=940";

export default function MoviesScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const [profile, setProfile] = useState<Profile | null>(null);
  const [ready, setReady] = useState(false);
  const [cats, setCats] = useState<Category[]>([]);
  const [movies, setMovies] = useState<Movie[]>([]);
  const [active, setActive] = useState<string>("ALL");
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");

  const loadCats = useCallback(async (kidsMode: boolean) => {
    try {
      const { data } = await api.get("/vod/categories");
      let list: Category[] = data.categories || [];
      if (kidsMode) list = list.filter((c) => isKidsCategoryName(c.category_name));
      setCats(list);
    } catch {}
  }, []);

  const loadMovies = useCallback(async (cat: string, kidsMode: boolean, kidsCatIds: Set<string>) => {
    setLoading(true);
    try {
      const { data } = await api.get("/vod/streams", {
        params: cat === "ALL" ? {} : { category_id: cat },
      });
      let list: Movie[] = data.streams || [];
      if (kidsMode && cat === "ALL") {
        list = list.filter((m) => kidsCatIds.has(String(m.category_id)));
      }
      setMovies(list);
    } catch {
      setMovies([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      (async () => {
        setReady(false);
        const p = await getActiveProfile();
        setProfile(p);
        await loadCats(!!p?.isKids);
        setReady(true);
      })();
    }, [loadCats]),
  );

  useEffect(() => {
    if (!ready) return;
    const ids = new Set(cats.map((c) => String(c.category_id)));
    loadMovies(active, !!profile?.isKids, ids);
  }, [active, profile, cats, loadMovies, ready]);

  const filtered = useMemo(() => {
    if (!search.trim()) return movies;
    const q = search.trim().toLowerCase();
    return movies.filter((m) => m.name.toLowerCase().includes(q));
  }, [movies, search]);

  return (
    <SafeAreaView style={styles.root} edges={["top"]}>
      <View style={styles.header}>
        <Text style={styles.title} testID="movies-title">
          Películas
        </Text>
        <Text style={styles.count}>{filtered.length} títulos</Text>
      </View>

      <View style={styles.searchWrap}>
        <Ionicons name="search" size={16} color={colors.textSecondary} />
        <TextInput
          value={search}
          onChangeText={setSearch}
          placeholder="Buscar película..."
          placeholderTextColor={colors.textMuted}
          style={styles.searchInput}
          testID="movies-search-input"
        />
      </View>

      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={{ paddingHorizontal: 16, gap: 8, paddingBottom: 12 }}
        style={{ maxHeight: 56, flexGrow: 0 }}
      >
        {[{ category_id: "ALL", category_name: "TODAS" }, ...cats].map((c) => {
          const isActive = active === c.category_id;
          return (
            <Pressable
              key={c.category_id}
              focusable
              onPress={() => setActive(c.category_id)}
              testID={`movies-chip-${c.category_id}`}
              style={({ focused }) => [
                styles.chip,
                isActive && styles.chipActive,
                focused && styles.chipFocused,
              ]}
            >
              <Text style={[styles.chipText, isActive && styles.chipTextActive]}>
                {c.category_name}
              </Text>
            </Pressable>
          );
        })}
      </ScrollView>

      {loading ? (
        <ActivityIndicator color={colors.primary} style={{ marginTop: 40 }} />
      ) : (
        <FlatList
          data={filtered}
          keyExtractor={(i) => String(i.stream_id)}
          numColumns={4}
          columnWrapperStyle={{ gap: 8, paddingHorizontal: 12 }}
          contentContainerStyle={{ gap: 12, paddingTop: 8, paddingBottom: 100 + insets.bottom }}
          renderItem={({ item }) => (
            <Pressable
              focusable
              onPress={() => router.push(`/movie/${item.stream_id}`)}
              testID={`movie-item-${item.stream_id}`}
              style={({ focused }) => [
                styles.card,
                focused && styles.cardFocused,
              ]}
            >
              <Image
                source={item.stream_icon || FALLBACK}
                style={styles.poster}
                contentFit="cover"
                transition={200}
              />
              <Text style={styles.name} numberOfLines={2}>
                {item.name}
              </Text>
              {item.rating ? (
                <View style={styles.ratingRow}>
                  <Ionicons name="star" size={10} color="#FFD700" />
                  <Text style={styles.rating}>{item.rating}</Text>
                </View>
              ) : null}
            </Pressable>
          )}
          ListEmptyComponent={<Text style={styles.empty}>No hay películas</Text>}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bg },
  header: {
    paddingHorizontal: 16,
    paddingTop: 8,
    paddingBottom: 8,
    flexDirection: "row",
    alignItems: "flex-end",
    justifyContent: "space-between",
  },
  title: { color: "#fff", fontSize: 34, fontWeight: "900", letterSpacing: -1 },
  count: { color: colors.textSecondary, fontSize: 12, marginBottom: 6 },
  searchWrap: {
    marginHorizontal: 16,
    marginBottom: 12,
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    paddingHorizontal: 14,
    backgroundColor: colors.surface,
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: colors.border,
  },
  searchInput: { flex: 1, color: "#fff", paddingVertical: 12, fontSize: 14 },
  chip: {
    height: 36,
    paddingHorizontal: 16,
    borderRadius: radius.pill,
    backgroundColor: colors.surface,
    justifyContent: "center",
    borderWidth: 1,
    borderColor: colors.border,
    flexShrink: 0,
  },
  chipActive: { backgroundColor: "#fff", borderColor: "#fff" },
  chipFocused: {
    borderColor: colors.primary,
    borderWidth: 2,
    shadowColor: colors.primary,
    shadowOpacity: 0.9,
    shadowRadius: 10,
    elevation: 10,
    transform: [{ scale: 1.06 }],
  },
  chipText: { color: colors.textSecondary, fontWeight: "700", fontSize: 12, letterSpacing: 1 },
  chipTextActive: { color: "#000" },
  card: { flex: 1, gap: 4, maxWidth: "25%", borderRadius: radius.md, borderWidth: 2, borderColor: "transparent" },
  cardFocused: {
    borderColor: colors.primary,
    shadowColor: colors.primary,
    shadowOpacity: 0.9,
    shadowRadius: 12,
    elevation: 12,
    transform: [{ scale: 1.05 }],
  },
  poster: {
    width: "100%",
    aspectRatio: 2 / 3,
    borderRadius: radius.md,
    backgroundColor: colors.surface,
  },
  name: { color: "#fff", fontSize: 11, fontWeight: "600" },
  ratingRow: { flexDirection: "row", alignItems: "center", gap: 3 },
  rating: { color: colors.textSecondary, fontSize: 10 },
  empty: { color: colors.textSecondary, textAlign: "center", marginTop: 40 },
});
