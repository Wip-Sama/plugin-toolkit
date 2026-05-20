import re
import os
import argparse

def extract_changelog(version, changelog_path):
    if not os.path.exists(changelog_path):
        return f"Error: Changelog file not found at {changelog_path}"

    with open(changelog_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # Pattern to find the block
    # It starts with "Version: <version>" (case insensitive)
    # And ends before a line of at least 50 dashes or end of file
    # Uses re.DOTALL to make '.' match newlines
    pattern = rf'(?i)Version:\s*{re.escape(version)}.*?(?=\n-{{50,}}|\Z)'
    match = re.search(pattern, content, re.DOTALL)

    if match:
        return match.group(0).strip()
    else:
        return None

def main():
    parser = argparse.ArgumentParser(description='Extract version-specific notes from CHANGELOG.md')
    parser.add_argument('version', help='Version to extract (e.g., 1.4.2)')
    parser.add_argument('changelog', help='Path to CHANGELOG.md')
    parser.add_argument('--output', help='Output file path', default='release_notes.md')

    args = parser.parse_args()

    notes = extract_changelog(args.version, args.changelog)

    if notes:
        with open(args.output, 'w', encoding='utf-8') as f:
            f.write(notes)
        print(f"Successfully extracted notes for version {args.version} to {args.output}")
    else:
        print(f"Warning: Could not find changelog for version {args.version}")
        # Create a default fallback
        with open(args.output, 'w', encoding='utf-8') as f:
            f.write(f"Release v{args.version}")

if __name__ == "__main__":
    main()
