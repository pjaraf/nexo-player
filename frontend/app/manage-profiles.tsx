import { useCallback, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  TextInput,
  Modal,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";
import { SafeAreaView } from "react-native-safe-area-context";
import { colors, radius } from "@/src/theme";
import {
  AVATAR_COLORS,
  Profile,
  addProfile,
  deleteProfile,
  getPin,
  getProfiles,
  updateProfile,
} from "@/src/state/profiles";

export default function ManageProfiles() {
  const router = useRouter();
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [pinSet, setPinSet] = useState(false);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<Profile | null>(null);

  const load = useCallback(async () => {
    setProfiles(await getProfiles());
    setPinSet(!!(await getPin()));
  }, []);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load]),
  );

  const openNew = () => {
    setEditing({
      id: "",
      name: "",
      color: AVATAR_COLORS[Math.floor(Math.random() * AVATAR_COLORS.length)],
      isKids: false,
    });
    setEditorOpen(true);
  };

  const openEdit = (p: Profile) => {
    setEditing({ ...p });
    setEditorOpen(true);
  };

  const save = async () => {
    if (!editing) return;
    if (!editing.name.trim()) return;
    if (editing.id) {
      await updateProfile(editing.id, editing);
    } else {
      await addProfile(editing);
    }
    setEditorOpen(false);
    setEditing(null);
    load();
  };

  const remove = async (id: string) => {
    if (profiles.length <= 1) return; // never delete last one
    await deleteProfile(id);
    load();
  };

  return (
    <SafeAreaView style={styles.root} edges={["top", "bottom"]}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} testID="manage-back">
          <Ionicons name="chevron-back" size={26} color="#fff" />
        </TouchableOpacity>
        <Text style={styles.title}>Administrar perfiles</Text>
        <View style={{ width: 26 }} />
      </View>

      <ScrollView contentContainerStyle={{ padding: 20, gap: 12 }}>
        {profiles.map((p) => (
          <View key={p.id} style={styles.row} testID={`manage-profile-${p.id}`}>
            <View style={[styles.avatar, { backgroundColor: p.color }]}>
              {p.isKids ? (
                <Ionicons name="happy" size={22} color="#fff" />
              ) : (
                <Text style={styles.avatarInitial}>
                  {p.name.charAt(0).toUpperCase()}
                </Text>
              )}
            </View>
            <View style={{ flex: 1 }}>
              <Text style={styles.name}>{p.name}</Text>
              <Text style={styles.badge}>{p.isKids ? "Kids" : "Estándar"}</Text>
            </View>
            <TouchableOpacity onPress={() => openEdit(p)} style={styles.iconBtn}>
              <Ionicons name="create-outline" size={20} color="#fff" />
            </TouchableOpacity>
            {profiles.length > 1 && (
              <TouchableOpacity onPress={() => remove(p.id)} style={styles.iconBtn}>
                <Ionicons name="trash-outline" size={20} color={colors.primary} />
              </TouchableOpacity>
            )}
          </View>
        ))}

        <TouchableOpacity style={styles.addBtn} onPress={openNew} testID="manage-add">
          <Ionicons name="add" size={22} color="#fff" />
          <Text style={styles.addText}>Añadir perfil</Text>
        </TouchableOpacity>

        <Text style={styles.section}>PIN de adultos</Text>
        <View style={styles.card}>
          <View style={{ flex: 1 }}>
            <Text style={styles.name}>
              {pinSet ? "PIN configurado" : "Sin PIN"}
            </Text>
            <Text style={styles.hint}>
              Los perfiles no-Kids requerirán este PIN para entrar.
            </Text>
          </View>
          <TouchableOpacity
            style={styles.pinBtn}
            onPress={() =>
              router.push({
                pathname: "/pin",
                params: { action: pinSet ? "remove" : "set" },
              })
            }
            testID="manage-pin"
          >
            <Text style={styles.pinBtnText}>{pinSet ? "Eliminar" : "Crear PIN"}</Text>
          </TouchableOpacity>
        </View>
      </ScrollView>

      <Modal visible={editorOpen} transparent animationType="slide">
        <View style={styles.modalRoot}>
          <View style={styles.modalCard}>
            <View style={styles.modalHeader}>
              <Text style={styles.modalTitle}>
                {editing?.id ? "Editar perfil" : "Nuevo perfil"}
              </Text>
              <TouchableOpacity onPress={() => setEditorOpen(false)}>
                <Ionicons name="close" size={22} color="#fff" />
              </TouchableOpacity>
            </View>

            <Text style={styles.label}>Nombre</Text>
            <TextInput
              value={editing?.name || ""}
              onChangeText={(v) => editing && setEditing({ ...editing, name: v })}
              placeholder="Ej. Juan"
              placeholderTextColor={colors.textMuted}
              style={styles.input}
              testID="profile-editor-name"
            />

            <Text style={styles.label}>Color</Text>
            <View style={styles.colorRow}>
              {AVATAR_COLORS.map((c) => (
                <TouchableOpacity
                  key={c}
                  onPress={() => editing && setEditing({ ...editing, color: c })}
                  style={[
                    styles.colorDot,
                    { backgroundColor: c },
                    editing?.color === c && styles.colorDotActive,
                  ]}
                />
              ))}
            </View>

            <TouchableOpacity
              onPress={() => editing && setEditing({ ...editing, isKids: !editing.isKids })}
              style={styles.toggleRow}
              testID="profile-editor-kids"
            >
              <View
                style={[
                  styles.toggleBox,
                  editing?.isKids && { backgroundColor: colors.primary, borderColor: colors.primary },
                ]}
              >
                {editing?.isKids ? <Ionicons name="checkmark" size={16} color="#fff" /> : null}
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.name}>Perfil para niños</Text>
                <Text style={styles.hint}>Solo mostrará contenido infantil</Text>
              </View>
            </TouchableOpacity>

            <TouchableOpacity style={styles.saveBtn} onPress={save} testID="profile-editor-save">
              <Text style={styles.saveText}>Guardar</Text>
            </TouchableOpacity>
          </View>
        </View>
      </Modal>
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
  row: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    backgroundColor: colors.surface,
    borderRadius: radius.md,
    padding: 12,
    borderWidth: 1,
    borderColor: colors.border,
  },
  avatar: {
    width: 46,
    height: 46,
    borderRadius: 12,
    justifyContent: "center",
    alignItems: "center",
  },
  avatarInitial: { color: "#fff", fontWeight: "900", fontSize: 20 },
  name: { color: "#fff", fontWeight: "700", fontSize: 14 },
  badge: { color: colors.textSecondary, fontSize: 11 },
  iconBtn: { padding: 8 },
  addBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    justifyContent: "center",
    padding: 14,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    borderStyle: "dashed",
  },
  addText: { color: "#fff", fontWeight: "700" },
  section: {
    color: colors.textSecondary,
    fontSize: 11,
    fontWeight: "800",
    letterSpacing: 3,
    marginTop: 20,
  },
  card: {
    flexDirection: "row",
    alignItems: "center",
    padding: 14,
    backgroundColor: colors.surface,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    gap: 10,
  },
  hint: { color: colors.textSecondary, fontSize: 11, marginTop: 2 },
  pinBtn: {
    backgroundColor: colors.primary,
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: radius.pill,
  },
  pinBtnText: { color: "#fff", fontWeight: "800", fontSize: 12 },

  // Modal
  modalRoot: { flex: 1, backgroundColor: "rgba(0,0,0,0.7)", justifyContent: "flex-end" },
  modalCard: {
    backgroundColor: colors.surface,
    padding: 20,
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    gap: 12,
  },
  modalHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  modalTitle: { color: "#fff", fontSize: 18, fontWeight: "800" },
  label: { color: colors.textSecondary, fontSize: 11, letterSpacing: 2, marginTop: 6 },
  input: {
    backgroundColor: "rgba(0,0,0,0.4)",
    borderWidth: 1,
    borderColor: colors.border,
    color: "#fff",
    borderRadius: radius.md,
    paddingHorizontal: 14,
    paddingVertical: 12,
  },
  colorRow: { flexDirection: "row", gap: 10, flexWrap: "wrap" },
  colorDot: {
    width: 36,
    height: 36,
    borderRadius: 18,
    borderWidth: 2,
    borderColor: "transparent",
  },
  colorDotActive: { borderColor: "#fff" },
  toggleRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    marginTop: 10,
    padding: 12,
    backgroundColor: "rgba(0,0,0,0.3)",
    borderRadius: radius.md,
  },
  toggleBox: {
    width: 24,
    height: 24,
    borderRadius: 6,
    borderWidth: 2,
    borderColor: colors.border,
    justifyContent: "center",
    alignItems: "center",
  },
  saveBtn: {
    marginTop: 8,
    backgroundColor: colors.primary,
    borderRadius: radius.pill,
    minHeight: 50,
    justifyContent: "center",
    alignItems: "center",
  },
  saveText: { color: "#fff", fontWeight: "900", letterSpacing: 3 },
});
