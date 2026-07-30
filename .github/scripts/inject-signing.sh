#!/bin/bash
set -e
[ -s release.keystore ] || { echo "No keystore, skipping signing injection"; exit 0; }
sed -i '/^    buildTypes {/i\
    signingConfigs {\
        release {\
            storeFile file("../release.keystore")\
            storePassword System.getenv("KEYSTORE_PASSWORD")\
            keyAlias System.getenv("KEY_ALIAS")\
            keyPassword System.getenv("KEY_PASSWORD")\
        }\
    }' app/build.gradle.kts
sed -i '/^                enable = true$/{
    a\            signingConfig signingConfigs.release
}' app/build.gradle.kts
echo "Signing config injected successfully"
