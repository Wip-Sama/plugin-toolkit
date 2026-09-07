"""Script to find Kotlin files exceeding a specified line count threshold.
Useful for identifying complex or oversized files before refactoring.
"""

import argparse
import os
import sys


def count_lines(file_path: str) -> int:
    """Counts lines in a file, using utf-8 encoding with fallback."""
    try:
        with open(file_path, "r", encoding="utf-8", errors="replace") as f:
            return sum(1 for _ in f)
    except Exception as e:
        print(f"Warning: Unable to read file {file_path}: {e}", file=sys.stderr)
        return 0


def find_large_kotlin_files(
    root_dir: str,
    threshold: int,
    ignore_dirs: set[str] | None = None,
) -> list[tuple[str, int]]:
    """Walks root_dir and returns sorted list of (rel_or_abs_path, line_count) for .kt/.kts files with lines > threshold."""
    if ignore_dirs is None:
        ignore_dirs = {
            ".git",
            ".gradle",
            ".idea",
            "build",
            ".kotlin",
            "bin",
            ".agents",
        }

    results: list[tuple[str, int]] = []

    for dirpath, dirnames, filenames in os.walk(root_dir):
        # Prune ignored directories in-place
        dirnames[:] = [d for d in dirnames if d not in ignore_dirs]

        for filename in filenames:
            if filename.endswith(".kt") or filename.endswith(".kts"):
                full_path = os.path.join(dirpath, filename)
                line_count = count_lines(full_path)
                if line_count > threshold:
                    rel_path = os.path.relpath(full_path, root_dir)
                    results.append((rel_path, line_count))

    # Sort descending by line count, then by path
    results.sort(key=lambda item: (-item[1], item[0]))
    return results


def main() -> None:
    parser = argparse.ArgumentParser(
        description="List Kotlin (.kt, .kts) files exceeding a specified line count threshold."
    )
    parser.add_argument(
        "-l",
        "--lines",
        type=int,
        default=200,
        help="Line count threshold (default: 200). Files with strictly more than this number of lines will be listed.",
    )
    parser.add_argument(
        "-d",
        "--dir",
        type=str,
        default=None,
        help="Root directory to search (defaults to project root containing scripts/).",
    )

    args = parser.parse_args()

    threshold = args.lines
    if threshold < 0:
        print("Error: Line threshold must be non-negative.", file=sys.stderr)
        sys.exit(1)

    if args.dir:
        root_dir = os.path.abspath(args.dir)
    else:
        # Default to repository root (parent directory of scripts/)
        script_dir = os.path.dirname(os.path.abspath(__file__))
        root_dir = os.path.abspath(os.path.join(script_dir, ".."))

    if not os.path.isdir(root_dir):
        print(f"Error: Directory not found: {root_dir}", file=sys.stderr)
        sys.exit(1)

    print(f"Searching for Kotlin files with > {threshold} lines in: {root_dir}")
    print("-" * 80)

    large_files = find_large_kotlin_files(root_dir, threshold)

    if not large_files:
        print(f"No Kotlin files found exceeding {threshold} lines.")
        return

    # Print table header
    print(f"{'Lines':>8}  {'File Path'}")
    print(f"{'-'*8}  {'-'*68}")
    for rel_path, count in large_files:
        print(f"{count:>8}  {rel_path}")

    print("-" * 80)
    print(f"Total files found: {len(large_files)}")


if __name__ == "__main__":
    main()
