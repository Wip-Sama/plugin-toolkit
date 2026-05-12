Plugin:
- If a plugin is installed in an "invalid location" (non managed folder) it should show it as unmanaged, and should not be possible to update it, only to remove it
> now the plugin installation status is linked to the managed folder so they will (should) disappear

Plugin Api:
- Add pause/cancel call to be able to properly pause the program
> sorta done
- Byte cose scanning for future security settings
- Properly test plugin update api
- Allow plugin to define custom types, share custom types
- Allow plugin to request special things like path/files (with specific types)

Documentation:
- Documentation for update plugin api
- Documentation for plugin setting page
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
- Conditional nodes like: is result (something) then (something else)

Repo:
- Test and make it work

Schedule:
- Scheduler

Auto update:
- Install updates (?)
> On Startup check if something exist, if it does run the update (?)
> Maybe when installing execute the install script, then close the main app and let the update script work
- Recheck if the modules are still supported in the new version of the application

General:
- Notify that to set the system startup in the registry you need to start the application as administrator
> Possibly implement a way to start a subprocess to avoid restarting teh app and spawn that as administrator
- Add icon provider attribution (muh_zakaria from SVGRepo) in the About section (https://www.svgrepo.com/author/muh_zakaria/)

Security:
- Sign App releases
> Check app signature on download update, if invalid notify the user and ask if it is willing to proceed anyway
> It may be dangerous to make github auto sign the application, search for a solution
- Sign modules
> Same procedure as app for modules update

Bugs:
- For some reasons when updating the app the first start will not load all plugins

- Reload should also revalidate
- If the validation fails it should be unvalidated and unloaded  
- When the validation fails it gets stuck in pending setup even if the setup completed
- The repositories should have a button to "share" copy the repo link
- Add options to contextual menu in windows
- Reload should call the validation if it's not validated
- Reload should call the load function in the plugin
- Option to select the number of worker
> When decreasing under the number of active worker the change will take effect when each worker finishes to the number required jobs (min 1)
- Job Lifecycle like Plugin Lifecycle
- Use annotations and reflections to auto define SettingsRegistry.build { ... } in main.kt (Koin) if possible
- Take correct description for teh update