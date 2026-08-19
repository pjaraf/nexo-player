with open('app/src/main/java/com/example/ui/screens/PlayerScreen.kt', 'r') as f:
    content = f.read()

# Fix the signature
content = content.replace("onSelectSubtitle: (MediaTrackOption) -> Unit", "onSelectSubtitle: (MediaTrackOption?) -> Unit")

# Fix the dummy call
bad_call = 'onSelectSubtitle(MediaTrackOption("none", "none", "Desactivar Subtítulos", isSubtitlesDisabled))'
good_call = 'onSelectSubtitle(null)'
content = content.replace(bad_call, good_call)

with open('app/src/main/java/com/example/ui/screens/PlayerScreen.kt', 'w') as f:
    f.write(content)
