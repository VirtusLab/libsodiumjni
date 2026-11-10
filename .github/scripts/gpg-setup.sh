#!/usr/bin/env sh

# Imports the release signing key, from the base64-encoded PGP_SECRET environment variable
# (adapted from https://github.com/coursier/apps/blob/f1d2bf568bf466a98569a85c3f23c5f3a8eb5360/.github/scripts/gpg-setup.sh)

set -eu

# quoted, so that line breaks in the base64 value (as GNU base64 adds by default) are kept
printf '%s\n' "$PGP_SECRET" | base64 --decode | gpg --import --no-tty --batch --yes

if ! gpg --list-secret-keys --with-colons | grep -q '^sec'; then
  echo "No secret key imported from PGP_SECRET" 1>&2
  exit 1
fi

echo "allow-loopback-pinentry" >>~/.gnupg/gpg-agent.conf
echo "pinentry-mode loopback" >>~/.gnupg/gpg.conf

gpg-connect-agent reloadagent /bye
