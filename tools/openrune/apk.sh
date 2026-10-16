#!/bin/sh
# OpenRune spike: builds the app retargeted at OpenRune into re/openrune/.spike/apk/{base,split-port-al}.apk
# (LOCAL_AUTH, jav_config from the sidecar on :8081, OpenRune's RSA modulus, game port 40255).
# Install: adb install-multiple -r base.apk split-port-al.apk (needs the device lock).
set -eu
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
A="$ROOT/re/openrune/.spike/apk"
MOD=$(openssl rsa -in "$ROOT/re/openrune/.data/game.key" -noout -modulus | cut -d= -f2 | tr A-F a-f)
LOCAL_AUTH=1 SERVER=10.0.2.2:8081 MODULUS=$MOD "$ROOT/tools/repack.sh"
mkdir -p "$A" && cp "$ROOT/re/out/base.apk" "$ROOT/re/out/split.apk" "$A/"
rm -rf "$A/x" && mkdir "$A/x" && cd "$A/x"
L=lib/arm64-v8a/liblibs.hal.system.osclient.so
unzip -q ../split.apk "$L"
python3 -I "$ROOT/tools/openrune/patch_port.py" "$L" "$L.new" 40255 && mv "$L.new" "$L"
cp ../split.apk ../split-port.apk && zip -q -d ../split-port.apk 'META-INF/*' && zip -q -0 ../split-port.apk "$L"
cd "$A" && zipalign -p -f 4 split-port.apk split-port-al.apk
apksigner sign --ks "$ROOT/re/debug.keystore" --ks-pass pass:android split-port-al.apk
echo "built $A/base.apk $A/split-port-al.apk"
