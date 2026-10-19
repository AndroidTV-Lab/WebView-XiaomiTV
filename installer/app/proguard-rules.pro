# Release builds run R8 (minifyEnabled + shrinkResources). Compose, AndroidX Lifecycle and
# kotlinx.coroutines ship their own consumer rules, and this app uses no reflection, so no
# additional keep rules are required. Add keeps here if a future feature relies on reflection.
