Version: 1.3.0
Date: 06-05-2026
Fixes:
  - release.yml no longer add all the tmp files to the release
  - added changelog for 1.2.0
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
