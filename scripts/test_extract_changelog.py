import unittest
import os
import tempfile
import shutil
from extract_changelog import extract_changelog

class TestExtractChangelog(unittest.TestCase):
    def setUp(self):
        self.test_dir = tempfile.mkdtemp()
        self.changelog_path = os.path.join(self.test_dir, "CHANGELOG.md")
        
    def tearDown(self):
        shutil.rmtree(self.test_dir)

    def write_changelog(self, content):
        with open(self.changelog_path, 'w', encoding='utf-8') as f:
            f.write(content)

    def test_extract_basic(self):
        content = """Version: 1.4.2
Date: 2026-05-15
- Feature A
- Feature B
----------------------------------------------------------------------------------------------------
Version: 1.4.1
- Fix C
"""
        self.write_changelog(content)
        notes = extract_changelog("1.4.2", self.changelog_path)
        self.assertIn("Version: 1.4.2", notes)
        self.assertIn("- Feature A", notes)
        self.assertIn("- Feature B", notes)
        self.assertNotIn("Version: 1.4.1", notes)
        self.assertNotIn("Fix C", notes)

    def test_extract_last_version(self):
        content = """Version: 1.4.2
- Feature A
----------------------------------------------------------------------------------------------------
Version: 1.4.1
- Feature B
"""
        self.write_changelog(content)
        notes = extract_changelog("1.4.1", self.changelog_path)
        self.assertIn("Version: 1.4.1", notes)
        self.assertIn("- Feature B", notes)
        self.assertNotIn("Version: 1.4.2", notes)

    def test_extract_not_found(self):
        content = "Version: 1.0.0\n- Initial"
        self.write_changelog(content)
        notes = extract_changelog("2.0.0", self.changelog_path)
        self.assertIsNone(notes)

    def test_extract_case_insensitive(self):
        content = "version: 1.4.2\n- Test"
        self.write_changelog(content)
        notes = extract_changelog("1.4.2", self.changelog_path)
        self.assertIsNotNone(notes)
        self.assertIn("version: 1.4.2", notes)

    def test_extract_separator_length(self):
        # Should not stop on short dashes
        content = """Version: 1.4.2
- List item
---
- Still 1.4.2
----------------------------------------------------------------------------------------------------
Version: 1.4.1
"""
        self.write_changelog(content)
        notes = extract_changelog("1.4.2", self.changelog_path)
        self.assertIn("- Still 1.4.2", notes)
        self.assertNotIn("Version: 1.4.1", notes)

if __name__ == '__main__':
    unittest.main()
