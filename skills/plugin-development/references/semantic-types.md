# Semantic Types Reference

Semantic Types dictate domain-specific data contracts on ports (e.g. "is this string a file path?", "is it a hex color?", "is it an image URL?"), separating domain semantics from binary Kotlin data representations (`DataType`).

---

## 1. Overview & Multi-Valued Contract

Ports support **multi-valued semantic types** (`List<SemanticType>`). This enables:
- A single output port to satisfy multiple downstream consumers (e.g. satisfying both a generic `image` contract and an explicit `file:path` contract).
- An input port to accept multiple acceptable semantic structures.

---

## 2. Grammar & Structured Identity

Every `SemanticType` consists of:
- **Namespace (Optional)**: Avoids collisions across independent plugin ecosystems (e.g. `wip/` vs standard types).
- **Name (Required)**: The primary semantic category (e.g. `color`, `file`, `image`).
- **Variant (Optional)**: Specialization or format subtype (e.g. `rgb`, `png`, `path`).

### String Representation (Grammar)

```text
[namespace/][name][:variant]
```

### Parsing & Normalization Rules:
1. **Normalization**: All namespace, name, and variant components are trimmed, converted to lowercase, and Unicode NFKC normalized.
2. **MIME Delimiter Syntax**: Standard MIME types (e.g. `image/png`) use `/` to separate namespace and name (`namespace = "image"`, `name = "png"`).
3. **Variant Delimiter Syntax**: `:` separates the primary name and variant (`name = "color"`, `variant = "hex"`).

### Canonical Examples:

| String Literal | Namespace | Name | Variant |
| :--- | :--- | :--- | :--- |
| `color:hex` | `null` | `color` | `hex` |
| `file:path` | `null` | `file` | `path` |
| `image/png` | `image` | `png` | `null` |
| `color` | `null` | `color` | `null` |
| `custom/audio:wav` | `custom` | `audio` | `wav` |

---

## 3. Standard Semantic Registry & Visual Priority

The host application resolves a port's `List<SemanticType>` into visual categories to render contextual UI widgets:

### Priority Categories:
1. **`COLOR`** (`color`) &rarr; Triggers interactive color picker controls.
2. **`IMAGE`** (`image`, `image/*`) &rarr; Triggers inline image preview widgets.
3. **`AUDIO`** (`audio`, `audio/*`) &rarr; Triggers inline audio player controls.
4. **`VIDEO`** (`video`, `video/*`) &rarr; Triggers inline video player controls.
5. **`FILE`** (`file`, `directory`, `application/*`) &rarr; Triggers native file/folder selector dialogs.
6. **`PATH`** (`path`) &rarr; Triggers local path explorer.

### Priority Resolution:
When a port declares multiple semantic types, the category is resolved to the **highest-priority match** in the hierarchy above. For example, if a port declares `[image:png, file:path]`, the resolved category is `IMAGE`.

---

## 4. Connection Compatibility & Matching Rules

When evaluating if an output port (source `S`) can connect to an input port (target `T`), the system evaluates `isSemanticTypeCompatible`:

### General Rules:
1. **Null/Empty List Safety**: If either the source or target has an empty semantic types list, they are treated as **universally compatible**.
2. **Any-to-Any Evaluation**: A connection is valid if **at least one** `SemanticType` in the source list satisfies **at least one** `SemanticType` in the target list.

### Individual Matcher Logic (`S` vs `T`):
For source `S` and target `T`, a match is satisfied if:
1. **Identity**: `S.namespace == T.namespace && S.name == T.name && S.variant == T.variant`.
2. **Generalization**: The source is specialized while the target is general.
   - Example: Source `color:rgb` satisfies Target `color`.
3. **Lenient Specialization**: The source is general while the target is specialized (enabled by default for flexibility).
   - Example: Source `color` satisfies Target `color:hex`.
4. **Wildcards**: The target defines a wildcard `*` for variant, name, or namespace.
   - Example: Source `image/png` satisfies Target `image/*`.

---

## 5. Migration & Backwards Compatibility

1. **Annotations**:
   - `@CapabilityParam`, `@CapabilityOutput`, `@CapabilityResult` support `semanticTypes: Array<String>`.
   - Single `semanticType: String` remains supported for backward compatibility, automatically parsed into a structured `SemanticType`.
2. **JSON Deserialization**:
   - Manifest and flow serializers use `JsonTransformingSerializer` to normalize legacy single `semanticType: "..."` strings into modern `semanticTypes: [...]` arrays.
3. **KSP Code Generator Output**:
   - Generated metadata writes both `semanticTypes` arrays and legacy canonical strings (`semanticType = "..."`) to preserve ABI compatibility with older host builds.
