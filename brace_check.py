
import sys

def check_braces(filepath):
    print(f"Checking {filepath}...")
    level = 0
    with open(filepath, 'r') as f:
        lines = f.readlines()
    
    first_level_zero = True # Initial state is level 0
    
    for i, line in enumerate(lines):
        line_num = i + 1
        for char in line:
            if char == '{':
                level += 1
            elif char == '}':
                level -= 1
                if level < 0:
                    print(f"ERROR: Extra closing brace at Line {line_num}: {line.strip()}")
                    return
                if level == 0:
                     print(f"INFO: Level dropped to 0 at Line {line_num}: {line.strip()}")

    
    if level > 0:
        print(f"ERROR: Missing {level} closing brace(s) at end of file. Last line: {len(lines)}")
    elif level == 0:
        print("SUCCESS: Braces balanced (at end).")

if __name__ == "__main__":
    for arg in sys.argv[1:]:
        check_braces(arg)
