import re

with open('app/src/main/java/com/example/ui/screens/MovieDetailScreen.kt', 'r') as f:
    code = f.read()

insert_pos = code.find('    Box(', code.find('private fun MovieDetailPhoneScreen'))
if insert_pos != -1:
    code = code[:insert_pos] + '    BackHandler(onBack = onBack)\n\n' + code[insert_pos:]

with open('app/src/main/java/com/example/ui/screens/MovieDetailScreen.kt', 'w') as f:
    f.write(code)

