import re

with open('app/src/main/java/com/example/ui/screens/MovieDetailScreen.kt', 'r') as f:
    code = f.read()

# Replace the inner BackHandler
code = code.replace('''                 BackHandler {
                     isFullScreenMode = false
                 }''', '')

# Insert at the top level
insert_pos = code.find('    val info = detail?.info')
if insert_pos != -1:
    code = code[:insert_pos] + '''    BackHandler {
        if (isFullScreenMode) {
            isFullScreenMode = false
        } else {
            onBack()
        }
    }

''' + code[insert_pos:]

with open('app/src/main/java/com/example/ui/screens/MovieDetailScreen.kt', 'w') as f:
    f.write(code)

