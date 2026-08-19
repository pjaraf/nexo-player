with open('app/src/main/java/com/example/ui/screens/PlayerScreen.kt', 'r') as f:
    lines = f.readlines()

import_line = "import com.example.ui.components.TvFullscreenPlayerOverlay\n"
if import_line not in lines:
    lines.insert(20, import_line)

for i, line in enumerate(lines):
    if "Box(" in line and "Controls HUD Overlay" in lines[i-7:i]: # Find the VOD else block
        # We need to replace the Box in the `else` with TvFullscreenPlayerOverlay if it's TV.
        pass

