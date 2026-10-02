# Module plugin-api

# Flow Editor Features

The CMP Desktop Application includes a powerful visual Flow Editor that allows you to orchestrate capabilities, system nodes, subflows, and inputs/outputs into cohesive workflows. This document details some of the advanced features available in the flow editor UI to manage complex diagrams effectively.

## Collapsible Nodes

To keep large flows readable, you can collapse nodes or individual sections of nodes. 

### Entire Node Collapse
Click the arrow icon in the top right corner of any node's header to toggle the collapsed state for the entire node. When a node is fully collapsed, it hides its internal details and instead displays a single unified input port and output port. Any existing connections visually merge into these unified ports to save space, but they maintain their logical connections behind the scenes.

### Section Collapse (Inputs / Outputs)
For nodes with multiple inputs and outputs, you can collapse just the inputs section or just the outputs section by clicking on the respective "Inputs" or "Outputs" headers within the node body. This is extremely useful for capability nodes that have many inputs, allowing you to hide the ones you don't actively need to view.

*Note: Backward compatibility is maintained. If a flow file does not specify collapse states, nodes will load uncollapsed by default.*

## Dynamic List Parameters

The flow editor supports dynamic list parameters (Arrays and Tuples). When a node has an input port defined as a list, it can accept multiple incoming connections simultaneously.

### Connection Ordering
Because order often matters when merging data into a list, the flow editor explicitly tracks the order of connections.
- **Order Badges**: An order number badge is displayed in the middle of the bezier curve for any connection targeting a list input.
- **Editing Order**: You can left-click on the connection's order badge to open a context menu. This menu allows you to quickly adjust the connection's order, such as moving it to the first or last position in the list.
- **Default Order**: Newly added connections to a list input are appended to the end of the list by default.

## Selection & Multi-Selection

The Flow Editor supports flexible single- and multi-selection models for organizing and editing complex graphs:

### Single Selection
- **Left Click**: Clicking on an element (node, group card, floating label, or wire junction) selects that single item and clears all other selections.
- **Empty Canvas Click**: Clicking anywhere on the empty canvas clears the entire selection.

### Multi-Selection
- **Ctrl + Left Click (Toggle Selection)**: Holding `Ctrl` while left-clicking any element (node, group card, floating label, or connection junction) toggles that individual element into or out of the current selection without deselecting other items. You can combine different element types in a single selection (e.g. several nodes, a group, and wire junctions).
- **Marquee Box Selection**: Left-click and drag across empty canvas space to draw a selection rectangle. All nodes, groups, labels, and connection points enclosed within or intersecting the marquee box are added to the selection.

### Multi-Element Operations
When multiple elements are selected, operations apply across the entire selection set:
- **Group Drag / Move**: Dragging any selected element moves all currently selected nodes, groups, labels, and junctions simultaneously. All connecting wires, waypoints, and visual layout indicators dynamically update and snap in real time.
- **Batch Deletion**: Pressing `Delete` or `Backspace` deletes all selected elements and their corresponding connections simultaneously.
- **Batch Painting & Washing**: Using the Paint Tool (`P` / `B`) or Wash Tool (`W`) on a selection tints or resets the visual styling of all selected elements in a single click.

## Flow-Only Capabilities

Plugins can declare capabilities with `context = CapabilityContext.FLOW_ONLY`. These capabilities are exclusively available as nodes within the Flow Editor palette and cannot be invoked as standalone runner jobs. This is ideal for helper, normalization, or intermediate transformation capabilities that only make sense within an orchestrated workflow.

## Control Flow & Loop Nodes

The flow engine includes built-in system nodes for dynamic iterations and conditional branching without requiring disk-based subflow files:

### In-Flow Loops (`For` and `While`)
- **`For` Node**:
  - Can iterate over a plugin enum (by entering the enum name in `enum_name`), an incoming collection/list (`items` or `input_data`), or a numeric range (`start`..`end` with `step`).
  - Connected downstream nodes execute automatically for each variant or item.
  - The current element and iteration index are exposed through `item` and `index` output ports.
  - Results from each iteration are aggregated and emitted through the `output_data` port as a collection.
- **`While` Node**:
  - Iterates connected downstream nodes while the `condition` input evaluates to true, bounded by `max_iterations`.
  - Emits the current iteration count and aggregated `output_data`.

### Conditional Node
- Evaluates truthiness of the `condition` input or matches against an optional `expected_value` input port.
- Activates either the `if_true` or `if_false` output branch accordingly.
- Conditional nodes, along with loop and merger nodes, are fully selectable, movable, and copy-pastable alongside regular nodes.

## Data Transformation Nodes

### Convert Node
Allows converting data between types. Users can select an explicit conversion type via the `target_type` input port:
- `AUTO` (default): Uses implicit runtime type inference.
- `STRING`, `INT`, `DOUBLE`, `BOOLEAN`, `LONG`, `FLOAT`: Enforces explicit conversion to the desired primitive type.

### String Merger / Merge String Node (`string_merger`, `merge_string`)
Merges a collection of strings (`Collection<String>`) or lists into a single formatted string.
- Configurable `separator` (default `", "`), `prefix` (default `""`), and `postfix` (default `""`).
- Emits the merged result on the `output` port.

### Extract from String Node (`extract_from_string`, `string_extract`)
Extracts substrings matching a regular expression pattern from an input string:
- `string`: The input string to inspect.
- `regex`: Regular expression pattern to search for.
- `group_index` (optional): Capture group index to extract (0 for full match, 1 for first capture group, etc.). If omitted, automatically defaults to capture group 1 when capture groups exist in the regex, or full match (0) otherwise.
- Emits matched strings as a list (`List<String>`) on `output` and `matches`.

### Lists Filter Node (`list_filter`, `lists_filter`)
Filters and slices a list using standard Python-style slice syntax:
- `items`: Input list (`List<Any>`), with element types automatically propagated via type inference.
- `pattern`: Slicing expression (e.g. `x:y:z`, `x:y`, `:y`, `x:`, `::z`, `::-1`, single index `x` such as `-1` or `0`, with optional brackets `[x:y:z]` or `[-1]`).
- Out-of-bounds slices safely return empty lists without failing the flow.
- Emits the sliced sublist as `List<Any>` on the `output` port.

### List Check Node (`list_check`)
Validates that an incoming list meets minimum and maximum length constraints:
- `items`: Input list (`List<Any>`), with item type and semantic type preservation.
- `min_length` (optional): Minimum required number of elements.
- `max_length` (optional): Maximum allowed number of elements.
- `output`: Outputs the original input list if validation passes, or an empty list if validation fails.
- `result`: Boolean output port emitting `true` if the list size is within bounds, or `false` if the check failed.

