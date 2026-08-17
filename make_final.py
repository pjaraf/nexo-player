with open('movie_top.kt', 'r') as f:
    top = f.read()

# get the tv_screen from make_tv.py
# actually let's just run make_tv.py but modified to use movie_phone_clean.kt
with open('make_tv.py', 'r') as f:
    make_tv = f.read()

make_tv = make_tv.replace("with open('movie_phone.kt', 'r') as f:\n    phone = f.read()", "with open('movie_phone_clean.kt', 'r') as f:\n    phone = f.read()")

with open('make_final_tv.py', 'w') as f:
    f.write(make_tv)

