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

## Group Movement

You can select multiple nodes by holding Shift and clicking, or by dragging a selection box across the canvas. When moving a selected group of nodes, all connections and visual indicators attached to those nodes update dynamically and seamlessly as the group is repositioned.
