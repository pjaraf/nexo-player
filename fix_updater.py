with open('app/src/main/java/com/example/data/updater/AppUpdateManager.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if line.strip() == '// Fallback for manual check if network didn\'t yield newer or failed':
        continue
    if line.strip() == '}' and len(new_lines) > 0 and new_lines[-1].strip() == '}' and new_lines[-2].strip() == '}':
        # we might have extra braces, let's just write exactly what's needed for the end of checkForUpdates
        pass
        
with open('app/src/main/java/com/example/data/updater/AppUpdateManager.kt', 'r') as f:
    content = f.read()

import re

# find checkForUpdates body
def fix(content):
    m = re.search(r'// Fallback for manual check if network didn\'t yield newer or failed\n\s*\}\n\s*AppStorage\.setLastUpdateCheckTime\(System\.currentTimeMillis\(\)\)\n\s*_latestUpdateInfo\.value = null\n\s*return@withContext null\n\s*\} catch \(e: Exception\) \{\n\s*Log\.e\(TAG, "Error during update check: \$\{e\.message\}"\)\n\s*\}\n\s*return@withContext null\n\s*\}', content)
    if m:
        replacement = """            AppStorage.setLastUpdateCheckTime(System.currentTimeMillis())
            _latestUpdateInfo.value = null
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error during update check: ${e.message}")
            return@withContext null
        }
    }"""
        return content.replace(m.group(0), replacement)
    return content

new_c = fix(content)
with open('app/src/main/java/com/example/data/updater/AppUpdateManager.kt', 'w') as f:
    f.write(new_c)

