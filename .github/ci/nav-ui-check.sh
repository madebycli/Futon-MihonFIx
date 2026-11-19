#!/usr/bin/env bash
# Emulator check of the navigation bar (PR #32) and of the kept tabs (PR #31), for the integration test branch only.
# Every step writes a screenshot (and for the tab checks a window/fragment dump) into $1, so the result can be looked
# at afterwards. Steps do not stop the run when they fail; the summary lists what failed.
set -u
OUT=${1:-nav-ui-out}
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

# ---------------------------------------------------------------------------------------------------------------------

log "device: $(adb shell getprop ro.product.model) API $(adb shell getprop ro.build.version.sdk) density $(adb shell wm density)"
adb root > /dev/null; sleep 3; adb wait-for-device
adb shell settings put global hide_error_dialogs 1
APK=$(ls app/build/outputs/apk/debug/*.apk | head -1)
log "installing $APK"
adb install -r -g "$APK" | tee -a "$OUT/run.log"
adb logcat -c

# First start without onboarding, then a history entry with a cover so the continue button has something to show.
launch 10
shot 00_first_start_onboarding
set_prefs onboarding_version=int:1 mode_switcher_spotlight_shown=boolean:true sources_version=int:1
launch 8
shot 00_first_start_main
adb shell am force-stop $PKG
adb pull "$DB" "$OUT/.db" > /dev/null 2>&1
adb pull "$DB-wal" "$OUT/.db-wal" > /dev/null 2>&1
python3 - "$OUT/.db" "$COVER_URL" <<'PY' | tee -a "$OUT/run.log"
import sqlite3, sys, time
db = sqlite3.connect(sys.argv[1])
def insert(table, values):
	cols = db.execute(f'PRAGMA table_info({table})').fetchall()
	names, params = [], []
	for _, name, ctype, notnull, default, pk in cols:
		if name in values:
			names.append(name); params.append(values[name])
		elif notnull and default is None:
			names.append(name); params.append(0 if ctype.upper() in ('INTEGER', 'REAL') else '')
	db.execute(f'INSERT OR REPLACE INTO {table} ({",".join(names)}) VALUES ({",".join("?" * len(names))})', params)
now = int(time.time() * 1000)
titles = ['Navigation check', 'Second manga', 'Third manga'] + [f'Scroll check {n}' for n in range(1, 25)]
for i, title in enumerate(titles):
	insert('manga', {
		'manga_id': 900001 + i, 'title': title, 'url': f'/nav-check/{i}', 'public_url': f'https://example.org/{i}',
		'rating': -1.0, 'nsfw': 0, 'cover_url': sys.argv[2], 'source': 'MANGADEX',
	})
	insert('history', {
		'manga_id': 900001 + i, 'created_at': now - i * 60000, 'updated_at': now - i * 60000, 'chapter_id': 1,
		'page': 0, 'scroll': 0.0, 'percent': 0.3, 'deleted_at': 0, 'chapters': 10,
	})
tables = [r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table'")]
if 'favourite_categories' in tables and 'favourites' in tables:
	for cid, name in ((1, 'Reading'), (2, 'Plan to read'), (3, 'Completed'), (4, 'Dropped')):
		insert('favourite_categories', {
			'category_id': cid, 'created_at': now, 'sort_key': cid, 'title': name, 'order': 'NEWEST',
			'track': 1, 'show_in_lib': 1, 'deleted_at': 0,
		})
	for i in range(len(titles)):
		insert('favourites', {
			'manga_id': 900001 + i, 'category_id': 1 + (i % 2), 'sort_key': i, 'pinned': 0,
			'created_at': now - i * 1000, 'deleted_at': 0,
		})
	print('favourites:', db.execute('SELECT COUNT(*) FROM favourites').fetchone()[0])
if 'sources' in tables:
	db.execute("UPDATE sources SET enabled = 1 WHERE rowid IN (SELECT rowid FROM sources ORDER BY rowid LIMIT 8)")
	print('enabled sources:', db.execute('SELECT COUNT(*) FROM sources WHERE enabled = 1').fetchone()[0])
db.commit()
db.execute('PRAGMA wal_checkpoint(TRUNCATE)')
print('history rows:', db.execute('SELECT COUNT(*) FROM history').fetchone()[0])
db.close()
PY
adb shell rm -f "$DB-wal" "$DB-shm"
adb push "$OUT/.db" "$DB" > /dev/null
fix_owner "$DB"

# --- 1. Before: the bar as it was (docked, no labels by default) -------------------------------------------------------
launch 8
shot 01_docked_default
uidump 01_docked_default

# --- 2. Label choices on the docked bar ------------------------------------------------------------------------------
for mode in ALL SELECTED SELECTED_BESIDE; do
	set_prefs nav_label_mode=string:$mode
	launch 6
	shot "02_docked_labels_$mode"
done

# --- 3. Floating bar: label choices, capsule, full width, height, radius, spacing --------------------------------------
for mode in SELECTED NONE ALL SELECTED_BESIDE; do
	set_prefs nav_floating=boolean:true nav_label_mode=string:$mode nav_capsule=boolean:true
	launch 6
	shot "03_floating_labels_$mode"
done
set_prefs nav_label_mode=string:SELECTED nav_capsule=boolean:false
launch 6
shot 04_floating_no_capsule
set_prefs nav_capsule=boolean:true nav_full_width=boolean:true
launch 6
shot 05_floating_full_width
set_prefs nav_full_width=boolean:false nav_floating_height=int:64 nav_corner_radius=int:12 nav_spacing=int:4
launch 6
shot 06_floating_height64_radius12_spacing4
set_prefs nav_floating_height=int:48 nav_corner_radius=int:28 nav_spacing=int:16 nav_label_mode=string:SELECTED
launch 6
shot 06b_floating_height48_selected
adb shell settings put system font_scale 1.3
launch 6
shot 06c_floating_height48_font130
set_prefs nav_floating=boolean:false nav_label_mode=string:SELECTED nav_height=int:56
launch 6
shot 06d_docked_height56_font130
adb shell settings put system font_scale 1.0
set_prefs nav_floating=boolean:true nav_floating_height=int:52 nav_accent=boolean:true nav_opacity=int:70
launch 6
shot 06e_floating_accent_opacity70
set_prefs nav_floating=boolean:false nav_height=int:64 nav_label_mode=string:NONE
launch 6
shot 06f_docked_accent_opacity70_height64
set_prefs nav_floating=boolean:true nav_accent=boolean:false nav_opacity=int:100 nav_height=remove:x nav_label_mode=string:SELECTED

# --- 4. Continue button: round with the cover, classic, off --------------------------------------------------------------
set_prefs main_fab=boolean:true main_fab_round=boolean:true
launch 12
shot 07_floating_round_cover
uidump 07_floating_round_cover
tap_node "History" && sleep 3 && shot 07b_floating_round_on_history
set_prefs main_fab_round=boolean:false
launch 6
tap_node "History" && sleep 3 && shot 08_floating_classic_fab_on_history
set_prefs main_fab=boolean:false main_fab_round=boolean:true
launch 6
shot 09_floating_fab_off
set_prefs main_fab=boolean:true nav_floating=boolean:false
launch 10
shot 10_docked_round_cover
set_prefs nav_layered_surface=boolean:true
launch 6
shot 11_docked_layered_round
set_prefs nav_layered_surface=boolean:false nav_floating=boolean:true

# --- 5. Pinned navigation with the floating bar and scrolling ------------------------------------------------------------
set_prefs nav_pinned=boolean:true
launch 6
tap_node "History"; sleep 2
adb shell input swipe 540 1600 540 600 400; sleep 1
shot 12_floating_pinned_after_scroll
set_prefs nav_pinned=boolean:false
launch 6
tap_node "History"; sleep 2
adb shell input swipe 540 1600 540 600 400; sleep 1
shot 13_floating_unpinned_after_scroll

# --- 6. Kept tabs: switch back and forth and look at the first frame after the switch -----------------------------------
launch 8
for round in 1 2 3; do
	for tab in History Favorites Explore; do
		tap_node "$tab" || continue
		sleep 0.4
		# No dialog check here: the picture has to be the first moment after the switch.
		adb exec-out screencap -p > "$OUT/14_tabs_r${round}_$tab.png"; log "screenshot 14_tabs_r${round}_$tab"
	done
done
fragments 15_tabs_after_switching
# quick switching
for _ in 1 2 3 4 5; do tap_node History; tap_node Explore; done
sleep 1
shot 16_tabs_after_fast_switching

# --- 7. Rotation and activity recreation keep the tab and the bar ---------------------------------------------------------
tap_node Explore; sleep 2
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 4
shot 17_landscape
fragments 17_landscape
adb shell settings put system user_rotation 0
sleep 4
shot 18_portrait_again
fragments 18_portrait_again

# --- 8. Live change in the settings, without restarting ----------------------------------------------------------------
set_prefs nav_floating=boolean:false nav_label_mode=string:NONE
launch 6
adb shell am start -W -n "$PKG/io.github.landwarderer.futon.settings.SettingsActivity" > /dev/null
sleep 3
shot 19_settings_root
tap_node "Appearance" && sleep 2
scroll_and_tap "Show labels in navigation bar" && sleep 1 && shot 20_label_choice && tap_node "Only the selected section"
sleep 1
scroll_and_tap "Floating navigation bar"
sleep 2
shot 21_settings_floating_on
adb shell input swipe 540 1700 540 1000 300; sleep 1
shot 22_settings_floating_options
uidump 22_settings_floating_options
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 3
shot 23_main_after_live_change
set_prefs nav_floating=boolean:false
adb shell am start -W -n "$PKG/io.github.landwarderer.futon.settings.SettingsActivity" > /dev/null
sleep 2
tap_node "Appearance" && sleep 2
for _ in 1 2 3 4 5 6; do adb shell input swipe 540 1700 540 900 300; sleep 0.5; done
shot 24_settings_docked_options
uidump 24_settings_docked_options

# --- 8b. Changed sections: six sections, and a kept tab whose section was removed --------------------------------------
set_prefs nav_floating=boolean:true nav_label_mode=string:SELECTED
launch 8
tap_node Explore; sleep 1; tap_node History; sleep 1
set_prefs nav_main=string:HISTORY,FAVORITES,LOCAL,UPDATED,BOOKMARKS,SUGGESTIONS
launch 8
shot 24b_floating_six_sections
fragments 24b_six_sections

# --- 8c. Floating top bar: lists scroll behind the search bar and the tabs of Favourites ------------------------------
set_prefs nav_main=string:HISTORY,FAVORITES,EXPLORE,FEED nav_floating=boolean:true top_bar_floating=boolean:false
launch 8
tap_node Favorites; sleep 3
shot 30_top_docked_favourites
set_prefs top_bar_floating=boolean:true
launch 8
shot 31_top_floating_history
adb shell input swipe 540 1500 540 1200 600; sleep 2
shot 32_top_floating_history_scrolled_a_little
adb shell input swipe 540 1700 540 700 500; sleep 2
shot 33_top_floating_history_scrolled
tap_node Favorites; sleep 3
shot 34_top_floating_favourites
adb shell input swipe 540 1500 540 1150 600; sleep 2
shot 35_top_floating_favourites_scrolled_a_little
adb shell input swipe 540 1700 540 700 500; sleep 2
shot 36_top_floating_favourites_scrolled
adb shell input swipe 540 700 540 1300 500; sleep 2
shot 37_top_floating_favourites_back_up
tap_node Explore; sleep 3
adb shell input swipe 540 1500 540 1150 600; sleep 2
shot 38_top_floating_explore_scrolled_a_little
adb shell cmd uimode night yes > /dev/null 2>&1
launch 8
tap_node Favorites; sleep 3
adb shell input swipe 540 1500 540 1150 600; sleep 2
shot 39_top_floating_favourites_dark
adb shell cmd uimode night no > /dev/null 2>&1
set_prefs top_bar_floating=boolean:false
launch 8
tap_node Favorites; sleep 3
shot 40_top_off_favourites_again

# --- 9. Persistence after a restart of the app -----------------------------------------------------------------------
launch 8
shot 25_after_restart
adb shell cat "$PREFS" > "$OUT/final-prefs.xml"

# --- 10. 3-button navigation and a German UI -------------------------------------------------------------------------
set_prefs nav_floating=boolean:true nav_label_mode=string:SELECTED main_fab_round=boolean:true
adb shell cmd overlay enable com.android.internal.systemui.navbar.threebutton > /dev/null 2>&1
sleep 3
launch 10
shot 26_floating_three_button_nav
adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural > /dev/null 2>&1
adb shell cmd locale set-app-locales $PKG --locales de-DE > /dev/null 2>&1 || true
launch 10
shot 27_floating_german
adb shell cmd uimode night yes > /dev/null 2>&1
launch 8
shot 28_floating_dark
adb shell cmd uimode night no > /dev/null 2>&1
launch 8
shot 29_floating_light

adb logcat -d > "$OUT/logcat.txt"
grep -A25 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -B2 -A22 "Process: $PKG" > "$OUT/crashes.txt" || true
if [ -s "$OUT/crashes.txt" ]; then fail "crash in logcat (see crashes.txt)"; fi
log "done; failures: ${#FAILURES[@]}"
for f in "${FAILURES[@]}"; do log " - $f"; done
exit 0
