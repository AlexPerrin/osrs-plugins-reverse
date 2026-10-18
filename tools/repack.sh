#!/usr/bin/env bash
# Rebuilds the official OSRS split APKs with libosrsrl.so injected.
#   tools/repack.sh            -> re/out/{base,split}.apk, signed with re/debug.keystore
#   tools/repack.sh --install  -> also replaces the app on the connected device
#   SERVER=10.0.2.2:8080 tools/repack.sh
#                              -> also points the game at the test server in server/ (config URLs +
#                                 login RSA modulus from server/data/rsa.properties, or $MODULUS)
#   LOCAL_AUTH=1 SERVER=... tools/repack.sh
#                              -> also lets the app sign in against the server's local OpenID
#                                 provider (server LOCAL_AUTH=true) instead of a Jagex account
#   GAME_PORT=40255 SERVER=... tools/repack.sh
#                              -> also patches the game port the client connects to (it ignores
#                                 jav_config for that; tools/openrune/patch_port.py)
#   OPENRUNE=1 LOCAL_AUTH=1 tools/repack.sh
#                              -> the build for OpenRune (openrune/): SERVER=10.0.2.2:8080 (its
#                                 jav_config sidecar), GAME_PORT=43594 and the modulus of
#                                 openrune/src/.data/game.key, unless set explicitly
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
RE=$ROOT/re
OUT=$RE/out
APKTOOL="java -jar $HOME/tools/apktool.jar"
HOOK_SO=$RE/build/libosrsrl.so
KEYSTORE=$RE/debug.keystore
KS_PASS=android

[[ -f $HOOK_SO ]] || { echo "build native/ first (missing $HOOK_SO)"; exit 1; }

if [[ -n ${OPENRUNE:-} ]]; then
    SERVER=${SERVER:-10.0.2.2:8080}
    GAME_PORT=${GAME_PORT:-43594}
    MODULUS=${MODULUS:-$("$ROOT/openrune/scripts/openrune.sh" modulus)}
fi
mkdir -p "$OUT"

# 1. Decode classes and resources (the manifest must be text for the telemetry switches; LOCAL_AUTH
#    also edits the network security config).
rm -rf "$RE/apk/base"
$APKTOOL d -q -f "$ROOT/base.apk" -o "$RE/apk/base"
if [[ -n ${LOCAL_AUTH:-} ]]; then
    python3 -I "$ROOT/tools/patch_local_auth.py" "$RE/apk/base"
fi
# 1b. Telemetry off, every build (docs/app-telemetry.md): Singular never starts, Firebase Analytics
#     deactivated, Crashlytics collection off.
python3 -I "$ROOT/tools/patch_telemetry.py" "$RE/apk/base"

# 2. Load our library right after the game's.
SMALI=$(grep -rl 'const-string [vp][0-9]*, "libs.hal.system.osclient"' "$RE/apk/base"/smali*/com/jagex/android/)
python3 -I - "$SMALI" <<'EOF'
import re, sys
path = sys.argv[1]
src = open(path).read()
pat = re.compile(r'(const-string ([vp]\d+), "libs\.hal\.system\.osclient"\n(?:\s*(?:\.line \d+)?\n)*\s*'
                 r'invoke-static \{\2\}, Ljava/lang/System;->loadLibrary\(Ljava/lang/String;\)V\n)')
m = pat.search(src)
if not m: sys.exit("loadLibrary call not found in " + path)
reg = m.group(2)
inject = (f'\n    const-string {reg}, "osrsrl"\n\n'
          f'    invoke-static {{{reg}}}, Ljava/lang/System;->loadLibrary(Ljava/lang/String;)V\n')
open(path, "w").write(src[:m.end()] + inject + src[m.end():])
print("patched", path)
EOF
$APKTOOL b -q "$RE/apk/base" -o "$OUT/base-unsigned.apk"

# 3. Optionally retarget the game library at the test server.
GAME_SO=""
if [[ -n ${SERVER:-} ]]; then
    MODULUS=${MODULUS:-$(grep '^modulus=' "$ROOT/server/data/rsa.properties" | cut -d= -f2)}
    GAME_SO=$OUT/liblibs.hal.system.osclient.so
    unzip -p "$ROOT/split_config.arm64_v8a.apk" lib/arm64-v8a/liblibs.hal.system.osclient.so > "$GAME_SO.orig"
    python3 -I "$ROOT/tools/patch_server.py" "$GAME_SO.orig" "$GAME_SO" --server "$SERVER" --modulus "$MODULUS"
    rm "$GAME_SO.orig"
    if [[ -n ${GAME_PORT:-} ]]; then
        python3 -I "$ROOT/tools/openrune/patch_port.py" "$GAME_SO" "$GAME_SO.port" "$GAME_PORT"
        mv "$GAME_SO.port" "$GAME_SO"
    fi
fi

# 4. Add the hook library (and patched game library) to the ABI split, dropping the old signature.
python3 -I - "$ROOT/split_config.arm64_v8a.apk" "$HOOK_SO" "$OUT/split-unsigned.apk" "$GAME_SO" <<'EOF'
import sys, zipfile
src, so, dst, game_so = sys.argv[1:]
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w") as zout:
    for item in zin.infolist():
        if item.filename.startswith("META-INF/"): continue
        data = zin.read(item.filename)
        if game_so and item.filename == "lib/arm64-v8a/liblibs.hal.system.osclient.so":
            data = open(game_so, "rb").read()
        zout.writestr(item, data)
    zout.write(so, "lib/arm64-v8a/libosrsrl.so", compress_type=zipfile.ZIP_DEFLATED)
EOF

# 5. Align + sign with one key (all splits must share a signer).
if [[ ! -f $KEYSTORE ]]; then
    keytool -genkeypair -keystore "$KEYSTORE" -storepass $KS_PASS -keypass $KS_PASS -alias osrsrl \
        -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=osrsrl" >/dev/null
fi
for name in base split; do
    zipalign -p -f 4 "$OUT/$name-unsigned.apk" "$OUT/$name.apk"
    apksigner sign --ks "$KEYSTORE" --ks-pass pass:$KS_PASS "$OUT/$name.apk"
    rm "$OUT/$name-unsigned.apk"
done
echo "built $OUT/base.apk $OUT/split.apk"

if [[ ${1:-} == --install ]]; then
    # Under WSL the emulator is only visible to Windows adb: ADB=/mnt/c/.../adb.exe
    ADB=${ADB:-adb}
    SERIAL_ARGS=(); [[ -n ${ANDROID_SERIAL:-} ]] && SERIAL_ARGS=(-s "$ANDROID_SERIAL")
    apk_path() { [[ $ADB == *.exe ]] && wslpath -w "$1" || echo "$1"; }
    # Update in place so app data (the Jagex sign-in) survives. Only when the installed app has a
    # different signer (e.g. the Play build) does it have to be uninstalled first.
    install() { "$ADB" "${SERIAL_ARGS[@]}" install-multiple -r "$(apk_path "$OUT/base.apk")" "$(apk_path "$OUT/split.apk")"; }
    if ! out=$(install 2>&1); then
        echo "$out"
        if [[ $out == *INSTALL_FAILED_UPDATE_INCOMPATIBLE* ]]; then
            echo "signer differs: uninstalling the installed app (this signs it out)"
            "$ADB" "${SERIAL_ARGS[@]}" uninstall com.jagex.oldscape.android
            install
        else
            exit 1
        fi
    else
        echo "$out"
    fi
fi
