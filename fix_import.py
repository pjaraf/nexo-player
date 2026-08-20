with open('app/src/main/java/com/example/ui/components/TvFullscreenPlayerOverlay.kt', 'r') as f:
    content = f.read()

if "import androidx.compose.ui.text.style.TextOverflow" not in content:
    content = content.replace("import androidx.compose.ui.text.font.FontWeight", "import androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.ui.text.style.TextOverflow")
    with open('app/src/main/java/com/example/ui/components/TvFullscreenPlayerOverlay.kt', 'w') as f:
        f.write(content)
