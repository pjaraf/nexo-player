import re

with open('movie_phone.kt', 'r') as f:
    code = f.read()

# We know MovieDetailPhoneScreen starts at `@Composable\nprivate fun MovieDetailPhoneScreen(`
# And we want to cut off before `private fun MovieDetailTvScreen(` or `// Bottom Row`

match = re.search(r'(@Composable\nprivate fun MovieDetailPhoneScreen\([\s\S]*?)\n@OptIn', code)
if match:
    clean_phone = match.group(1)
else:
    # try another way
    match2 = re.search(r'(@Composable\nprivate fun MovieDetailPhoneScreen\([\s\S]*?\n\s*\n)@', code)
    if match2:
        clean_phone = match2.group(1)
    else:
        # just split by private fun MovieDetailTvScreen
        clean_phone = code.split('private fun MovieDetailTvScreen(')[0]
        # and remove any trailing decorators
        clean_phone = re.sub(r'@OptIn[\s\S]*?$', '', clean_phone)

with open('movie_phone_clean.kt', 'w') as f:
    f.write(clean_phone)

