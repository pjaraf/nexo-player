import re

with open('app/src/main/java/com/example/data/updater/AppUpdateManager.kt', 'r') as f:
    content = f.read()

content = re.sub(
    r'if \(isNewer \|\| force\) \{\s+val dismissed = AppStorage.getDismissedUpdateVersion\(\)\s+if \(!force && dismissed == versionInfo.versionName\) \{\s+Log.d\(TAG, "Update v\$\{versionInfo.versionName\} was previously dismissed"\)\s+\} else \{\s+versionInfo\s+\}\s+Log.i\(TAG, "New update found: v\$\{infoToShow.versionName\} \(code=\$\{infoToShow.versionCode\}\)"\)\s+_latestUpdateInfo.value = infoToShow\s+AppStorage.setLastUpdateCheckTime\(System.currentTimeMillis\(\)\)\s+return@withContext infoToShow\s+\}\s+\}',
    """if (isNewer) {
                    val dismissed = AppStorage.getDismissedUpdateVersion()
                    if (!force && dismissed == versionInfo.versionName) {
                        Log.d(TAG, "Update v${versionInfo.versionName} was previously dismissed")
                    } else {
                        Log.i(TAG, "New update found: v${versionInfo.versionName} (code=${versionInfo.versionCode})")
                        _latestUpdateInfo.value = versionInfo
                        AppStorage.setLastUpdateCheckTime(System.currentTimeMillis())
                        return@withContext versionInfo
                    }
                }""",
    content, count=1
)

content = re.sub(
    r'if \(isNewer \|\| force\) \{\s+val dismissed = AppStorage.getDismissedUpdateVersion\(\)\s+if \(!force && dismissed == releaseInfo.versionName\) \{\s+Log.d\(TAG, "Update v\$\{releaseInfo.versionName\} was previously dismissed"\)\s+\} else \{\s+releaseInfo\s+\}\s+Log.i\(TAG, "New update found via GitHub: v\$\{infoToShow.versionName\}"\)\s+_latestUpdateInfo.value = infoToShow\s+AppStorage.setLastUpdateCheckTime\(System.currentTimeMillis\(\)\)\s+return@withContext infoToShow\s+\}\s+\}',
    """if (isNewer) {
                    val dismissed = AppStorage.getDismissedUpdateVersion()
                    if (!force && dismissed == releaseInfo.versionName) {
                        Log.d(TAG, "Update v${releaseInfo.versionName} was previously dismissed")
                    } else {
                        Log.i(TAG, "New update found via GitHub: v${releaseInfo.versionName}")
                        _latestUpdateInfo.value = releaseInfo
                        AppStorage.setLastUpdateCheckTime(System.currentTimeMillis())
                        return@withContext releaseInfo
                    }
                }""",
    content, count=1
)

with open('app/src/main/java/com/example/data/updater/AppUpdateManager.kt', 'w') as f:
    f.write(content)
