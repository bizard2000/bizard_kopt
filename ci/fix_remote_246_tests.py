#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
test_path = ROOT / "remote/src/test/java/com/bizard/homesmokeremote/ComposeLayoutTest.kt"
text = test_path.read_text(encoding="utf-8")

def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    text = text.replace(old, new, 1)

replace_once(
'''        snapshot("settings-320-font130")
        compose.onNodeWithText("Сохранить").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Подключить").performScrollTo().assertIsDisplayed()
''',
'''        snapshot("settings-320-font130")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNodeWithText("Сохранить").assertIsDisplayed()
        compose.onNodeWithText("Подключить").assertIsDisplayed()
''',
"settings actions lazy scroll",
)

replace_once(
'''        compose.onNodeWithText("Применить").assertIsDisplayed()
''',
'''        compose.onNodeWithText("Применить").performScrollTo().assertIsDisplayed()
''',
"monitor apply scroll",
)

replace_once(
'''        nextPage("График")
        compose.onNodeWithText("Тестовые сценарии · открыть").performScrollTo().performClick()
''',
'''        nextPage("График")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNodeWithText("Тестовые сценарии · открыть").assertIsDisplayed().performClick()
''',
"graph test card lazy scroll",
)

test_path.write_text(text, encoding="utf-8")
Path(__file__).unlink()
print("Remote 2.4.6 Compose test scrolling fixed")
