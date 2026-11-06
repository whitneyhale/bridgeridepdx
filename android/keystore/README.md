# Test signing key

`test.jks` signs pre-alpha builds so each one installs over the last. Passwords are `bridgeride-test` (store and key), alias `bridgeride-test`. They are public on purpose: this key proves nothing.

Never use it for the Play Store or for builds other people install. Before that, create a private key, store it in GitHub secrets, and change `android/app/build.gradle.kts`. Changing the key means uninstalling the test build first, which deletes rides on the phone: export them before.
