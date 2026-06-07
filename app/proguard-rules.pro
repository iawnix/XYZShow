# Keep the app's own classes — they're referenced from manifest, layouts, and Java code.
-keep class io.iaw.molview.MainActivity { *; }
-keep class io.iaw.molview.DetailsActivity { *; }
-keep class io.iaw.molview.SourceActivity { *; }

# MoleculeView is added programmatically; keep enough so reflection in GLSurfaceView works.
-keep class io.iaw.molview.MoleculeView { *; }
