import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TouchableOpacity,
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
import { getFavs, toggleFav } from "@/src/state/favorites";

type Channel = { id: string; name: string; logo?: string; group: string };
type Category = { id: string; name: string; count?: number };

const FALLBACK =
  "https://images.unsplash.com/photo-1693328394659-e0782c606d25?crop=entropy&cs=srgb&fm=jpg&ixid=M3w3NTY2NzZ8MHwxfHNlYXJjaHwyfHxsaXZlJTIwc3BvcnRzJTIwYnJvYWRjYXN0fGVufDB8fHx8MTc4NDE3NTc4Nnww&ixlib=rb-4.1.0&q=85";

export default function LiveScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const [profile, setProfile] = useState<Profile | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [channels, setChannels] = useState<Channel[]>([]);
  const [favIds, setFavIds] = useState<Set<string>>(new Set());
  const [active, setActive] = useState<string>("");
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const initialActiveSetRef = useRef(false);

  const loadFavs = useCallback(async (pid?: string) => {
    if (!pid) return;
    const favs = await getFavs("live", pid);
    setFavIds(new Set(favs.map((f) => f.id)));
  }, []);

  const loadCats = useCallback(async (kidsMode: boolean) => {
    try {
      const { data } = await api.get("/live/categories");
      let cats: Category[] = data.categories || [];
      if (kidsMode) cats = cats.filter((c) => isKidsCategoryName(c.name));
      setCategories([
        { id: "ALL", name: "TODOS" },
        { id: "__FAVS__", name: "★ FAVORITOS" },
        ...cats,
      ]);
      // Auto-select TV CHILE on very first load (once per session). If the user
      // already navigated to a category we respect their choice on re-focus.
      if (!initialActiveSetRef.current) {
        initialActiveSetRef.current = true;
        const tvChile = cats.find((c) => c.name.toLowerCase().includes("tv chile"));
        setActive(tvChile ? tvChile.id : "ALL");
      }
    } catch {
      // On error still let the user browse "ALL" so the list is not empty
      if (!initialActiveSetRef.current) {
        initialActiveSetRef.current = true;
        setActive("ALL");
      }
    }
  }, []);

  const loadChannels = useCallback(
    async (cat: string, kidsMode: boolean, favSet: Set<string>) => {
      setLoading(true);
      try {
        const params: any = {};
        if (cat !== "ALL" && cat !== "__FAVS__") params.category = cat;
        const { data } = await api.get("/live/channels", { params });
        let list: Channel[] = data.channels || [];
        if (kidsMode) list = list.filter((c) => isKidsCategoryName(c.group));
        if (cat === "__FAVS__") list = list.filter((c) => favSet.has(c.id));
        setChannels(list);
      } catch {
        setChannels([]);
      } finally {
        setLoading(false);
      }
    },
    [],
  );

  useFocusEffect(
    useCallback(() => {
      (async () => {
        const p = await getActiveProfile();
        setProfile(p);
        await loadCats(!!p?.isKids);
        await loadFavs(p?.id);
      })();
    }, [loadCats, loadFavs]),
  );

  useEffect(() => {
    if (!active) return; // wait until loadCats decides the initial category
    loadChannels(active, !!profile?.isKids, favIds);
  }, [active, profile, favIds, loadChannels]);

  const toggle = async (c: Channel) => {
    if (!profile) return;
    const next = await toggleFav("live", profile.id, {
      id: c.id,
      title: c.name,
      image: c.logo,
    });
    setFavIds((prev) => {
      const n = new Set(prev);
      if (next) n.add(c.id);
      else n.delete(c.id);
      return n;
    });
  };

  const filtered = useMemo(() => {
    if (!search.trim()) return channels;
    const q = search.trim().toLowerCase();
    return channels.filter((c) => c.name.toLowerCase().includes(q));
  }, [channels, search]);

  return (
    <SafeAreaView style={styles.root} edges={["top"]}>
      <View style={styles.header}>
        <Text style={styles.title} testID="live-title">
          En Vivo
        </Text>
        <Text style={styles.count}>{filtered.length} canales</Text>
      </View>

      <View style={styles.searchWrap}>
        <Ionicons name="search" size={16} color={colors.textSecondary} />
        <TextInput
          value={search}
          onChangeText={setSearch}
          placeholder="Buscar canal..."
          placeholderTextColor={colors.textMuted}
          style={styles.searchInput}
          testID="live-search-input"
        />
      </View>

      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={{ paddingHorizontal: 16, gap: 8, paddingBottom: 12 }}
        style={{ maxHeight: 56, flexGrow: 0 }}
      >
        {categories.map((cat) => {
          const isActive = active === cat.id;
          return (
            <Pressable
              key={cat.id}
              focusable
              onPress={() => setActive(cat.id)}
              testID={`live-chip-${cat.id}`}
              style={({ focused }) => [
                styles.chip,
                isActive && styles.chipActive,
                focused && styles.chipFocused,
              ]}
            >
              <Text style={[styles.chipText, isActive && styles.chipTextActive]}>
                {cat.name}
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
          keyExtractor={(i) => i.id}
          contentContainerStyle={{ gap: 8, paddingHorizontal: 12, paddingTop: 4, paddingBottom: 100 + insets.bottom }}
          renderItem={({ item }) => {
            const isF = favIds.has(item.id);
            return (
              <Pressable
                focusable
                onPress={() =>
                  router.push({
                    pathname: "/player",
                    params: {
                      type: "live",
                      id: item.id,
                      title: item.name,
                      category: active === "__FAVS__" ? "ALL" : active,
                    },
                  })
                }
                testID={`live-channel-${item.id}`}
                style={({ focused }) => [
                  styles.card,
                  focused && styles.cardFocused,
                ]}
              >
                <View style={styles.logoWrap}>
                  <Image
                    source={item.logo || FALLBACK}
                    style={styles.logoImg}
                    contentFit="contain"
                    transition={200}
                  />
                </View>
                <View style={styles.cardBody}>
                  <View style={styles.cardHeader}>
                    <View style={styles.liveBadge}>
                      <View style={styles.liveDot} />
                      <Text style={styles.liveBadgeText}>EN VIVO</Text>
                    </View>
                  </View>
                  <Text style={styles.cardTitle} numberOfLines={1}>
                    {item.name}
                  </Text>
                  <Text style={styles.cardGroup} numberOfLines={1}>
                    {item.group}
                  </Text>
                </View>
                <TouchableOpacity
                  onPress={() => toggle(item)}
                  style={styles.heart}
                  hitSlop={8}
                  testID={`live-fav-${item.id}`}
                >
                  <Ionicons
                    name={isF ? "heart" : "heart-outline"}
                    size={20}
                    color={isF ? colors.primary : "#fff"}
                  />
                </TouchableOpacity>
                <Ionicons
                  name="play-circle"
                  size={26}
                  color={colors.primary}
                />
              </Pressable>
            );
          }}
          ListEmptyComponent={
            <Text style={styles.empty}>
              {active === "__FAVS__"
                ? "Todavía no tienes canales favoritos. Toca el corazón en cualquier canal."
                : "No hay canales en esta categoría"}
            </Text>
          }
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
  card: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    borderRadius: radius.md,
    backgroundColor: colors.surface,
    padding: 10,
    borderWidth: 2,
    borderColor: colors.border,
  },
  cardFocused: {
    borderColor: colors.primary,
    backgroundColor: "rgba(229,9,20,0.08)",
    shadowColor: colors.primary,
    shadowOpacity: 0.9,
    shadowRadius: 14,
    elevation: 14,
    transform: [{ scale: 1.02 }],
  },
  logoWrap: {
    width: 60,
    height: 60,
    borderRadius: radius.sm,
    backgroundColor: "#000",
    justifyContent: "center",
    alignItems: "center",
    overflow: "hidden",
  },
  logoImg: { width: 56, height: 56 },
  cardBody: { flex: 1, gap: 3 },
  cardHeader: { flexDirection: "row", alignItems: "center", gap: 6 },
  liveBadge: {
    backgroundColor: colors.primary,
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 3,
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    alignSelf: "flex-start",
  },
  liveDot: { width: 5, height: 5, borderRadius: 3, backgroundColor: "#fff" },
  liveBadgeText: { color: "#fff", fontSize: 9, fontWeight: "900", letterSpacing: 1 },
  heart: { padding: 4 },
  cardTitle: { color: "#fff", fontWeight: "700", fontSize: 14 },
  cardGroup: { color: colors.textSecondary, fontSize: 11 },
  empty: { color: colors.textSecondary, textAlign: "center", marginTop: 40, paddingHorizontal: 24 },
});
