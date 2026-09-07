import os
import shutil
import tempfile
import unittest

from find_large_kotlin_files import count_lines, find_large_kotlin_files


class TestFindLargeKotlinFiles(unittest.TestCase):
    def setUp(self):
        self.test_dir = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.test_dir)

    def create_file(self, rel_path: str, lines_count: int):
        full_path = os.path.join(self.test_dir, rel_path)
        os.makedirs(os.path.dirname(full_path), exist_ok=True)
        with open(full_path, "w", encoding="utf-8") as f:
            f.write("\n".join(f"// line {i}" for i in range(lines_count)) + "\n")
        return full_path

    def test_count_lines(self):
        path = self.create_file("test.kt", 15)
        self.assertEqual(count_lines(path), 15)

    def test_find_large_files_filtering_and_sorting(self):
        self.create_file("src/Small.kt", 50)
        self.create_file("src/Medium.kt", 120)
        self.create_file("src/Large.kt", 300)
        self.create_file("src/Script.kts", 150)
        self.create_file("src/IgnoredExtension.java", 500)
        self.create_file("build/Generated.kt", 1000)

        # Threshold 100 should match Medium.kt (120), Script.kts (150), Large.kt (300)
        # build/ should be ignored by default
        results = find_large_kotlin_files(self.test_dir, threshold=100)

        expected_order = [
            (os.path.join("src", "Large.kt"), 300),
            (os.path.join("src", "Script.kts"), 150),
            (os.path.join("src", "Medium.kt"), 120),
        ]
        self.assertEqual(results, expected_order)

    def test_threshold_boundary(self):
        self.create_file("Exact.kt", 100)
        results = find_large_kotlin_files(self.test_dir, threshold=100)
        # strictly greater than 100
        self.assertEqual(results, [])

        results_99 = find_large_kotlin_files(self.test_dir, threshold=99)
        self.assertEqual(len(results_99), 1)
        self.assertEqual(results_99[0][0], "Exact.kt")
        self.assertEqual(results_99[0][1], 100)


if __name__ == "__main__":
    unittest.main()
