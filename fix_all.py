import re

with open('app/src/main/java/com/example/data/api/XtreamApi.kt', 'r') as f:
    content = f.read()

content = content.replace('return@withContext', 'return')

def replace_in_block(block):
    # Split by lines and replace return with return@withContext
    lines = block.split('\n')
    for i, line in enumerate(lines):
        if 'return' in line and not line.strip().startswith('//'):
            lines[i] = re.sub(r'\breturn\b', 'return@withContext', line)
    return '\n'.join(lines)

# Find all blocks: = withContext(Dispatchers.IO) { ... }
# since regex is hard with nested braces, let's just do it manually for each function
funcs = ['fetch', 'fetchLiveM3uContent', 'parseAndCacheLiveList', 'getLiveCategories', 'getLiveChannels', 'getVodCategories', 'getVodStreams', 'getVodDetail', 'getSeriesCategories', 'getSeriesList', 'getSeriesDetail']

for func in funcs:
    pattern = r'(suspend fun ' + func + r'\b.*?= withContext\(Dispatchers\.IO\) \{)(.*?)(\n    \})'
    def replacer(m):
        return m.group(1) + replace_in_block(m.group(2)) + m.group(3)
    content = re.sub(pattern, replacer, content, flags=re.DOTALL)
    
    pattern_private = r'(private suspend fun ' + func + r'\b.*?= withContext\(Dispatchers\.IO\) \{)(.*?)(\n    \})'
    def replacer_priv(m):
        return m.group(1) + replace_in_block(m.group(2)) + m.group(3)
    content = re.sub(pattern_private, replacer_priv, content, flags=re.DOTALL)

with open('app/src/main/java/com/example/data/api/XtreamApi.kt', 'w') as f:
    f.write(content)
