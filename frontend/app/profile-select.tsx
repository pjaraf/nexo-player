import { useCallback, useEffect, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  ActivityIndicator,
  Pressable,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";
import { SafeAreaView } from "react-native-safe-area-context";
import { colors, radius } from "@/src/theme";
import {
  Profile,
  ensureDefaultProfile,
  getProfiles,
  getPin,
  setActiveProfileId,
} from "@/src/state/profiles";
import { storage } from "@/src/utils/storage";
import { USER_KEY } from "@/src/api/client";

export default function ProfileSelect() {
  const router = useRouter();
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    const user = await storage.getItem<{ username?: string }>(USER_KEY, {} as any);
    const list = await ensureDefaultProfile(user?.username || "Principal");
    setProfiles(await getProfiles().then((x) => (x.length ? x : list)));
    setLoading(false);
  }, []);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load]),
  );

  useEffect(() => {
    load();
  }, [load]);

  const handlePick = async (p: Profile) => {
    await setActiveProfileId(p.id);
    if (p.isKids) {
      router.replace("/(tabs)");
      return;
    }
    const pin = await getPin();
    if (pin) {
      router.replace({ pathname: "/pin", params: { action: "enter" } });
    } else {
      router.replace("/(tabs)");
    }
  };

  return (
    <SafeAreaView style={styles.root} edges={["top", "bottom"]} testID="profile-select-screen">
      <View style={{ paddingHorizontal: 24, paddingTop: 20, alignItems: "center" }}>
        <Text style={styles.brand}>
          NEX<Text style={{ color: colors.primary }}>US</Text>
        </Text>
        <Text style={styles.title}>¿Quién está viendo?</Text>
      </View>

      {loading ? (
        <ActivityIndicator color={colors.primary} style={{ marginTop: 40 }} />
      ) : (
        <ScrollView
          contentContainerStyle={styles.grid}
          showsVerticalScrollIndicator={false}
        >
          {profiles.map((p) => (
            <Pressable
              key={p.id}
              focusable
              onPress={() => handlePick(p)}
              testID={`profile-item-${p.id}`}
              style={({ focused }) => [styles.item, focused && styles.itemFocused]}
            >
              <View style={[styles.avatar, { backgroundColor: p.color }]}>
                {p.isKids ? (
                  <Ionicons name="happy" size={40} color="#fff" />
                ) : (
                  <Text style={styles.avatarInitial}>
                    {p.name.charAt(0).toUpperCase()}
                  </Text>
                )}
                {p.isKids && (
                  <View style={styles.kidsBadge}>
                    <Text style={styles.kidsBadgeText}>KIDS</Text>
                  </View>
                )}
              </View>
              <Text style={styles.name} numberOfLines={1}>
                {p.name}
              </Text>
            </Pressable>
          ))}

          <Pressable
            focusable
            onPress={() => router.push("/manage-profiles")}
            testID="profile-add"
            style={({ focused }) => [styles.item, focused && styles.itemFocused]}
          >
            <View style={[styles.avatar, styles.avatarAdd]}>
              <Ionicons name="add" size={40} color={colors.textSecondary} />
            </View>
            <Text style={styles.name}>Añadir</Text>
          </Pressable>
        </ScrollView>
      )}

      <TouchableOpacity
        style={styles.manageBtn}
        onPress={() => router.push("/manage-profiles")}
        testID="profile-manage"
      >
        <Ionicons name="settings-outline" size={16} color={colors.textSecondary} />
        <Text style={styles.manageText}>Administrar perfiles</Text>
      </TouchableOpacity>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bg },
  brand: { color: "#fff", fontSize: 32, fontWeight: "900", letterSpacing: 3, marginBottom: 30 },
  title: { color: "#fff", fontSize: 24, fontWeight: "800", marginBottom: 30 },
  grid: {
    flexDirection: "row",
    flexWrap: "wrap",
    justifyContent: "center",
    gap: 24,
    padding: 24,
  },
  item: { width: 110, alignItems: "center", gap: 10, padding: 6, borderRadius: 20, borderWidth: 2, borderColor: "transparent" },
  itemFocused: {
    borderColor: colors.primary,
    shadowColor: colors.primary,
    shadowOpacity: 0.9,
    shadowRadius: 14,
    elevation: 14,
    transform: [{ scale: 1.06 }],
  },
  avatar: {
    width: 100,
    height: 100,
    borderRadius: 20,
    justifyContent: "center",
    alignItems: "center",
    borderWidth: 2,
    borderColor: "transparent",
  },
  avatarAdd: {
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderStyle: "dashed",
  },
  avatarInitial: { color: "#fff", fontSize: 44, fontWeight: "900" },
  kidsBadge: {
    position: "absolute",
    bottom: -6,
    backgroundColor: "#fff",
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 999,
  },
  kidsBadgeText: { color: "#000", fontSize: 9, fontWeight: "900", letterSpacing: 1 },
  name: { color: "#fff", fontWeight: "700", fontSize: 14 },
  manageBtn: {
    alignSelf: "center",
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: radius.pill,
    backgroundColor: colors.surface,
    marginBottom: 20,
  },
  manageText: { color: colors.textSecondary, fontSize: 12, fontWeight: "700" },
});
