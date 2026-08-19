import re

with open('app/src/main/java/com/example/ui/screens/LoginScreen.kt', 'r') as f:
    content = f.read()

# Add import for CinematicBackground
content = content.replace("import com.example.ui.viewmodels.MainViewModel", "import com.example.ui.components.CinematicBackground\nimport com.example.ui.viewmodels.MainViewModel")

# Remove the private POSTERS_* variables and PosterColumn function
content = re.sub(r'// High-resolution authentic TMDB blockbuster movie posters.*?(?=@OptIn)', '', content, flags=re.DOTALL)
content = re.sub(r'private fun PosterColumn.*?\}\n\}\n', '', content, flags=re.DOTALL)

# Replace the Box background with CinematicBackground
box_start = content.find('Box(\n        modifier = Modifier\n            .fillMaxSize()\n            .background(Color.Black)')

if box_start != -1:
    # Find the end of the Box content before row top-left logo
    # Actually, it's easier to just do regex replacement
    pattern = r'Box\(\s*modifier = Modifier\s*\.fillMaxSize\(\)\s*\.background\(Color\.Black\)\s*\.testTag\("login_screen"\)\s*\)\s*\{.*?// --- 3\. Top-Left Brand Logo'
    replacement = 'Box(\n        modifier = Modifier\n            .fillMaxSize()\n            .testTag("login_screen")\n    ) {\n        CinematicBackground()\n\n        // --- 3. Top-Left Brand Logo'
    content = re.sub(pattern, replacement, content, flags=re.DOTALL)

with open('app/src/main/java/com/example/ui/screens/LoginScreen.kt', 'w') as f:
    f.write(content)

