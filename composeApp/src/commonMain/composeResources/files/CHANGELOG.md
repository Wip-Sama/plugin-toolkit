Version: 1.7.0
Date: 28-06-2026
Added:
	- It's not possible to change a connection destination/origin after its creation
	- Tooltip names for the capability/flows with long names
	- Flows and Nodes now are in a not-ready state if a required parameter is not connected or has a default
	- Components no longer default to the color purple for accents
	- PluginsSetting can now define constraints like the CapabilityParameter
	- Signing for manifest files
Changes:
  - now updating a plugin will make the button reflect what's happening to avoid confusion
  - shift is not used to fast delete a node connection
  - BIG refactor to the plugin-api so 1.7.x is not compatible with 1.6.x
  - PluginFileSystem is completely revamped with new features and pipelines to follow to be compliant with best practices for plugins
Fixed:
  - Broken flow would appear as running
  - plugin-api would improperly identify the required capability param
----------------------------------------------------------------------------------------------------
Version: 1.6.1
Date: 13-06-2026
Changes:
	- Minor improvements to the codebase
Fixed:
  - Parsing of null / empty values in flows
  - Create folder system node not accepting connection in the path port
Planned:
  - Create folder would return an error if the folder already existed
----------------------------------------------------------------------------------------------------
Version: 1.6.0
Date: 12-06-2026
Changes:
	- Updated dependencies
	- Execute -> Flows / Capabilities
	- Flow editor is now only accessible by the flow manager
	- Changelog markdown strikethrough updated do double \~\~ from single
	- Various minor internal improvements
Removed:
	- Dedicated sections for flows and plugins
Added:
	- Better logging for plugin loading step 
	- Dedicated section for repository and manager
	- System node to create a new folder with a specific name given a directory
	- Dynamic node Parameters, list and similar now allow for multiple connections in the port
	- Capabilities locked behind required plugin setting/s
	- Capabilities only available in flows 
	- Capabilities only available in standard execution 
	- Autosave setting for flows
	- Dense CapabilityOutput
	- Nodes are not compactable in input/output to accomodate nodes with lots of input/outputs
	- Migration logic for plugins who change capabilities (update flows where possible)
	- Tooltip for node ports
Fixed:
	- Moving multiple selected nodes would not animate all the connection properly until movement stopped
	- Capabilities with lots of input/output would not be drawn correctly
	- When a capability used by a flow is changed, the flow is marked as broken if not updatable / alternative available
	- When a flow is installed the install button would not change to installed until reentering the tab/page
	- Parsing of list elements in flows runner
----------------------------------------------------------------------------------------------------
Version: 1.5.2
Date: 23-05-2026
Fixed:
  - JOB executor not closing some job correctly
  - Release date of version 1.5.1 in changelog
----------------------------------------------------------------------------------------------------
Version: 1.5.1
Date: 23-05-2026
Added:
	- Iterator node
	- Comparator node
	- Converter can now convert Int to Bool (try)
	- Ability to search/type path/folder and them be resolved by the application when working with flows and capabilities
	- Common semantic type analysis with custom input fields based on those
	- Optional regex support to validate string input
Changes:
	- Restyled the ui a bit more
	- System dialogs are now blocking for the ui
	- Moved managed folders to the settings
----------------------------------------------------------------------------------------------------
Version: 1.5.0
Date: 20-05-2026
Changes:
	- Major ui rework
Fixed:
	- Updater on windows now launches with correct path when applicable (.jar is not updatable)
  - The update page now correctly displays the change description
  - Quick links now works
Added:
  - Plugin can now define which os they support (linux, windows, macos)
  - Plugin can now define multiple return values
  - Plugin can now add custom types (mime/type) to the return values
  - Check to ensure the plugin is compatible with the app version
  - Plugin signature
  - Library used in the about section
----------------------------------------------------------------------------------------------------
Version: 1.4.1
Date: 12-05-2026
Added
	- Ended jobs tab to see the logs and what happened 
Changes:
  - Incremented time to download the update before closing the connection (cut if not data has been transferred in 30s)
  - Implemented new system for the settings generation and control
Fixed:
  - Updater on windows not launching correctly (again)
  - Plugin-api failed to generate proper plugin if not settings were defined
Planned:
  - ~~The update page now correctly displays the change description~~
----------------------------------------------------------------------------------------------------
Version: 1.4.0
Date: 12-05-2026
Added:
  - Support for required and secret settings.
  - Button to directly open latest log
Changes:
  - Invalidating and unloading plugins on validation fail
  - Major refactor
Fixed:
  - Installing puling would be stuck on "required setup" whgen updating from local in some cases
----------------------------------------------------------------------------------------------------
Version: 1.3.3
Date: 10-05-2026
Fixed:
	- Updater on windows not launching correctly
	- App icon in the exe
	- After n_worker canceled jobs the application would be unable to start other jobs
  - Validation step would be run before installation
----------------------------------------------------------------------------------------------------
Version: 1.3.2
Date: 10-05-2026
Changes:
  - Install from remote ui is less of a mess
  - ChangelogParser is a bit more lenient with the separators, now anything longer than 50 "-" will count
Fixed:
  - Adding repo would fail due to hidden characters
  - Improper changelog divider
  - Repositories are correctly refreshed are start
----------------------------------------------------------------------------------------------------
Version: 1.3.1
Date: 09-05-2026
Added:
	- Checking for active jobs before updating
	- Update file is preserver to avoid downloading it every time
	- Updater now knows the correct installation path on windows
	- Added search popup for installing plugins from remote
Changes:
	- Remove custom downloading update popup
Fixed:
	- Application no longer checks for update every time it's opened when closed to tray
	- Application update progress bar correctly reports progress
	- Updates are now installed correctly (at least on windows)
----------------------------------------------------------------------------------------------------
Version: 1.3.0
Date: 08-05-2026
Added:
  - App logo by [Muh_zakaria](https://www.svgrepo.com/author/muh_zakaria/)
  - Changelog for 1.2.0
  - Manifest Preloading for Repositories
  - Support for **bold**, _italic_, ~strikethrough~, `monospace`, [links](https://www.google.com) in changelog
  - Plugin-Api PluginLoad endpoint executed after setup before validation
  - Plugin-Api required action feedback loop
Changes:
  - Unified changelog parser
  - Migrates plugin-api from java-library to KPM
Removed:
  - changelog in .txt format
Fixes:
  - release.yml no longer add all the tmp files to the release
Planned:
  - switch to proGuard (release distribution) for actual releases, for now it removes too much from the jar to make this viable
----------------------------------------------------------------------------------------------------
Version: 1.2.0
Date: 06-05-2026
Changes:
  - Split ExecutionContext into a PluginContext interface with focused sub-interfaces (PluginLogger, PluginFileSystem, PluginSignalManager, ...).
  - Introduced a sealed class ExecutionResult to replace the exception-based pausing mechanism.
  - Upgraded the KSP processor to generate an action registry and update the host to use PluginAction objects.
  - Decomposing pluginManager into four focused components: PluginRegistry, PluginInstaller, PluginLifecycleManager, and PluginScanner.
  - Ensuring atomicity in the PluginRegistry using a Mutex to protect state updates and persistence.
  - Trying to prevent memory leaks by strengthening the ownership contract in PluginLifecycleManager and ensuring PluginLoader reliably closes classloaders and nullifies references.
  - Decoupling from JobManager how plugin validation and setup jobs are handled.
  - Refactored ManifestProcessor, ManifestJsonGenerator, and KotlinGenerator to replace shortName with qualifiedName.
  - Completely removed ExecutionContext
  - Settings updates are now atomic and thread-safe.
  - Debounced persistence ensures minimal disk overhead.
  - Automated persistence to remove the risk of "forgotten save calls."
----------------------------------------------------------------------------------------------------
Version: 1.1.0
Date: 06-05-2026
Added:
  - Plugin Setting
  - Plugin Actions
  - Error checking while loading plugins
----------------------------------------------------------------------------------------------------
Version: 1.0.0
Date: 05-05-2026
Added:
  - Initial project setup
  - Added unified versioning with BuildKonfig
  - Integrated GitHub Actions for automatic releases
  - Implemented About section with Changelog viewer
  - Prepared unit testing environment with Kotest and MockK
----------------------------------------------------------------------------------------------------
Version: 0.1.0
Date: 01-05-2026
Added:
  - Conceptual prototype
