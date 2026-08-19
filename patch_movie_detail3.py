with open('app/src/main/java/com/example/ui/screens/MovieDetailScreen.kt', 'r') as f:
    content = f.read()

content = content.replace("movieCover", "cover")

with open('app/src/main/java/com/example/ui/screens/MovieDetailScreen.kt', 'w') as f:
    f.write(content)

