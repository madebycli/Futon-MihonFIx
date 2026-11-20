#!/usr/bin/env bash
# XXL navigation matrix (test-only): for many combinations of the bar settings, gestures on every tab and geometry
# checks after each of them (nav_geometry.py). Writes report.txt, run.log and a screenshot plus UI dump of every
# failure into $1. Base settings of the job in $2 (e.g. "nav_floating=boolean:true top_bar_floating=boolean:false").
set -u
OUT=${1:-nav-matrix-out}
JOB_PREFS=${2:-}
mkdir -p "$OUT"
GEOM=.github/ci/nav_geometry.py
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

CHECKS=0
WARNINGS=0
FAIL_SHOTS=0
COMBO=""
TAB=""
CTX="$OUT/.ctx.json"

dump_ui() {
	for _ in 1 2 3; do
		rm -f "$OUT/.cur.xml"
		# Without this a failed dump would leave the previous file on the device, and an old screen would be checked.
		adb shell rm -f /sdcard/ui.xml
		adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
		adb pull /sdcard/ui.xml "$OUT/.cur.xml" > /dev/null 2>&1
		if [ -s "$OUT/.cur.xml" ] && grep -q "<hierarchy" "$OUT/.cur.xml"; then return 0; fi
		sleep 1
	done
	return 1
}

record_failure() {
	fail "$COMBO | $TAB | $1"
	echo "$COMBO | $TAB | $1" >> "$OUT/report.txt"
	if (( FAIL_SHOTS < 60 )); then
		FAIL_SHOTS=$((FAIL_SHOTS + 1))
		adb exec-out screencap -p > "$OUT/fail-$FAIL_SHOTS.png"
		[ -s "$OUT/.cur.xml" ] && cp "$OUT/.cur.xml" "$OUT/fail-$FAIL_SHOTS.xml"
		echo "   -> fail-$FAIL_SHOTS.png" >> "$OUT/report.txt"
	fi
}

# check <label> <checks...>: the screen as it is now.
check() {
	local label=$1; shift
	ensure_main || return
	CHECKS=$((CHECKS + 1))
	if ! dump_ui; then
		WARNINGS=$((WARNINGS + 1)); log "warning: no UI dump for $COMBO | $TAB | $label"; return
	fi
	local out
	out=$(python3 "$GEOM" check "$OUT/.cur.xml" "$CTX" "$TAB" "$@" 2>&1)
	[ -n "$out" ] && record_failure "$label: $out"
}

check_one_tab() {
	CHECKS=$((CHECKS + 1))
	adb shell dumpsys activity "$ACTIVITY" > "$OUT/.frag.txt" 2>&1
	local out
	out=$(python3 "$GEOM" tabs "$OUT/.frag.txt")
	[ -n "$out" ] && record_failure "$1: $out"
}

baseline() {
	if dump_ui; then python3 "$GEOM" baseline "$OUT/.cur.xml" "$CTX" "$TAB"; fi
}

screen_size() {
	local size
	size=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1)
	W=${size%x*}; H=${size#*x}
	if [ "$(adb shell settings get system user_rotation | tr -d '\r')" = "1" ]; then local t=$W; W=$H; H=$t; fi
	X=$((W / 2))
}

# drag <px up (negative: down)> <ms>
drag() {
	local dy=$1 ms=$2 y0
	if (( dy > 0 )); then y0=$((H * 72 / 100)); else y0=$((H * 30 / 100)); fi
	adb shell input swipe $X $y0 $X $((y0 - dy)) "$ms"
}
fling_up() { adb shell input swipe $X $((H * 80 / 100)) $X $((H * 25 / 100)) 120; }
fling_down() { adb shell input swipe $X $((H * 25 / 100)) $X $((H * 80 / 100)) 120; }
rest() { sleep "${1:-1.3}"; }

status_bar_height() {
	adb shell dumpsys window > "$OUT/.win.txt" 2>&1
	python3 - "$OUT/.win.txt" "$CTX" <<'PY'
import json, re, sys
text = open(sys.argv[1], errors="replace").read()
heights = [int(h) for h in re.findall(r"type=statusBars frame=\[\d+,\d+\]\[\d+,(\d+)\]", text)]
json.dump({"status_bar": max(heights, default=0), "max_gap": 96}, open(sys.argv[2], "w"))
PY
}

# A gesture can still land as a tap and open a manga: go back to the main screen and note it as a slip of the
# test, not as a failure of the app. Returns 1 when the main screen was not shown.
ensure_main() {
	local top
	top=$(adb shell dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" | tr -d '\r')
	case "$top" in *MainActivity*) return 0;; esac
	WARNINGS=$((WARNINGS + 1)); log "warning: left the main screen ($COMBO | $TAB): $top"
	for _ in 1 2 3; do
		adb shell input keyevent KEYCODE_BACK; sleep 1
		top=$(adb shell dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" | tr -d '\r')
		case "$top" in *MainActivity*) return 1;; esac
	done
	launch 6
	return 1
}

# Taps a section of the navigation; when the bottom bar is scrolled away it is brought back first. Never taps a
# remembered position (there may be a manga under it now).
tap_tab() {
	local xy
	dismiss_system_dialogs
	for attempt in 1 2 3; do
		dump_ui || continue
		xy=$(python3 - "$OUT/.cur.xml" "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for n in root.iter('node'):
	if sys.argv[2] in (n.get('content-desc'), n.get('text')) and n.get('clickable') == 'true':
		x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
		if y2 - y1 > 20:
			print((x1 + x2) // 2, (y1 + y2) // 2)
			break
PY
)
		if [ -n "$xy" ]; then adb shell input tap $xy; return 0; fi
		drag -500 400; rest 1.2
	done
	WARNINGS=$((WARNINGS + 1)); log "warning: no tab '$1' on screen ($COMBO)"
	return 1
}

is_on() { case " $JOB_PREFS $COMBO_PREFS " in *" $1=boolean:true "*) return 0;; *) return 1;; esac; }

# Gestures and checks on the tab that is shown.
exercise_tab() {
	local rest_checks="top_rest"
	# The floating category tabs stop below the status bar; the plain row scrolls away with the content, as before.
	is_on top_bar_floating && rest_checks="top_rest pill_below_status"
	local bottom="bottom_rest"
	is_on nav_floating && bottom=""
	is_on nav_pinned && bottom="bottom_shown"
	rest 1.5
	check_one_tab "after switching"
	baseline
	check "at rest" $rest_checks top_open first_below_top no_gap_top $([ -n "$bottom" ] && echo bottom_shown)
	# How the bar looks fully hidden on this tab (Favourites keeps the status bar strip of its app bar).
	drag 900 500; rest
	if ensure_main && dump_ui; then
		python3 "$GEOM" collapsed "$OUT/.cur.xml" "$CTX" "$TAB"
		[ "$TAB" != "Favorites" ] && ! is_on nav_pinned && check "fully hidden" hidden_fully
		check "height of the bar" same_height
	fi
	for _ in 1 2 3 4 5 6; do fling_down; sleep 0.4; done; rest 2
	drag 120 450; rest
	check "short drag then release" $rest_checks $bottom
	drag 60 300; rest
	check "very short drag" $rest_checks $bottom
	local half
	half=$(python3 -c "import json;print(json.load(open('$CTX')).get('$TAB',{}).get('appbar_h',300)//2)")
	drag "$half" 600; rest
	check "half the bar dragged" $rest_checks $bottom
	drag 700 500; rest
	check "bar hidden by a long drag" $rest_checks $bottom
	drag -60 300; rest
	check "slight drag back" $rest_checks $bottom
	drag -45 250; rest
	check "tiny drag back" $rest_checks $bottom
	fling_up; rest 2.5
	check "after a fling" $rest_checks $bottom
	for _ in 1 2 3 4 5 6; do fling_up; sleep 0.4; done; rest 2.5
	if is_on nav_floating || is_on nav_pinned; then
		check "at the end of the list" $rest_checks $bottom last_above_bar
	else
		check "at the end of the list" $rest_checks $bottom
	fi
	fling_down; rest 2.5
	check "fling back" $rest_checks $bottom
	for _ in 1 2 3 4 5 6 7; do fling_down; sleep 0.4; done; rest 2.5
	check "back at the top" $rest_checks $bottom top_open first_below_top no_gap_top
	if [ "$TAB" = "Favorites" ]; then
		drag 700 500; rest
		adb shell input swipe $((W * 85 / 100)) $((H / 2)) $((W * 15 / 100)) $((H / 2)) 250; rest 1.8
		check "other category, bar hidden" $rest_checks first_below_top no_gap_top
		adb shell input swipe $((W * 15 / 100)) $((H / 2)) $((W * 85 / 100)) $((H / 2)) 250; rest 1.8
		check "first category again" $rest_checks
		drag 120 450; sleep 0.2
		adb shell input swipe $((W * 85 / 100)) $((H / 2)) $((W * 15 / 100)) $((H / 2)) 250; rest 1.8
		check "category swiped with the bar halfway" $rest_checks
		adb shell input swipe $((W * 15 / 100)) $((H / 2)) $((W * 85 / 100)) $((H / 2)) 250; rest 1.2
	fi
}

TABS=(History Favorites Explore Feed)

run_combo() {
	COMBO=$1; shift
	COMBO_PREFS="$*"
	log "=== combo $COMBO: $JOB_PREFS $COMBO_PREFS"
	adb logcat -c
	# Defaults first, so a combination does not inherit the previous one.
	set_prefs nav_label_mode=remove: main_fab_round=remove: nav_capsule=remove: nav_full_width=remove: \
		nav_floating_height=remove: nav_corner_radius=remove: nav_spacing=remove: nav_height=remove: \
		nav_opacity=remove: nav_accent=remove: nav_layered_surface=remove: \
		nav_main=string:HISTORY,FAVORITES,EXPLORE,FEED $JOB_PREFS "$@"
	launch 8
	screen_size
	status_bar_height
	local i=0 next
	for TAB in "${TABS[@]}"; do
		tap_tab "$TAB" || continue
		exercise_tab
		# A drag that leaves the bar halfway, then at once another tab: the bar has to settle there.
		next=${TABS[$(( (i + 1) % ${#TABS[@]} ))]}
		i=$((i + 1))
		drag 120 400
		tap_tab "$next"
		local prev=$TAB; TAB=$next; rest 1.8
		check "tab switch right after a drag (from $prev)" top_rest
		check_one_tab "fast switch from $prev"
		TAB=$prev
	done
	# Rapid switching: exactly one tab at the end.
	for _ in 1 2 3; do tap_tab History; tap_tab Favorites; tap_tab Explore; done
	TAB=Explore; rest 2
	check_one_tab "rapid switching"
	if adb logcat -d | grep -A3 "FATAL EXCEPTION" | grep -q "Process: $PKG"; then
		TAB="-"; record_failure "crash (see logcat-$COMBO.txt)"
		adb logcat -d > "$OUT/logcat-${COMBO//\//_}.txt"
	fi
	if adb logcat -d | grep -q "ANR in $PKG"; then TAB="-"; record_failure "ANR"; fi
}

# The setting switched in the settings screen while the main screen stays open: afterwards everything as at a start.
live_switch() {
	COMBO=$1; local title=$2
	log "=== live switch of '$title'"
	launch 8
	tap_tab Favorites; rest 1.5
	drag 700 500; rest
	for round in 1 2; do
		adb shell am start -W -n "$PKG/io.github.landwarderer.futon.settings.SettingsActivity" > /dev/null
		sleep 2
		tap_node "Appearance" && sleep 2
		scroll_and_tap "$title"; sleep 1
		adb shell input keyevent KEYCODE_BACK; sleep 1
		adb shell input keyevent KEYCODE_BACK; sleep 3
		for TAB in Favorites History; do
			tap_tab "$TAB" || continue
			rest 1.5
			check "after switching '$title' ($round)" top_rest first_below_top no_gap_top
			check_one_tab "after switching '$title' ($round)"
		done
	done
}

log "device: $(adb shell getprop ro.product.model) API $(adb shell getprop ro.build.version.sdk) density $(adb shell wm density)"
log "job: $JOB_PREFS"
adb root > /dev/null; sleep 3; adb wait-for-device
adb shell settings put global hide_error_dialogs 1
APK=$(ls app/build/outputs/apk/debug/*.apk | head -1)
log "installing $APK"
adb install -r -g "$APK" | tee -a "$OUT/run.log"
: > "$OUT/report.txt"
launch 10
set_prefs onboarding_version=int:1 mode_switcher_spotlight_shown=boolean:true sources_version=int:1
launch 8
shot 00_first_start_main
adb shell am force-stop $PKG
adb pull "$DB" "$OUT/.db" > /dev/null 2>&1
adb pull "$DB-wal" "$OUT/.db-wal" > /dev/null 2>&1
python3 - "$OUT/.db" "$COVER_URL" <<'PY' 2>&1 | tee -a "$OUT/run.log"
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
	quoted = ",".join(f'"{n}"' for n in names)
	db.execute(f'INSERT OR REPLACE INTO "{table}" ({quoted}) VALUES ({",".join("?" * len(names))})', params)
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

adb shell settings put system accelerometer_rotation 0
run_combo 01 nav_label_mode=string:NONE main_fab_round=boolean:false
run_combo 02 nav_label_mode=string:ALL main_fab_round=boolean:false
run_combo 03 nav_label_mode=string:SELECTED main_fab_round=boolean:true
run_combo 04 nav_label_mode=string:SELECTED_BESIDE main_fab_round=boolean:true
adb shell settings put system font_scale 1.3
run_combo 05-font130 nav_label_mode=string:ALL main_fab_round=boolean:false
run_combo 06-font130 nav_label_mode=string:SELECTED main_fab_round=boolean:true
adb shell settings put system font_scale 1.0
run_combo 07 nav_label_mode=string:NONE nav_capsule=boolean:false
run_combo 08 nav_label_mode=string:SELECTED nav_full_width=boolean:true main_fab_round=boolean:true
run_combo 09 nav_label_mode=string:ALL nav_floating_height=int:64 nav_corner_radius=int:12 nav_spacing=int:4 nav_height=int:72
run_combo 10 nav_label_mode=string:SELECTED nav_opacity=int:70 nav_accent=boolean:true nav_layered_surface=boolean:true
adb shell settings put system user_rotation 1
run_combo 11-landscape nav_label_mode=string:ALL main_fab_round=boolean:true
adb shell settings put system user_rotation 0
adb shell cmd overlay enable com.android.internal.systemui.navbar.threebutton > /dev/null 2>&1; sleep 3
run_combo 12-3button nav_label_mode=string:SELECTED main_fab_round=boolean:true
adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural > /dev/null 2>&1; sleep 3
screen_size
live_switch 13-live "Floating top bar"
live_switch 14-live "Floating navigation bar"
live_switch 15-live "Pin navigation UI"

log "done; checks: $CHECKS, warnings (no UI dump): $WARNINGS, failures: ${#FAILURES[@]}"
{ echo; echo "checks: $CHECKS, warnings: $WARNINGS, failures: ${#FAILURES[@]}"; } >> "$OUT/report.txt"
(( ${#FAILURES[@]} == 0 ))
