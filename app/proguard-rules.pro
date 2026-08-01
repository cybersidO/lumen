# R8 rules. Kept minimal on purpose — rules added "just in case" hide real problems.
# M9 verifies the release build by actually opening a book, because Readium's and Room's
# reflection breakage only shows up there.

# Kotlin metadata is needed by reflection-based libraries (Hilt, later Room/Readium).
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions

# Coroutines: R8 already ships rules for these via the library's consumer rules.
# Hilt and DataStore likewise. Nothing hand-written is needed yet.
