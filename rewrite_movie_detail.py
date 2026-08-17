import re

with open('app/src/main/java/com/example/ui/screens/SeriesDetailScreen.kt', 'r') as f:
    series_code = f.read()

with open('app/src/main/java/com/example/ui/screens/MovieDetailScreen.kt', 'r') as f:
    movie_code = f.read()

# We want to extract the Tv layout from series_code and adapt it.
# Actually, since the user said "Quiero que las películas se vean igual que las series como la imagen con la diferencia que abajo donde salen los números aparescan películas en un carrusel", it means the TV screen should look like SeriesDetailTvScreen.

