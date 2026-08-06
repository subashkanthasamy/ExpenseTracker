#!/usr/bin/env bash
#
# Writes a GitHub Actions secret to a file, tolerating how the secret was actually stored.
#
# `base64 --decode` exits with "invalid input" on anything that is not valid base64. The two
# ways that happens are both easy mistakes and neither is diagnosable from the log, because
# Actions masks the value:
#
#   1. the secret holds the raw file contents instead of base64 of them
#   2. the base64 is correct but carries CRLF or 76-column line wrapping, picked up from
#      `base64` without `-w 0` or from a paste through the GitHub web UI
#
# So: strip the whitespace, and if the result still is not the file we expect, fall back to
# treating the secret as the verbatim contents. Base64's alphabet is [A-Za-z0-9+/=], so a
# leading `{` or `<` proves the value is not base64 and the test cannot go the wrong way.
#
# Usage:  ci-decode-secret.sh <destination> <json|xml|binary>   # value on stdin
#
set -uo pipefail

dest=$1
kind=$2
raw=$(mktemp)
decoded=$(mktemp)
trap 'rm -f "$raw" "$decoded"' EXIT

cat > "$raw"

if [ ! -s "$raw" ]; then
  echo "::error::Secret for $dest is empty or not set."
  exit 1
fi

case $kind in
  json)   usable() { head -c 1 "$1" | grep -q '{'; } ;;
  xml)    usable() { head -c 8 "$1" | grep -qa -E '<\?xml|bplist'; } ;;
  # A keystore cannot survive being stored as text, so it must be base64. Check the
  # container magic: 0xFEEDFEED for JKS, 0x3082 (DER SEQUENCE) for PKCS#12, which is what
  # keytool produces by default now. `od` pads each byte field to three columns, so strip
  # all whitespace before matching rather than writing the spaces into the pattern.
  binary) usable() { [ "$(head -c 2 "$1" | od -An -tx1 | tr -d ' \n')" = feed ] ||
                     [ "$(head -c 2 "$1" | od -An -tx1 | tr -d ' \n')" = 3082 ]; } ;;
  *)      echo "::error::Unknown kind '$kind'"; exit 1 ;;
esac

if tr -d '\r\n \t' < "$raw" | base64 --decode > "$decoded" 2>/dev/null && usable "$decoded"; then
  cp "$decoded" "$dest"
  echo "$dest <- decoded from base64 ($(wc -c < "$dest" | tr -d ' ') bytes)"
elif [ "$kind" != binary ] && usable "$raw"; then
  cp "$raw" "$dest"
  echo "$dest <- secret was stored verbatim, not base64; used as-is ($(wc -c < "$dest" | tr -d ' ') bytes)"
else
  echo "::error::Secret for $dest is neither valid base64 nor a usable $kind file. Re-set it with: base64 -i <file> | tr -d '\\n' | gh secret set <SECRET_NAME>"
  exit 1
fi

# Catch a value that decoded cleanly but was truncated on the way into the secret.
if [ "$kind" = json ] && command -v python3 > /dev/null; then
  python3 -c "import json,sys; json.load(open(sys.argv[1]))" "$dest" || {
    echo "::error::$dest decoded but is not valid JSON — the secret is probably truncated."
    exit 1
  }
fi
