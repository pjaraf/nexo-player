import re

with open('app/src/main/java/com/example/ui/screens/MovieDetailScreen.kt', 'r') as f:
    code = f.read()

# I want to remove the extra MovieDetailTvScreen at the end.
# Actually I'll just rewrite it properly from scratch because this is messy.

