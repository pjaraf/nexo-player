import { useEffect, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ActivityIndicator,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useLocalSearchParams, useRouter } from "expo-router";
import { SafeAreaView } from "react-native-safe-area-context";
import { colors, radius } from "@/src/theme";
import { getPin, setPin as savePin, clearPin } from "@/src/state/profiles";

// action: enter | set | remove
export default function PinScreen() {
  const router = useRouter();
  const { action = "enter", next: nextRoute } = useLocalSearchParams<{
    action?: string;
    next?: string;
  }>();
  const [step, setStep] = useState<"pin" | "confirm">("pin");
  const [pin, setPinValue] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(action === "remove");
  const [existingPin, setExistingPin] = useState<string>("");

  useEffect(() => {
    (async () => {
      const p = await getPin();
      setExistingPin(p);
      if (action === "remove") {
        setLoading(false);
      }
    })();
  }, [action]);

  const currentValue = step === "pin" ? pin : confirm;

  const press = (digit: string) => {
    setError(null);
    if (currentValue.length >= 4) return;
    const next = currentValue + digit;
    if (step === "pin") {
      setPinValue(next);
      if (next.length === 4) handleFilled(next);
    } else {
      setConfirm(next);
      if (next.length === 4) handleConfirmed(next);
    }
  };

  const back = () => {
    setError(null);
    if (step === "pin") setPinValue(pin.slice(0, -1));
    else setConfirm(confirm.slice(0, -1));
  };

  const handleFilled = async (val: string) => {
    if (action === "enter") {
      if (val === existingPin) {
        if (nextRoute) router.replace(nextRoute as any);
        else router.replace("/(tabs)");
      } else {
        setError("PIN incorrecto");
        setTimeout(() => setPinValue(""), 400);
      }
    } else if (action === "set") {
      setStep("confirm");
    } else if (action === "remove") {
      if (val === existingPin) {
        await clearPin();
        router.back();
      } else {
        setError("PIN incorrecto");
        setTimeout(() => setPinValue(""), 400);
      }
    }
  };

  const handleConfirmed = async (val: string) => {
    if (val === pin) {
      await savePin(pin);
      router.back();
    } else {
      setError("Los PIN no coinciden");
      setTimeout(() => {
        setConfirm("");
      }, 400);
    }
  };

  const title =
    action === "set"
      ? step === "pin"
        ? "Crea un PIN de 4 dígitos"
        : "Confirma tu PIN"
      : action === "remove"
      ? "Ingresa tu PIN para eliminarlo"
      : "Ingresa tu PIN";

  if (loading) {
    return (
      <View style={[styles.root, { justifyContent: "center", alignItems: "center" }]}>
        <ActivityIndicator color={colors.primary} />
      </View>
    );
  }

  return (
    <SafeAreaView style={styles.root} edges={["top", "bottom"]} testID="pin-screen">
      <View style={styles.top}>
        <TouchableOpacity onPress={() => router.back()} style={styles.close}>
          <Ionicons name="close" size={22} color="#fff" />
        </TouchableOpacity>
        <Ionicons name="lock-closed" size={40} color={colors.primary} />
        <Text style={styles.title}>{title}</Text>
      </View>

      <View style={styles.dotsRow}>
        {[0, 1, 2, 3].map((i) => (
          <View
            key={i}
            style={[
              styles.dot,
              currentValue.length > i && styles.dotActive,
              error && styles.dotError,
            ]}
          />
        ))}
      </View>

      {error ? <Text style={styles.error}>{error}</Text> : <View style={{ height: 20 }} />}

      <View style={styles.pad}>
        {["1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "back"].map((k, idx) => {
          if (k === "") return <View key={idx} style={styles.key} />;
          if (k === "back") {
            return (
              <TouchableOpacity
                key={idx}
                style={styles.key}
                onPress={back}
                testID="pin-back"
              >
                <Ionicons name="backspace-outline" size={26} color="#fff" />
              </TouchableOpacity>
            );
          }
          return (
            <TouchableOpacity
              key={idx}
              style={styles.key}
              onPress={() => press(k)}
              testID={`pin-key-${k}`}
            >
              <Text style={styles.keyText}>{k}</Text>
            </TouchableOpacity>
          );
        })}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bg },
  top: { alignItems: "center", padding: 24, gap: 14 },
  close: { position: "absolute", top: 12, right: 12, padding: 10 },
  title: { color: "#fff", fontSize: 20, fontWeight: "800", textAlign: "center" },
  dotsRow: { flexDirection: "row", justifyContent: "center", gap: 18, marginTop: 30 },
  dot: {
    width: 16,
    height: 16,
    borderRadius: 8,
    borderWidth: 2,
    borderColor: colors.border,
  },
  dotActive: { backgroundColor: "#fff", borderColor: "#fff" },
  dotError: { borderColor: colors.primary, backgroundColor: colors.primary },
  error: { color: colors.primary, textAlign: "center", marginTop: 10, fontWeight: "700" },
  pad: {
    marginTop: "auto",
    padding: 24,
    flexDirection: "row",
    flexWrap: "wrap",
    justifyContent: "space-between",
    gap: 14,
  },
  key: {
    width: "30%",
    height: 68,
    borderRadius: radius.md,
    backgroundColor: colors.surface,
    justifyContent: "center",
    alignItems: "center",
    borderWidth: 1,
    borderColor: colors.border,
  },
  keyText: { color: "#fff", fontSize: 26, fontWeight: "700" },
});
