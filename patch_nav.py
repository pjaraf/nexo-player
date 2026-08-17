import re

with open('app/src/main/java/com/example/ui/navigation/AppNavigation.kt', 'r') as f:
    content = f.read()

# Add splash route
content = content.replace('object Routes {\n', 'object Routes {\n    const val SPLASH = "splash"\n')

# Change start destination
content = content.replace('''    val startDestination = if (!AppStorage.isLoggedIn()) {
        Routes.LOGIN
    } else {
        Routes.TABS
    }''', '''    val startDestination = Routes.SPLASH''')

# Add splash screen composable
splash_composable = '''        composable(Routes.SPLASH) {
            SplashScreen(
                onSplashFinished = {
                    val nextScreen = if (!AppStorage.isLoggedIn()) Routes.LOGIN else Routes.TABS
                    navController.navigate(nextScreen) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
'''

content = content.replace('        ) {\n', '        ) {\n' + splash_composable)

with open('app/src/main/java/com/example/ui/navigation/AppNavigation.kt', 'w') as f:
    f.write(content)
