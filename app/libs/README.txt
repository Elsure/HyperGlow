Put XposedBridgeApi-82.jar in this folder (app/libs) before building.

It is referenced as compileOnly in app/build.gradle.kts and is NOT packaged into the APK;
LSPosed provides the Xposed API at runtime.

Source: rovo89/XposedBridge artifacts, or any mirror of de.robv.android.xposed:api:82.