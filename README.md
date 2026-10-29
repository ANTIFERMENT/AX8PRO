# AX 8 PRO · OTA

Official Android control center for OLAX AX8 Pro. Design language follows QR LINK; source project keeps router-control functions.

## Initial one-time activation

1. Add the `AX8PRO_SIGNING_BUNDLE` repository secret from the provided separate, private text file. Keep the secret out of public commits.
2. Upload `AX8PRO_SOURCE.zip` to the repository root on `main` using **Add file → Upload files**. GitHub Actions will automatically import, compile, verify, sign, and publish the first release.
3. Download `AX8PRO.apk` from Releases and install it. Future releases update from **Settings → OTA** in the app.

Do not publish the signing key or the private signing bundle. The APK package is `ru.xarkon.ax8pro`, separate from older `ru.xarkon.ax8` builds because the signing identity differs.

**OTA endpoint:** https://github.com/ANTIFERMENT/AX8PRO/releases/latest/download/update.json

**APK endpoint:** https://github.com/ANTIFERMENT/AX8PRO/releases/latest/download/AX8PRO.apk

**Status:** Initial source ZIP and secret required before the first release.
