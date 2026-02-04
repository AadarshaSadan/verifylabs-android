
import sys

def check_structure(filepath):
    print(f"Checking structure of {filepath}...")
    level = 0
    with open(filepath, 'r') as f:
        lines = f.readlines()
    
    for i, line in enumerate(lines):
        line_num = i + 1
        stripped = line.strip()
        
        # Check level before processing line (for definition check)
        # Assuming functions start at begin of line or indented 4 spaces
        if stripped.startswith("fun ") or stripped.startswith("override fun ") or stripped.startswith("private fun "):
            # Check indentation
            indent = len(line) - len(line.lstrip())
            # Expected indent for class member is 4 spaces (Level 1)
            # 1 level = 1 open brace (Class)
            print(f"Line {line_num}: Level {level}, Indent {indent}: {stripped}")
        
        for char in line:
            if char == '{':
                level += 1
            elif char == '}':
                level -= 1

if __name__ == "__main__":
    for arg in sys.argv[1:]:
        check_structure(arg)
