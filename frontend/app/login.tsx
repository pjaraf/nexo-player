import { useState } from "react";
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  Pressable,
} from "react-native";
import { Image } from "expo-image";
import { LinearGradient } from "expo-linear-gradient";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import { api, saveSession } from "@/src/api/client";
import { prefetchAll } from "@/src/api/prefetch";
import { colors, radius } from "@/src/theme";

const HERO_IMG =
  "https://images.unsplash.com/photo-1614020661498-fef5b2293108?crop=entropy&cs=srgb&fm=jpg&ixid=M3w4NjY2NjV8MHwxfHNlYXJjaHwyfHxkYXJrJTIwc3RyZWFtaW5nJTIwaW50ZXJmYWNlfGVufDB8fHx8MTc4NDE3NTc4Nnww&ixlib=rb-4.1.0&q=85";

export default function Login() {
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPass, setShowPass] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleLogin = async () => {
    if (!username.trim() || !password.trim()) {
      setError("Ingresa usuario y clave");
      return;
    }
    setError(null);
    setLoading(true);
    try {
      const { data } = await api.post("/auth/login", {
        username: username.trim(),
        password: password.trim(),
      });
      await saveSession(data.token, data.user);
      // Fire-and-forget: warm up the backend cache so every tab loads instantly
      prefetchAll();
      router.replace("/profile-select");
    } catch (e: any) {
      const msg =
        e?.response?.data?.detail ||
        (e?.code === "ECONNABORTED" ? "Tiempo de espera agotado" : "Error de conexión");
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <View style={styles.root} testID="login-screen">
      <Image source={HERO_IMG} style={StyleSheet.absoluteFill} contentFit="cover" blurRadius={20} />
      <LinearGradient
        colors={["rgba(5,5,5,0.4)", "rgba(5,5,5,0.85)", "#050505"]}
        style={StyleSheet.absoluteFill}
      />
      <SafeAreaView style={{ flex: 1 }} edges={["top", "bottom"]}>
        <KeyboardAvoidingView
          style={{ flex: 1 }}
          behavior={Platform.OS === "ios" ? "padding" : "height"}
        >
          <ScrollView
            contentContainerStyle={styles.scroll}
            keyboardShouldPersistTaps="handled"
            showsVerticalScrollIndicator={false}
          >
            <View style={styles.brandBlock}>
              <Text style={styles.brand} testID="login-brand">
                NEX<Text style={{ color: colors.primary }}>US</Text>
              </Text>
              <Text style={styles.tagline}>Tu TV en vivo, películas y series</Text>
            </View>

            <View style={styles.card} testID="login-card">
              <Text style={styles.cardTitle}>Iniciar sesión</Text>
              <Text style={styles.cardSubtitle}>Usa las credenciales de tu proveedor</Text>

              <View style={styles.inputWrap}>
                <Ionicons name="person-outline" size={18} color={colors.textSecondary} />
                <TextInput
                  testID="login-username-input"
                  value={username}
                  onChangeText={setUsername}
                  placeholder="Usuario"
                  placeholderTextColor={colors.textMuted}
                  style={styles.input}
                  autoCapitalize="none"
                  autoCorrect={false}
                />
              </View>

              <View style={styles.inputWrap}>
                <Ionicons name="lock-closed-outline" size={18} color={colors.textSecondary} />
                <TextInput
                  testID="login-password-input"
                  value={password}
                  onChangeText={setPassword}
                  placeholder="Clave"
                  placeholderTextColor={colors.textMuted}
                  style={styles.input}
                  secureTextEntry={!showPass}
                  autoCapitalize="none"
                  autoCorrect={false}
                />
                <TouchableOpacity
                  onPress={() => setShowPass((v) => !v)}
                  testID="login-toggle-password"
                  hitSlop={8}
                >
                  <Ionicons
                    name={showPass ? "eye-off-outline" : "eye-outline"}
                    size={18}
                    color={colors.textSecondary}
                  />
                </TouchableOpacity>
              </View>

              {error ? (
                <Text style={styles.error} testID="login-error">
                  {error}
                </Text>
              ) : null}

              <Pressable
                testID="login-submit-button"
                onPress={handleLogin}
                disabled={loading}
                style={({ focused, pressed }) => [
                  styles.button,
                  (loading || pressed) && { opacity: 0.7 },
                  focused && styles.buttonFocused,
                ]}
              >
                {loading ? (
                  <ActivityIndicator color="#fff" />
                ) : (
                  <Text style={styles.buttonText}>ENTRAR</Text>
                )}
              </Pressable>

              <Text style={styles.hint}>
                Al ingresar aceptas usar tu suscripción de forma responsable.
              </Text>
            </View>
          </ScrollView>
        </KeyboardAvoidingView>
      </SafeAreaView>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bg },
  scroll: { flexGrow: 1, justifyContent: "center", padding: 24, gap: 24 },
  brandBlock: { alignItems: "center", gap: 6, marginBottom: 8 },
  brand: {
    color: "#fff",
    fontSize: 48,
    fontWeight: "900",
    letterSpacing: 4,
  },
  tagline: { color: colors.textSecondary, fontSize: 14, letterSpacing: 2 },
  card: {
    backgroundColor: "rgba(20,20,30,0.75)",
    borderRadius: radius.xl,
    padding: 24,
    borderWidth: 1,
    borderColor: colors.border,
    gap: 14,
  },
  cardTitle: { color: "#fff", fontSize: 24, fontWeight: "800" },
  cardSubtitle: { color: colors.textSecondary, marginBottom: 8 },
  inputWrap: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: "rgba(0,0,0,0.4)",
    borderRadius: radius.md,
    paddingHorizontal: 14,
    minHeight: 52,
  },
  input: { flex: 1, color: "#fff", fontSize: 16, paddingVertical: 12 },
  button: {
    backgroundColor: colors.primary,
    borderRadius: radius.pill,
    minHeight: 52,
    alignItems: "center",
    justifyContent: "center",
    marginTop: 6,
    borderWidth: 2,
    borderColor: "transparent",
  },
  buttonFocused: {
    borderColor: "#fff",
    shadowColor: colors.primary,
    shadowOpacity: 0.9,
    shadowRadius: 14,
    elevation: 14,
    transform: [{ scale: 1.04 }],
  },
  buttonText: { color: "#fff", fontWeight: "900", letterSpacing: 3, fontSize: 15 },
  error: { color: "#ff6b6b", textAlign: "center", fontSize: 13 },
  hint: { color: colors.textMuted, fontSize: 11, textAlign: "center", marginTop: 6 },
});
