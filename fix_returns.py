import re

with open('app/src/main/java/com/example/data/api/XtreamApi.kt', 'r') as f:
    content = f.read()

def replace_returns(match):
    return match.group(0).replace('return ', 'return@withContext ')

content = re.sub(r'suspend fun [a-zA-Z0-9_]+\([^)]*\): [^{]+ = withContext\(Dispatchers\.IO\) \{.*?\}', replace_returns, content, flags=re.DOTALL)
content = re.sub(r'private suspend fun [a-zA-Z0-9_]+\([^)]*\): [^{]+ = withContext\(Dispatchers\.IO\) \{.*?\}', replace_returns, content, flags=re.DOTALL)

with open('app/src/main/java/com/example/data/api/XtreamApi.kt', 'w') as f:
    f.write(content)
