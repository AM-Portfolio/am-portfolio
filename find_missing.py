import os
import re

for root, dirs, files in os.walk('.'):
    for f in files:
        if f.endswith('.java'):
            path = os.path.join(root, f)
            with open(path, 'r', encoding='utf-8') as file:
                content = file.read()
                if 'List<' in content or ' List ' in content:
                    if 'import java.util.List;' not in content and 'import java.util.*;' not in content:
                        print(f"MISSING IMPORT: {path}")
