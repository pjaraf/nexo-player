import re
import json

# Update build.gradle.kts
with open('app/build.gradle.kts', 'r') as f:
    content = f.read()

version_code_match = re.search(r'versionCode = (\d+)', content)
version_name_match = re.search(r'versionName = "([^"]+)"', content)

if version_code_match and version_name_match:
    old_vc = int(version_code_match.group(1))
    old_vn = version_name_match.group(1)
    
    new_vc = old_vc + 1
    parts = old_vn.split('.')
    parts[-1] = str(int(parts[-1]) + 1)
    new_vn = '.'.join(parts)
    
    content = re.sub(r'versionCode = \d+', f'versionCode = {new_vc}', content)
    content = re.sub(r'versionName = "[^"]+"', f'versionName = "{new_vn}"', content)
    
    with open('app/build.gradle.kts', 'w') as f:
        f.write(content)
        
    # Update version.json
    with open('version.json', 'r') as f:
        v_data = json.load(f)
        
    v_data['versionName'] = new_vn
    v_data['versionCode'] = new_vc
    
    with open('version.json', 'w') as f:
        json.dump(v_data, f, indent=4)
        
    print(f"Bumped to {new_vn} ({new_vc})")
