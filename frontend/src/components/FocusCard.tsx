import { ReactNode } from "react";
import { Pressable, StyleSheet, View, ViewStyle } from "react-native";
import { colors, radius } from "@/src/theme";

/**
 * FocusCard: wraps interactive elements so they get a bold RED focus ring
 * when navigated with the Android TV / D-pad remote. Falls back to normal
 * touch behaviour on phones (where `focused` never triggers).
 */
export function FocusCard({
  children,
  onPress,
  onLongPress,
  style,
  testID,
  shape = "rect",
}: {
  children: ReactNode | ((focused: boolean) => ReactNode);
  onPress?: () => void;
  onLongPress?: () => void;
  style?: ViewStyle | ViewStyle[];
  testID?: string;
  shape?: "rect" | "pill" | "square";
}) {
  const br =
    shape === "pill" ? radius.pill : shape === "square" ? radius.md : radius.md;

  return (
    <Pressable
      focusable
      onPress={onPress}
      onLongPress={onLongPress}
      testID={testID}
      style={({ focused, pressed }) => [
        styles.base,
        { borderRadius: br },
        style,
        (focused || pressed) && [
          styles.focused,
          { borderRadius: br + 2 },
        ],
      ]}
    >
      {({ focused }) => (
        <View style={{ flex: 1 }}>
          {typeof children === "function" ? children(focused) : children}
        </View>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    borderWidth: 2,
    borderColor: "transparent",
  },
  focused: {
    borderColor: colors.primary,
    shadowColor: colors.primary,
    shadowOpacity: 0.9,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 0 },
    elevation: 12,
    transform: [{ scale: 1.04 }],
  },
});
