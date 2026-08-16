import { useEffect } from "react";
import { View, ActivityIndicator, StyleSheet, Text } from "react-native";
import { useRouter } from "expo-router";
import { getStoredToken } from "@/src/api/client";
import { prefetchAll } from "@/src/api/prefetch";
import { colors } from "@/src/theme";

export default function Index() {
  const router = useRouter();

  useEffect(() => {
    (async () => {
      const token = await getStoredToken();
      if (token) {
        // Warm up backend cache in the background while we navigate
        prefetchAll();
        router.replace("/profile-select");
      } else {
        router.replace("/login");
      }
    })();
  }, [router]);

  return (
    <View style={styles.container} testID="splash-screen">
      <Text style={styles.brand}>NEXUS</Text>
      <ActivityIndicator color={colors.primary} size="large" />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.bg,
    alignItems: "center",
    justifyContent: "center",
    gap: 24,
  },
  brand: {
    color: colors.primary,
    fontSize: 40,
    fontWeight: "900",
    letterSpacing: 6,
  },
});
