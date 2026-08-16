import { useCallback, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";
import { SafeAreaView } from "react-native-safe-area-context";
import { storage } from "@/src/utils/storage";
import { clearSession, USER_KEY } from "@/src/api/client";
import { colors, radius } from "@/src/theme";
import { getActiveProfile, getPin, Profile, setActiveProfileId } from "@/src/state/profiles";

type UserInfo = {
  username?: string;
  status?: string;
  exp_date?: string;
  active_cons?: string;
  max_connections?: string;
  created_at?: string;
};

function formatDate(unix?: string) {
  if (!unix) return "-";
  try {
    return new Date(Number(unix) * 1000).toLocaleDateString();
  } catch {
    return "-";
  }
}

export default function ProfileScreen() {
  const router = useRouter();
  const [user, setUser] = useState<UserInfo | null>(null);
  const [profile, setProfile] = useState<Profile | null>(null);
  const [hasPin, setHasPin] = useState(false);

  useFocusEffect(
    useCallback(() => {
      (async () => {
        const u = await storage.getItem<UserInfo>(USER_KEY, {} as UserInfo);
        setUser(u);
        setProfile(await getActiveProfile());
        setHasPin(!!(await getPin()));
      })();
    }, []),
  );

  const handleLogout = async () => {
    await clearSession();
    await setActiveProfileId("");
    router.replace("/login");
  };

  const handleSwitch = async () => {
    // Kids profiles can't switch without PIN
    if (profile?.isKids && hasPin) {
      router.push({ pathname: "/pin", params: { action: "enter", next: "/profile-select" } });
      return;
    }
    router.replace("/profile-select");
  };

  return (
    <SafeAreaView style={styles.root} edges={["top"]}>
      <ScrollView contentContainerStyle={{ padding: 20, paddingBottom: 120 }}>
        <View style={styles.avatarWrap}>
          <View
            style={[
              styles.avatar,
              { backgroundColor: profile?.color || colors.surface, borderColor: profile?.color || colors.primary },
            ]}
          >
            {profile?.isKids ? (
              <Ionicons name="happy" size={44} color="#fff" />
            ) : (
              <Text style={styles.avatarInit}>
                {(profile?.name || user?.username || "U").charAt(0).toUpperCase()}
              </Text>
            )}
          </View>
          <Text style={styles.username} testID="profile-username">
            {profile?.name || user?.username || "Usuario"}
          </Text>
          <View style={styles.badges}>
            {profile?.isKids && (
              <View style={styles.kidsBadge}>
                <Text style={styles.kidsBadgeText}>KIDS</Text>
              </View>
            )}
            <View style={styles.statusBadge}>
              <View
                style={[
                  styles.statusDot,
                  { backgroundColor: user?.status === "Active" ? "#22c55e" : "#f59e0b" },
                ]}
              />
              <Text style={styles.statusText}>{user?.status || "Activo"}</Text>
            </View>
          </View>
        </View>

        <TouchableOpacity
          style={styles.switchBtn}
          onPress={handleSwitch}
          testID="profile-switch"
        >
          <Ionicons name="swap-horizontal" size={18} color="#fff" />
          <Text style={styles.switchText}>Cambiar perfil</Text>
        </TouchableOpacity>

        {!profile?.isKids && (
          <>
            <Text style={styles.section}>Mi contenido</Text>
            <View style={styles.card}>
              <ActionRow
                label="Mis Favoritos"
                icon="heart-outline"
                onPress={() => router.push("/favorites")}
                testID="profile-favorites"
              />
              <ActionRow
                label="Administrar perfiles"
                icon="people-outline"
                onPress={() => router.push("/manage-profiles")}
                testID="profile-manage"
              />
            </View>
          </>
        )}

        <Text style={styles.section}>Suscripción</Text>
        <View style={styles.card}>
          <Row label="Usuario" value={user?.username || "-"} icon="person-outline" />
          <Row label="Vencimiento" value={formatDate(user?.exp_date)} icon="calendar-outline" />
          <Row
            label="Conexiones"
            value={`${user?.active_cons || 0} / ${user?.max_connections || 1}`}
            icon="wifi-outline"
          />
          <Row label="Creado" value={formatDate(user?.created_at)} icon="time-outline" />
        </View>

        <Text style={styles.section}>Ajustes</Text>
        <View style={styles.card}>
          <ActionRow label="Términos y privacidad" icon="document-text-outline" />
          <ActionRow label="Acerca de Nexus" icon="information-circle-outline" />
        </View>

        <TouchableOpacity
          style={styles.logout}
          onPress={handleLogout}
          testID="profile-logout-button"
        >
          <Ionicons name="log-out-outline" size={20} color="#fff" />
          <Text style={styles.logoutText}>Cerrar sesión</Text>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

function Row({ label, value, icon }: { label: string; value: string; icon: any }) {
  return (
    <View style={styles.row}>
      <Ionicons name={icon} size={18} color={colors.textSecondary} />
      <Text style={styles.rowLabel}>{label}</Text>
      <Text style={styles.rowValue}>{value}</Text>
    </View>
  );
}

function ActionRow({
  label,
  icon,
  onPress,
  testID,
}: {
  label: string;
  icon: any;
  onPress?: () => void;
  testID?: string;
}) {
  return (
    <TouchableOpacity style={styles.row} onPress={onPress} testID={testID}>
      <Ionicons name={icon} size={18} color={colors.textSecondary} />
      <Text style={[styles.rowLabel, { flex: 1 }]}>{label}</Text>
      <Ionicons name="chevron-forward" size={16} color={colors.textMuted} />
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bg },
  avatarWrap: { alignItems: "center", marginTop: 20, marginBottom: 20, gap: 10 },
  avatar: {
    width: 96,
    height: 96,
    borderRadius: 20,
    justifyContent: "center",
    alignItems: "center",
    borderWidth: 2,
  },
  avatarInit: { color: "#fff", fontSize: 40, fontWeight: "900" },
  username: { color: "#fff", fontSize: 22, fontWeight: "800" },
  badges: { flexDirection: "row", gap: 6 },
  kidsBadge: {
    backgroundColor: colors.accent,
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: radius.pill,
  },
  kidsBadgeText: { color: "#000", fontSize: 10, fontWeight: "900", letterSpacing: 1 },
  statusBadge: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    paddingHorizontal: 12,
    paddingVertical: 5,
    backgroundColor: colors.surface,
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: colors.border,
  },
  statusDot: { width: 6, height: 6, borderRadius: 3 },
  statusText: { color: colors.textSecondary, fontSize: 11, fontWeight: "700" },
  switchBtn: {
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "center",
    gap: 8,
    minHeight: 46,
    borderRadius: radius.pill,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    marginBottom: 8,
  },
  switchText: { color: "#fff", fontWeight: "700", letterSpacing: 1 },
  card: {
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    marginBottom: 16,
    overflow: "hidden",
  },
  row: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    paddingVertical: 14,
    paddingHorizontal: 16,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  rowLabel: { color: colors.textSecondary, fontSize: 13 },
  rowValue: { color: "#fff", fontSize: 13, marginLeft: "auto", fontWeight: "700" },
  section: {
    color: colors.textSecondary,
    fontSize: 11,
    fontWeight: "800",
    letterSpacing: 3,
    marginBottom: 8,
    marginTop: 8,
  },
  logout: {
    marginTop: 12,
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "center",
    gap: 10,
    minHeight: 52,
    borderRadius: radius.pill,
    backgroundColor: colors.primary,
  },
  logoutText: { color: "#fff", fontWeight: "900", letterSpacing: 2 },
});
