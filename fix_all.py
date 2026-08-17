import re

with open('movie_original.kt', 'r') as f:
    orig = f.read()

# Replace "fun MovieDetailScreen(" with "private fun MovieDetailPhoneScreen(" in orig
# Wait, I already did that in movie_original.kt?
# Let's check what's in movie_original.kt

