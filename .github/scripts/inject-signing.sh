#!/bin/bash
set -e
# Gracefully exit if no keystore (not provided or empty)
[ -s release.keystore ] || { echo "No keystore, skipping signing injection"; exit 0; }

# Inject signingConfigs block before buildTypes {
# We use temporary files to avoid sed -i compatibility issues on macOS
TMP=$(mktemp)
sed '/^    buildTypes {/i\
    signingConfigs {\
        release {\
            storeFile file("../release.keystore")\
            storePassword System.getenv("KEYSTORE_PASSWORD")\
            keyAlias System.getenv("KEY_ALIAS")\
            keyPassword System.getenv("KEY_PASSWORD")\
        }\
    }' app/build.gradle.kts > "$TMP" && mv "$TMP" app/build.gradle.kts

# Inject signingConfig into release buildType after optimization block
TMP=$(mktemp)
sed '/^                enable = false$/{
    a\            signingConfig signingConfigs.release
}' app/build.gradle.kts > "$TMP" && mv "$TMP" app/build.gradle.kts

echo "Signing config injected successfully"
