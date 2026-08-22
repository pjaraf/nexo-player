import re

with open("app/src/main/java/com/example/ui/screens/LiveScreen.kt", "r") as f:
    content = f.read()

content = content.replace("@Composabl@Composable", "@Composable")
content = content.replace("onPlayChannel(channel.id, selectedCat, channel.name)", "onPlayChannel(channel.id, selectedCat ?: \"ALL\", channel.name)")

with open("app/src/main/java/com/example/ui/screens/LiveScreen.kt", "w") as f:
    f.write(content)
