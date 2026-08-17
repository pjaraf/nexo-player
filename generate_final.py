import re

with open('movie_original.kt', 'r') as f:
    orig = f.read()

# Orig ends at line 380, but it might be cut off.
# I'll just write the full file out.
