Plugin:
- If a plugin is installed in an "invalid location" (non managed folder) it should show it as unmanaged, and should not be possible to update it, only to remove it

Plugin Api:
- (?) Plan to switch of plugins access files in a way that allow the main app to preload/prepare the needed resources to have more control on how the plugins operate
- Add pause/cancel call to be able to properly pause the program
- Byte cose scanning for future security settings
- Documentation for update plugin api (make it work)
- Documentation for plugin setting page (make it work)
- Documentation for plugin custom action page (make it work)

Gradle:
- (?) Change the naming output of the plugin file to include the version
- Change the main app versioning to follow what gradle saves to avoid hardcoded strings
- Support for signing the plugins with a key

Documentation:
- The api interface should have documentation for the various annotations and what / how to use it, probably with Dokka
- Define repo in the documentation
- Better .md

Job:
- Currently there is no handling for when a plugin is plugged out and a job is running
> A setting should probably be added to define the behaviours like: prevent plugin unplugging while working, stop everything and unplug

Flows:
- Create with support for recursive flow
- When a plugin is removed and a flow loses the ability to proceed due to missing capability there should be ways of replacing that capability without redoing the whole flow
> This assumes the flows has to store redundant information about the capability to visualize the broken one and allow for replacement
> How connection behave when replacing has to be seen, a GUI to select where to connect what may be useful
- Default entrypoint, with local/remote support
- Data Duplication entrypoint

Repo:
- Test and make it work

Schedule:
- Scheduler

Auto update:
- Check for updates
- Download updates (is auto update is enabled)
- Install updates (?)
> On Startup check if something exist, if it does run the update (?)
> Maybe when installing execute the install script, then close the main app and let the update script work
- Auto compile update with github actions
- Recheck if the modules are still supported in the new version of the application

General:
- Notify that to set the system startup in the registry you need to start the application as administrator
> Possibly implement a way to start a subprocess to avoid restarting teh app and spawn that as administrator
- [x] Add app author
- [x] Add app image
- [ ] Add icon provider attribution (muh_zakaria from SVGRepo) in the About section (https://www.svgrepo.com/author/muh_zakaria/)

Security:
- Sign App releases
> Check app signature on download update, if invalid notify the user and ask if it is willing to proceed anyway
> It may be dangerous to make github auto sign the application, search for a solution
- Sign modules
> Same procedure as app for modules update

Bugs:
- For some reasons when updating the app teh first start will not load all plugins

Changelog:
- Markdown support
- Switch changelog.txt to changelog.md