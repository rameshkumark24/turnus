#!/usr/bin/env bash
# Checks the drafted listing against Play's field limits.
#
# The counts in STORE-LISTING.md are claims, and a listing rejected at upload
# for being one character over is a silly way to lose an afternoon. Run this
# after any edit to the copy.
set -u
doc="$(dirname "$0")/STORE-LISTING.md"

# Pulls the Nth fenced block out of the document.
block() { awk -v n="$1" '/^```$/{c++; next} c==2*n-1' "$doc"; }

check() {
  local name="$1" limit="$2" text="$3"
  # Play counts characters, and a trailing newline is not one of them.
  local n
  n=$(printf '%s' "$text" | tr -d '\n' | wc -m | tr -d ' ')
  if [ "$n" -le "$limit" ]; then
    printf 'PASS  %-18s %4s / %s\n' "$name" "$n" "$limit"
  else
    printf 'FAIL  %-18s %4s / %s  (%s over)\n' "$name" "$n" "$limit" "$((n - limit))"
    return 1
  fi
}

fail=0
check "app name"          30   "$(block 1)" || fail=1
check "short description" 80   "$(block 2)" || fail=1
# The full description keeps its line breaks; Play counts those too.
long="$(block 3)"
n=$(printf '%s' "$long" | wc -m | tr -d ' ')
if [ "$n" -le 4000 ]; then
  printf 'PASS  %-18s %4s / 4000\n' "full description" "$n"
else
  printf 'FAIL  %-18s %4s / 4000  (%s over)\n' "full description" "$n" "$((n - 4000))"; fail=1
fi

# The privacy policy. A 404 here is a rejection, and the app now links to it
# from Settings as well, so there are two places it has to be right.
echo
echo "Privacy policy:"
src="$(dirname "$0")/../app/src/main/kotlin/com/turnus/rota/ui/settings/SettingsScreen.kt"
url=$(sed -n 's/^const val PRIVACY_POLICY_URL = "\(.*\)"$/\1/p' "$src")
if [ -z "$url" ]; then
  printf 'FAIL  no PRIVACY_POLICY_URL found in SettingsScreen.kt\n'; fail=1
else
  printf '      in-app link: %s\n' "$url"
  # --max-time so a hung host cannot hang the check; -L because Pages redirects.
  code=$(curl -sL -o /dev/null -w '%{http_code}' --max-time 20 "$url" || echo "000")
  if [ "$code" = "200" ]; then
    printf 'PASS  policy reachable        %s\n' "$code"
  else
    printf 'FAIL  policy NOT reachable    %s  <- Play rejects this\n' "$code"; fail=1
  fi
fi

echo
echo "Claims that must not appear (see STORE-LISTING.md section 7):"
for banned in "pay" "salary" "earnings" "scan" "sync"; do
  if printf '%s' "$long" | grep -qiw "$banned"; then
    # "does not calculate pay" is the honest mention and is allowed.
    printf '  note  "%s" appears — check it is the honest mention, not a claim\n' "$banned"
  fi
done
exit $fail
