with open('app/src/main/java/com/example/ui/screens/PlayerScreen.kt', 'r') as f:
    content = f.read()

search_str = """                    }
                }
                }
            }
        }

        // Audio & Subtitles Selection Dialog"""

replace_str = """                    }
                }
            }
        }

        // Audio & Subtitles Selection Dialog"""

content = content.replace(search_str, replace_str)
with open('app/src/main/java/com/example/ui/screens/PlayerScreen.kt', 'w') as f:
    f.write(content)

