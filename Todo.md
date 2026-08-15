
Plugin Api:
- Add pause/cancel call to be able to properly pause the program
> sorta done
- Byte cose scanning for future security settings
- Properly test plugin update api


Flows:
- Default entrypoint, with local/remote support
- Data Duplication entrypoint
- Conditional nodes like: is result (something) then (something else)
- Add info abot connection points (mime type and datatype)
- connection point should be disabled if a default is used
- connection point default should represent the datatype (bool should not be a textfield for example)

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
- Reload should also revalidate
- If the validation fails it should be unvalidated and unloaded  
- When the validation fails it gets stuck in pending setup even if the setup completed
- Reload should call the validation if it's not validated
- Reload should call the load function in the plugin
- Use annotations and reflections to auto define SettingsRegistry.build { ... } in main.kt (Koin) if possible
- execution graph to flows and a setting to allow for parallelization of capabilities when running flows
- capability resources usage estimation function
- Scanner that execute jobs on events