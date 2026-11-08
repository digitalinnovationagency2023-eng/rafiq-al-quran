#!/usr/bin/env bash
# Distribution signing on a macOS runner with fastlane match.
#
# Fetches (or on first use creates) the Apple Distribution certificate and the
# App Store provisioning profile for the bundle id from the private
# ios-certificates repository, installs them in a throw-away keychain, switches
# the Runner target to manual signing with that profile, and writes the
# ExportOptions.plist that `flutter build ipa` needs.
#
# Usage: tool/ios/match_sign.sh <bundle_id>     (run from the Flutter project root)
# Env:   APPLE_TEAM_ID, ASC_KEY_ID, ASC_ISSUER_ID, ASC_KEY_PATH (AuthKey .p8),
#        MATCH_PASSWORD, MATCH_DEPLOY_KEY (private SSH key with write access to the repo)
set -euo pipefail

BUNDLE_ID="$1"
CERTS_REPO="git@github.com:digitalinnovationagency2023-eng/ios-certificates.git"
PROFILE_NAME="match AppStore $BUNDLE_ID"
TMP="${RUNNER_TEMP:-/tmp}"

for v in APPLE_TEAM_ID ASC_KEY_ID ASC_ISSUER_ID ASC_KEY_PATH MATCH_PASSWORD MATCH_DEPLOY_KEY; do
  [ -n "${!v:-}" ] || { echo "::error::$v is not set"; exit 1; }
done

# SSH access to the certificates repository.
mkdir -p ~/.ssh && chmod 700 ~/.ssh
printf '%s\n' "$MATCH_DEPLOY_KEY" > ~/.ssh/ios_certificates_key
chmod 600 ~/.ssh/ios_certificates_key
ssh-keyscan -t ed25519,rsa github.com >> ~/.ssh/known_hosts 2>/dev/null
export GIT_SSH_COMMAND="ssh -i ~/.ssh/ios_certificates_key -o IdentitiesOnly=yes"

# App Store Connect API key in fastlane's JSON form.
jq -n --arg k "$ASC_KEY_ID" --arg i "$ASC_ISSUER_ID" --rawfile p "$ASC_KEY_PATH" \
  '{key_id: $k, issuer_id: $i, key: $p, in_house: false}' > "$TMP/asc_api_key.json"

# Throw-away keychain for the certificate.
KEYCHAIN="$TMP/match.keychain-db"
KEYCHAIN_PW="$(openssl rand -hex 16)"
security create-keychain -p "$KEYCHAIN_PW" "$KEYCHAIN"
security set-keychain-settings -lut 21600 "$KEYCHAIN"
security unlock-keychain -p "$KEYCHAIN_PW" "$KEYCHAIN"
security list-keychains -d user -s "$KEYCHAIN" $(security list-keychains -d user | tr -d '" ')

fastlane match appstore \
  --git_url "$CERTS_REPO" --git_branch main \
  --app_identifier "$BUNDLE_ID" --team_id "$APPLE_TEAM_ID" \
  --api_key_path "$TMP/asc_api_key.json" \
  --keychain_name "$KEYCHAIN" --keychain_password "$KEYCHAIN_PW" \
  --readonly false --skip_docs --verbose false

security find-identity -v -p codesigning "$KEYCHAIN" | grep -q "Apple Distribution" \
  || { echo "::error::no Apple Distribution identity in the keychain after match"; exit 1; }

# Manual signing for the app target only (the test target keeps automatic signing; it is not archived).
python3 - "$BUNDLE_ID" "$PROFILE_NAME" "$APPLE_TEAM_ID" <<'PY'
import re, sys
from pathlib import Path
bundle, profile, team = sys.argv[1:4]
p = Path('ios/Runner.xcodeproj/project.pbxproj')
s = p.read_text()
pattern = re.compile(r'PRODUCT_BUNDLE_IDENTIFIER = ' + re.escape(bundle) + r';')
block = ('PRODUCT_BUNDLE_IDENTIFIER = %s;\n\t\t\t\tCODE_SIGN_STYLE = Manual;\n\t\t\t\tDEVELOPMENT_TEAM = %s;'
         '\n\t\t\t\tPROVISIONING_PROFILE_SPECIFIER = "%s";\n\t\t\t\tCODE_SIGN_IDENTITY = "Apple Distribution";'
         '\n\t\t\t\t"CODE_SIGN_IDENTITY[sdk=iphoneos*]" = "Apple Distribution";') % (bundle, team, profile)
# Drop earlier automatic-signing lines that follow the bundle id, then insert the manual block.
s = re.sub(r'PRODUCT_BUNDLE_IDENTIFIER = ' + re.escape(bundle) + r';(\n\t\t\t\t(DEVELOPMENT_TEAM|CODE_SIGN_STYLE) = [^;]+;)*', block, s)
p.write_text(s)
print(f"manual signing set for {bundle} with profile '{profile}' ({len(pattern.findall(s))} configurations)")
PY

mkdir -p build/ios
cat > build/ios/ExportOptions.plist <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>method</key><string>app-store-connect</string>
  <key>destination</key><string>export</string>
  <key>signingStyle</key><string>manual</string>
  <key>teamID</key><string>$APPLE_TEAM_ID</string>
  <key>signingCertificate</key><string>Apple Distribution</string>
  <key>provisioningProfiles</key><dict>
    <key>$BUNDLE_ID</key><string>$PROFILE_NAME</string>
  </dict>
  <key>uploadSymbols</key><true/>
  <key>manageAppVersionAndBuildNumber</key><false/>
</dict></plist>
EOF
echo "Signing ready: $PROFILE_NAME"
