import os
import re
import sys
import urllib.request
import urllib.error
import xml.etree.ElementTree as ET

try:
    from packaging.version import parse as parse_version
    HAS_PACKAGING = True
except ImportError:
    try:
        from pkg_resources import parse_version
        HAS_PACKAGING = True
    except ImportError:
        HAS_PACKAGING = False
        print("Note: 'packaging' module not found. Update detection might not correctly compare alpha/beta versions.")
        print("To fix: pip install packaging\n")
        def parse_version(v):
            return v # Dummy implementation

def load_toml(filepath):
    # Try Python 3.11+ built-in tomllib
    try:
        import tomllib
        with open(filepath, "rb") as f:
            return tomllib.load(f)
    except ImportError:
        pass
        
    # Try third-party toml
    try:
        import toml
        with open(filepath, "r", encoding="utf-8") as f:
            return toml.load(f)
    except ImportError:
        # Fallback to basic regex parser
        return basic_toml_parse(filepath)

def basic_toml_parse(filepath):
    data = {"versions": {}, "libraries": {}, "plugins": {}}
    current_section = None
    with open(filepath, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#'): continue
            if line.startswith('['):
                section_name = line.strip('[]')
                if section_name in data:
                    current_section = data[section_name]
                else:
                    data[section_name] = {}
                    current_section = data[section_name]
                continue
                
            if current_section is not None:
                str_match = re.match(r'([a-zA-Z0-9_\-]+)\s*=\s*["\']([^"\']+)["\']', line)
                if str_match:
                    current_section[str_match.group(1)] = str_match.group(2)
                    continue
                dict_match = re.match(r'([a-zA-Z0-9_\-]+)\s*=\s*{(.+)}', line)
                if dict_match:
                    key = dict_match.group(1)
                    props_str = dict_match.group(2)
                    props = {}
                    for prop in props_str.split(','):
                        prop = prop.strip()
                        if '=' in prop:
                            k, v = prop.split('=', 1)
                            props[k.strip()] = v.strip().strip('"\'')
                    current_section[key] = props
    return data

def extract_dependencies(toml_data):
    versions = toml_data.get("versions", {})
    libs = toml_data.get("libraries", {})
    plugins = toml_data.get("plugins", {})
    
    dependencies = []
    
    for key, value in libs.items():
        group, name, version = None, None, None
        if isinstance(value, str):
            parts = value.split(':')
            if len(parts) == 3:
                group, name, version = parts
        elif isinstance(value, dict):
            if "module" in value:
                parts = value["module"].split(':')
                if len(parts) == 2:
                    group, name = parts
            else:
                group = value.get("group")
                name = value.get("name")
                
            if "version.ref" in value:
                version = versions.get(value["version.ref"])
            elif "version" in value:
                v_val = value["version"]
                if isinstance(v_val, dict) and "ref" in v_val:
                    version = versions.get(v_val["ref"])
                else:
                    version = v_val
                
        if group and name and isinstance(version, str):
            dependencies.append((group, name, version))
            
    for key, value in plugins.items():
        group, name, version = None, None, None
        if isinstance(value, str):
            parts = value.split(':')
            if len(parts) == 3:
                group, name, version = parts
        elif isinstance(value, dict):
            id_val = value.get("id")
            if id_val:
                group = id_val
                name = id_val + ".gradle.plugin"
                
            if "version.ref" in value:
                version = versions.get(value["version.ref"])
            elif "version" in value:
                v_val = value["version"]
                if isinstance(v_val, dict) and "ref" in v_val:
                    version = versions.get(v_val["ref"])
                else:
                    version = v_val
                
        if group and name and isinstance(version, str):
            dependencies.append((group, name, version))
            
    return dependencies

def get_google_maven_versions(group, name):
    url = f"https://maven.google.com/{group.replace('.', '/')}/group-index.xml"
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=5) as response:
            xml_data = response.read()
        root = ET.fromstring(xml_data)
        for child in root:
            if child.tag == name:
                versions = child.get('versions')
                if versions:
                    return versions.split(',')
    except Exception:
        pass
    return None

def get_maven_central_versions(group, name):
    url = f"https://repo1.maven.org/maven2/{group.replace('.', '/')}/{name}/maven-metadata.xml"
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=5) as response:
            xml_data = response.read()
        root = ET.fromstring(xml_data)
        versions = []
        for v in root.findall(".//version"):
            if v.text:
                versions.append(v.text)
        return versions
    except Exception:
        pass
    return None

def is_newer(v_test, v_current):
    if HAS_PACKAGING:
        try:
            return parse_version(v_test) > parse_version(v_current)
        except Exception:
            return False
    else:
        return v_test != v_current

def main():
    filepath = 'gradle/libs.versions.toml'
    if len(sys.argv) > 1:
        filepath = sys.argv[1]
        
    if not os.path.exists(filepath):
        print(f"Error: Could not find {filepath}. Please run this script from the project root.")
        return

    print("Parsing libs.versions.toml...")
    toml_data = load_toml(filepath)
    libs = extract_dependencies(toml_data)
    
    if not libs:
        print("No dependencies found or failed to parse.")
        return
        
    print(f"Checking {len(libs)} dependencies for updates...\n")
    
    updates_found = False
    
    for group, name, current_version in libs:
        repo = "Google Maven"
        versions = get_google_maven_versions(group, name)
        
        if not versions:
            repo = "Maven Central"
            versions = get_maven_central_versions(group, name)
            
        if not versions:
            print(f"- {group}:{name}: Not found in repos (might be JetBrains/other repo).")
            continue
            
        newer_versions = []
        if HAS_PACKAGING:
            for v in versions:
                if is_newer(v, current_version):
                    newer_versions.append(v)
        else:
            if current_version in versions:
                idx = versions.index(current_version)
                newer_versions = versions[idx+1:]
            else:
                newer_versions = versions[-5:]
                
        if newer_versions:
            updates_found = True
            latest = newer_versions[-1]
            all_newer = ", ".join(newer_versions[-3:])
            if len(newer_versions) > 3:
                all_newer = "... " + all_newer
                
            print(f"UPDATE AVAILABLE: {group}:{name}")
            print(f"  Current: {current_version} -> Latest: {latest} ({repo})")
            print(f"  Updates: {all_newer}\n")
            
    if not updates_found:
        print("All dependencies are up to date!")

if __name__ == "__main__":
    main()
