import json
import re

with open('version.json', 'r') as f:
    data = json.load(f)

old_version = data['versionName']
parts = old_version.split('.')
parts[-1] = str(int(parts[-1]) + 1)
new_version = '.'.join(parts)
data['versionName'] = new_version
data['versionCode'] = data['versionCode'] + 1

with open('version.json', 'w') as f:
    json.dump(data, f, indent=4)

with open('app/build.gradle.kts', 'r') as f:
    content = f.read()

content = re.sub(r'versionName = ".*?"', f'versionName = "{new_version}"', content)
content = re.sub(r'versionCode = \d+', f'versionCode = {data["versionCode"]}', content)

with open('app/build.gradle.kts', 'w') as f:
    f.write(content)

print(f"Bumped to {new_version}")
