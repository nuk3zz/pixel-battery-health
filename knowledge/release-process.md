# Release Process

1. Update `versionCode`, `versionName`, README, and release notes.
2. Run parser unit tests and build the debug APK using Android Studio's bundled JDK 17.
3. Confirm the manifest does not request internet permission.
4. Rename the installable APK with the application version.
5. Commit and push the reviewed changes.
6. Publish a GitHub release and attach the APK.
7. Verify the release asset is downloadable.
