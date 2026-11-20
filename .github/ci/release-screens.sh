#!/usr/bin/env bash
# Test-only: screenshots for the release notes. Dark theme, clean status bar (demo mode), floating bars, neutral
# sample titles with generated covers (.github/ci/release-covers), no sources shown.
set -u
OUT=${1:-release-screens}
mkdir -p "$OUT"
PKG=io.github.landwarderer.futon.debug
ACTIVITY=$PKG/io.github.landwarderer.futon.main.ui.MainActivity
PREFS=/data/data/$PKG/shared_prefs/${PKG}_preferences.xml
DB=/data/data/$PKG/databases/futon-db
COVER_URL=https://raw.githubusercontent.com/madebycli/Clio/devel/metadata/en-US/images/phoneScreenshots/1.png
FAILURES=()
log() { echo "[$(date +%T)] $*" | tee -a "$OUT/run.log"; }
fail() { log "FAIL: $*"; FAILURES+=("$*"); }

# The CI emulator sometimes shows "<app> isn't responding" for a system app; such a dialog covers the screen and
# swallows the taps, so it is answered with "Wait" before every screenshot and tap.
dismiss_system_dialogs() {
	adb shell uiautomator dump /sdcard/ui-dlg.xml > /dev/null 2>&1
	adb pull /sdcard/ui-dlg.xml "$OUT/.dlg.xml" > /dev/null 2>&1 || return 0
	if grep -q 'text="No thanks"' "$OUT/.dlg.xml"; then
		# The one-time question of Explore about suggestions covers the bar.
		log "answering the suggestions question with No thanks"
		TAP_NO_DIALOG_CHECK=1 tap_node "No thanks"
		sleep 1
	fi
	if grep -q "isn&apos;t responding\|isn't responding\|keeps stopping" "$OUT/.dlg.xml"; then
		log "system dialog on screen: $(grep -o 'text="[^"]*responding[^"]*"\|text="[^"]*stopping[^"]*"' "$OUT/.dlg.xml" | head -1)"
		local xy
		xy=$(python3 - "$OUT/.dlg.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for n in root.iter('node'):
	if n.get('text') in ('Wait', 'Close app'):
		x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
		print((x1 + x2) // 2, (y1 + y2) // 2)
		break
PY
)
		[ -n "$xy" ] && adb shell input tap $xy
		sleep 1
	fi
}
shot() { dismiss_system_dialogs; adb exec-out screencap -p > "$OUT/$1.png" && log "screenshot $1"; }
uidump() {
	adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
	adb pull /sdcard/ui.xml "$OUT/$1.xml" > /dev/null 2>&1
}
fragments() { adb shell dumpsys activity "$ACTIVITY" > "$OUT/$1.dumpsys.txt" 2>&1; }

# Taps the first node whose content-desc or text is $1 (exactly). Returns 1 when there is none.
declare -A TAP_CACHE
tap_node() {
	[ -z "${TAP_NO_DIALOG_CHECK:-}" ] && dismiss_system_dialogs
	adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
	adb pull /sdcard/ui.xml "$OUT/.last-ui.xml" > /dev/null 2>&1
	local xy
	xy=$(python3 - "$OUT/.last-ui.xml" "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
try:
	root = ET.parse(sys.argv[1]).getroot()
except Exception:
	sys.exit(0)
for n in root.iter('node'):
	if sys.argv[2] in (n.get('content-desc'), n.get('text')):
		x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
		print((x1 + x2) // 2, (y1 + y2) // 2)
		break
PY
)
	if [ -z "$xy" ] && [ -n "${TAP_CACHE[$1]:-}" ]; then
		# uiautomator cannot dump while something animates; the bar has not moved, so the last position is used.
		log "no dump for '$1', tapping its last position"
		xy=${TAP_CACHE[$1]}
	fi
	if [ -z "$xy" ]; then
		fail "no node '$1' on screen"
		return 1
	fi
	TAP_CACHE[$1]=$xy
	adb shell input tap $xy
}

# Scrolls the settings list until a node with text $1 is visible, then taps it.
scroll_and_tap() {
	for _ in $(seq 1 12); do
		adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
		adb pull /sdcard/ui.xml "$OUT/.last-ui.xml" > /dev/null 2>&1
		if grep -q "text=\"$1\"" "$OUT/.last-ui.xml"; then
			tap_node "$1"
			return $?
		fi
		adb shell input swipe 540 1700 540 900 300
		sleep 1
	done
	fail "could not find '$1' in the settings"
	return 1
}

# set_prefs key=bool:true key=int:52 key=string:SELECTED ... (the app must be stopped)
set_prefs() {
	adb shell am force-stop $PKG
	adb pull "$PREFS" "$OUT/.prefs.xml" > /dev/null 2>&1 || echo '<?xml version="1.0" encoding="utf-8" standalone="yes" ?><map></map>' > "$OUT/.prefs.xml"
	python3 - "$OUT/.prefs.xml" "$@" <<'PY'
import sys, xml.etree.ElementTree as ET
path = sys.argv[1]
tree = ET.parse(path)
root = tree.getroot()
for arg in sys.argv[2:]:
	key, spec = arg.split('=', 1)
	kind, value = spec.split(':', 1)
	for el in list(root):
		if el.get('name') == key:
			root.remove(el)
	if kind == 'remove':
		continue
	if kind == 'string':
		el = ET.SubElement(root, 'string', name=key)
		el.text = value
	else:
		ET.SubElement(root, kind, name=key, value=value)
tree.write(path, encoding='utf-8', xml_declaration=True)
PY
	adb push "$OUT/.prefs.xml" "$PREFS" > /dev/null
	fix_owner "$PREFS"
}

fix_owner() {
	local owner
	owner=$(adb shell stat -c %u:%g /data/data/$PKG)
	adb shell chown "$owner" "$1"
	adb shell restorecon "$1" > /dev/null 2>&1 || true
}

launch() {
	adb shell am force-stop $PKG
	adb shell am start -W -n "$ACTIVITY" > /dev/null
	sleep "${1:-6}"
}


COVERS=/data/data/$PKG/files/release-covers
log "device: $(adb shell getprop ro.product.model) API $(adb shell getprop ro.build.version.sdk)"
adb root > /dev/null; sleep 3; adb wait-for-device
adb shell settings put global hide_error_dialogs 1
APK=$(ls app/build/outputs/apk/debug/*.apk | head -1)
adb install -r -g "$APK" | tee -a "$OUT/run.log"
adb shell cmd uimode night yes > /dev/null 2>&1
launch 10
set_prefs onboarding_version=int:1 mode_switcher_spotlight_shown=boolean:true sources_version=int:1
launch 8
adb shell am force-stop $PKG
adb shell mkdir -p $COVERS
for f in .github/ci/release-covers/*.png; do adb push "$f" $COVERS/ > /dev/null; done
OWNER=$(adb shell stat -c %u:%g /data/data/$PKG | tr -d '\r')
adb shell chown -R "$OWNER" /data/data/$PKG/files
adb shell restorecon -R /data/data/$PKG/files > /dev/null 2>&1 || true
adb pull "$DB" "$OUT/.db" > /dev/null 2>&1
adb pull "$DB-wal" "$OUT/.db-wal" > /dev/null 2>&1
python3 - "$OUT/.db" "$COVERS" <<'PY' 2>&1 | tee -a "$OUT/run.log"
import sqlite3, sys, time
db = sqlite3.connect(sys.argv[1])
covers = sys.argv[2]
def insert(table, values):
	cols = db.execute(f'PRAGMA table_info({table})').fetchall()
	names, params = [], []
	for _, name, ctype, notnull, default, pk in cols:
		if name in values:
			names.append(name); params.append(values[name])
		elif notnull and default is None:
			names.append(name); params.append(0 if ctype.upper() in ('INTEGER', 'REAL') else '')
	quoted = ",".join(f'"{n}"' for n in names)
	db.execute(f'INSERT OR REPLACE INTO "{table}" ({quoted}) VALUES ({",".join("?" * len(names))})', params)
now = int(time.time() * 1000)
titles = ['Northern Lights', 'The Quiet Harbor', 'Ember Road', 'Paper Moons', 'Glass Garden', 'Tidewalker',
	'Silver Orchard', 'Lantern Street', 'Hollow Peak', 'Saltwind', 'Echo Valley', 'Starling Hours',
	'Midnight Atlas', 'Copper Sky', 'Willow Gate', 'Blue Hour']
percents = [0.42, 0.87, 0.15, 0.64, 1.0, 0.33, 0.71, 0.08, 0.55, 0.92, 0.27, 0.6, 0.12, 0.78, 0.45, 0.2]
for i, title in enumerate(titles):
	insert('manga', {
		'manga_id': 700001 + i, 'title': title, 'url': f'/sample/{i}', 'public_url': f'https://example.org/{i}',
		'rating': -1.0, 'nsfw': 0, 'cover_url': f'file://{covers}/{i + 1:02d}.png', 'source': 'MANGADEX',
	})
	insert('history', {
		'manga_id': 700001 + i, 'created_at': now - i * 3600000 * 5, 'updated_at': now - i * 3600000 * 5,
		'chapter_id': 1, 'page': 0, 'scroll': 0.0, 'percent': percents[i], 'deleted_at': 0, 'chapters': 40,
	})
tables = [r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table'")]
for cid, name in ((1, 'Reading'), (2, 'Plan to read'), (3, 'Completed'), (4, 'Favourites')):
	insert('favourite_categories', {
		'category_id': cid, 'created_at': now, 'sort_key': cid, 'title': name, 'order': 'NEWEST',
		'track': 1, 'show_in_lib': 1, 'deleted_at': 0,
	})
for i in range(len(titles)):
	insert('favourites', {
		'manga_id': 700001 + i, 'category_id': 1 + (i % 3), 'sort_key': i, 'pinned': 0,
		'created_at': now - i * 1000, 'deleted_at': 0,
	})
db.commit()
db.execute('PRAGMA wal_checkpoint(TRUNCATE)')
db.close()
print('seeded')
PY
adb shell rm -f "$DB-wal" "$DB-shm"
adb push "$OUT/.db" "$DB" > /dev/null
fix_owner "$DB"

# A clean status bar.
adb shell settings put global sysui_demo_allowed 1
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" > /dev/null; }
demo enter
demo clock -e hhmm 1000
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e fully true
demo network -e mobile hide
demo notifications -e visible false

set_prefs nav_main=string:HISTORY,FAVORITES,EXPLORE,FEED nav_floating=boolean:true top_bar_floating=boolean:true \
	nav_label_mode=string:SELECTED main_fab_round=boolean:true nav_pinned=boolean:false
launch 8
demo enter
demo clock -e hhmm 1000
demo notifications -e visible false
sleep 3
shot 01_history
adb shell input swipe 540 1600 540 1380 600; sleep 2
shot 02_history_scrolled
tap_node Favorites; sleep 3
shot 03_favourites
adb shell input swipe 540 1600 540 1400 600; sleep 2
shot 04_favourites_scrolled
adb shell input swipe 540 1700 540 900 500; sleep 2
shot 05_favourites_bar_hidden
# The settings of the bars.
adb shell am start -W -n "$PKG/io.github.landwarderer.futon.settings.SettingsActivity" > /dev/null
sleep 3
tap_node "Appearance" && sleep 2
shot 06_settings_appearance
for _ in 1 2 3; do
	adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb pull /sdcard/ui.xml "$OUT/.s.xml" > /dev/null 2>&1
	grep -q 'text="Floating navigation bar"' "$OUT/.s.xml" && break
	adb shell input swipe 540 1700 540 1000 400; sleep 1
done
shot 07_settings_bars
adb shell input swipe 540 1700 540 1200 400; sleep 1
shot 08_settings_bars_more
for n in 09 10 11 12; do
	adb shell input swipe 540 1700 540 1100 400; sleep 1
	shot "${n}_settings_further"
done
log "done"
