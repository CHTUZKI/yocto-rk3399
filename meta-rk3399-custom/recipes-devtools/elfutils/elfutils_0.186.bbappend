# Fix dwarfsrcfiles error with static library files
# The error occurs because splitstaticdebuginfo() calls source_info() which tries to process .a files
# Workaround: Disable debug source file extraction by setting PACKAGE_DEBUG_SPLIT_STYLE to debug-without-src
# This prevents source_info from being called, avoiding the error with static libraries

# Disable debug source file processing for this package
# This sets srcdir to empty, which prevents source_info from being called
PACKAGE_DEBUG_SPLIT_STYLE = "debug-without-src"
