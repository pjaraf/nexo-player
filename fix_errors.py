with open('app/src/main/java/com/example/ui/screens/SeriesDetailScreen.kt', 'r') as f:
    lines = f.readlines()

import re

# We need to add testTag import
import_test_tag = "import androidx.compose.ui.platform.testTag\n"
lines.insert(25, import_test_tag) # add somewhere in imports

for i, line in enumerate(lines):
    if "This material API is experimental" in line or "@androidx.compose.material3.ExperimentalMaterial3Api" in line:
        pass # we might need to add it to OptIn
        
    if "title" in line and "AsyncImage" in lines[i-3:i+2]:
        lines[i] = line.replace('contentDescription = title', 'contentDescription = seriesTitle')
    if ".testTag(" in line:
        pass # it will work with the import

# Fix OptIn
for i, line in enumerate(lines):
    if line.startswith("@Composable") and lines[i-1].startswith("@OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)"):
        pass

with open('app/src/main/java/com/example/ui/screens/SeriesDetailScreen.kt', 'w') as f:
    f.writelines(lines)
