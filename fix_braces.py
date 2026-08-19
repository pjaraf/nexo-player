with open('app/src/main/java/com/example/ui/screens/PlayerScreen.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
skip = False
for i, line in enumerate(lines):
    if "onAspectRatio = { cycleResizeMode() }" in line:
        new_lines.append(line)
        new_lines.append("                )\n")
        new_lines.append("            }\n")
        new_lines.append("        }\n")
        
        # We know the next lines were:
        #                 )
        #                 }
        #             }
        #         }
        skip = True
        continue
        
    if skip:
        if "// Audio & Subtitles Selection Dialog" in line:
            skip = False
            new_lines.append(line)
        continue
        
    new_lines.append(line)

with open('app/src/main/java/com/example/ui/screens/PlayerScreen.kt', 'w') as f:
    f.writelines(new_lines)
