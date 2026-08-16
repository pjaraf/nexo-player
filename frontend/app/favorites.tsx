import { useCallback, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TouchableOpacity,
} from "react-native";
import { Image } from "expo-image";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";
import { SafeAreaView, useSafeAreaInsets } from "react-native-safe-area-context";
import { colors, radius } from "@/src/theme";
import { FavItem, getFavs } from "@/src/state/favorites";
import { getActiveProfileId } from "@/src/state/profiles";

type FavEntry = FavItem & { kind: "movies" | "series" };

const FALLBACK =
  "https://images.pexels.com/photos/12838778/pexels-photo-12838778.jpeg?auto=compress&cs=tinysrgb&dpr=2&h=650&w=940";

export default function Favorites() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const [items, setItems] = useState<FavEntry[]>([]);
  const [tab, setTab] = useState<"all" | "movies" | "series">("all");

  const load = useCallback(async () => {
    const pid = await getActiveProfileId();
    if (!pid) return setItems([]);
    const [mv, sr] = await Promise.all([getFavs("movies", pid), getFavs("series", pid)]);
    const combined: FavEntry[] = [
      ...mv.map((m) => ({ ...m, kind: "movies" as const })),
      ...sr.map((s) => ({ ...s, kind: "series" as const })),
    ].sort((a, b) => b.addedAt - a.addedAt);
    setItems(combined);
  }, []);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load]),
  );

  const filtered = tab === "all" ? items : items.filter((i) => i.kind === tab);

  return (
    <SafeAreaView style={styles.root} edges={["top"]}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} testID="favorites-back">
          <Ionicons name="chevron-back" size={26} color="#fff" />
        </TouchableOpacity>
        <Text style={styles.title}>Mis Favoritos</Text>
        <View style={{ width: 26 }} />
      </View>

      <View style={styles.tabs}>
        {[
          { id: "all", label: "Todos" },
          { id: "movies", label: "Películas" },
          { id: "series", label: "Series" },
        ].map((t) => (
          <TouchableOpacity
            key={t.id}
            onPress={() => setTab(t.id as any)}
            style={[styles.tab, tab === t.id && styles.tabActive]}
            testID={`favorites-tab-${t.id}`}
          >
            <Text style={[styles.tabText, tab === t.id && { color: "#000" }]}>{t.label}</Text>
          </TouchableOpacity>
        ))}
      </View>

      <FlatList
        data={filtered}
        keyExtractor={(i) => `${i.kind}_${i.id}`}
        numColumns={4}
        columnWrapperStyle={{ gap: 8, paddingHorizontal: 12 }}
        contentContainerStyle={{ gap: 12, paddingBottom: 100 + insets.bottom, paddingTop: 8 }}
        renderItem={({ item }) => (
          <TouchableOpacity
            style={styles.card}
            onPress={() => router.push(`/${item.kind === "movies" ? "movie" : "series"}/${item.id}`)}
            testID={`favorite-${item.kind}-${item.id}`}
          >
            <Image
              source={item.image || FALLBACK}
              style={styles.poster}
              contentFit="cover"
              transition={200}
            />
            <View style={styles.kindTag}>
              <Text style={styles.kindTagText}>
                {item.kind === "movies" ? "PELÍCULA" : "SERIE"}
              </Text>
            </View>
            <Text style={styles.name} numberOfLines={2}>
              {item.title}
            </Text>
          </TouchableOpacity>
        )}
        ListEmptyComponent={
          <View style={styles.empty}>
            <Ionicons name="heart-outline" size={44} color={colors.textMuted} />
            <Text style={styles.emptyText}>
              Aún no tienes favoritos. Toca el corazón en cualquier película o serie.
            </Text>
          </View>
        }
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bg },
  header: {
    padding: 16,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  title: { color: "#fff", fontSize: 18, fontWeight: "800" },
  tabs: {
    flexDirection: "row",
    gap: 8,
    paddingHorizontal: 16,
    paddingBottom: 12,
  },
  tab: {
    height: 36,
    paddingHorizontal: 18,
    justifyContent: "center",
    borderRadius: radius.pill,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
  },
  tabActive: { backgroundColor: "#fff", borderColor: "#fff" },
  tabText: { color: colors.textSecondary, fontWeight: "700", fontSize: 12, letterSpacing: 1 },
  card: { flex: 1, gap: 4, maxWidth: "25%" },
  poster: {
    width: "100%",
    aspectRatio: 2 / 3,
    borderRadius: radius.md,
    backgroundColor: colors.surface,
  },
  kindTag: {
    position: "absolute",
    top: 8,
    left: 8,
    backgroundColor: "rgba(0,0,0,0.7)",
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
  },
  kindTagText: { color: "#fff", fontSize: 9, fontWeight: "800", letterSpacing: 1 },
  name: { color: "#fff", fontSize: 12, fontWeight: "600" },
  empty: { alignItems: "center", gap: 14, marginTop: 60, paddingHorizontal: 40 },
  emptyText: { color: colors.textSecondary, textAlign: "center", fontSize: 13 },
});
