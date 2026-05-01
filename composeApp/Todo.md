Plugin:
- If a plugin is installed in an "invalid location" (non managed folder) it should show it as unmanaged, and should not be possible to update it, only to remove ità
- Add pause/cancel call to be able to properly pause teh program
- add non pausable flag

Plugin Api:
- (?) Plan to switch of plugins access files in a way that allow the main app to preload/prepare the needed resources to have more control on how the modules operate

Gradle:
- (?) Change the naming output of the plugin file to include the version
- Change the main app versioning to follow what gradle saves to avoid hardcoded strings
- Support for signing the modules with a key

Documentation:
- The api interface should have documentation for the various annotations and what / how to use it, probably with Dokka

Job:
- Currently there is no handling for when a module is plugged out and a job is running
> A setting should probably be added to define the behaviours like: prevent module unplugging while working, stop everything and unplug

Flows:
- When a module is remove and a flow loses the ability to proceed due to missing capability there should be ways of replacing that capability without redoing the whole flow
> This assumes the flows has to store redundant information about the capability to visualize the broken one and allow for replacement
> How connection behave when replacing has to be seen, a GUI to select where to connect what may be useful