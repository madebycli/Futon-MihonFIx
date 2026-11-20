#!/usr/bin/env python3
"""Geometry checks of the main screen for the navigation matrix run (test-only).

    nav_geometry.py baseline <ui.xml> <ctx.json> <tab>          remember the bars of <tab> at rest at the list top
    nav_geometry.py check <ui.xml> <ctx.json> <tab> <check>...  print one line per failed check (nothing when fine)
    nav_geometry.py tabs <dumpsys.txt>                          print a failure unless exactly one tab is shown

The positions come from a uiautomator dump, whose bounds are clipped to the screen: a bar that is pushed partly off
the screen has a smaller visible height there.
"""
import json
import re
import sys
import xml.etree.ElementTree as ET

PKG = "io.github.landwarderer.futon.debug"
SLACK = 3  # px


def bounds(node):
    x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", node.get("bounds")))
    return x1, y1, x2, y2


def by_id(root, name):
    found = [n for n in root.iter("node") if n.get("resource-id") == f"{PKG}:id/{name}"]
    found = [n for n in found if bounds(n)[3] > bounds(n)[1]]
    return found


def largest(nodes):
    def area(n):
        x1, y1, x2, y2 = bounds(n)
        return (x2 - x1) * max(0, y2 - y1)
    return max(nodes, key=area) if nodes else None


def measure(path):
    root = ET.parse(path).getroot()
    m = {}
    screen = [bounds(n) for n in root.iter("node")]
    m["screen_h"] = max((b[3] for b in screen), default=0)
    appbar = largest(by_id(root, "appbar"))
    m["appbar_h"] = (bounds(appbar)[3] - bounds(appbar)[1]) if appbar is not None else 0
    m["appbar_bottom"] = bounds(appbar)[3] if appbar is not None else 0
    search = largest(by_id(root, "search_bar"))
    m["search_bottom"] = bounds(search)[3] if search is not None else None
    m["search_h"] = (bounds(search)[3] - bounds(search)[1]) if search is not None else 0
    tabs = largest(by_id(root, "tabs"))
    m["tabs_top"] = bounds(tabs)[1] if tabs is not None else None
    m["tabs_bottom"] = bounds(tabs)[3] if tabs is not None else None
    nav = largest(by_id(root, "bottomNav"))
    items = []
    if nav is not None:
        items = [bounds(n) for n in nav.iter("node") if n.get("clickable") == "true" and bounds(n)[3] > bounds(n)[1]]
    m["nav_items"] = len(items)
    m["nav_item_h"] = max((b[3] - b[1] for b in items), default=0)
    m["nav_top"] = min((b[1] for b in items), default=None)
    lists = by_id(root, "recyclerView")
    rv = largest(lists)
    children = [bounds(c) for c in rv.findall("node")] if rv is not None else []
    children = [c for c in children if c[3] > c[1]]
    m["list_first_top"] = min((c[1] for c in children), default=None)
    m["list_last_bottom"] = max((c[3] for c in children), default=None)
    m["list_items"] = len(children)
    return m


def load(ctx_path):
    try:
        with open(ctx_path) as f:
            return json.load(f)
    except (OSError, ValueError):
        return {}


def main():
    cmd = sys.argv[1]
    if cmd == "tabs":
        text = open(sys.argv[2], errors="replace").read()
        states = []
        for match in re.finditer(r"mTag=primary:(\S+)", text):
            hidden = re.search(r"mHidden=(true|false)", text[match.end():])
            states.append((match.group(1).rsplit(".", 1)[-1], hidden.group(1) if hidden else "?"))
        shown = sorted({name for name, hidden in states if hidden == "false"})
        if len(shown) != 1:
            print(f"tabs shown: {shown or 'none'} (all: {states})")
        return
    ui, ctx_path, tab = sys.argv[2], sys.argv[3], sys.argv[4]
    ctx = load(ctx_path)
    m = measure(ui)
    if cmd == "baseline":
        ctx[tab] = m
        with open(ctx_path, "w") as f:
            json.dump(ctx, f)
        return
    base = ctx.get(tab, {})
    sb = ctx.get("status_bar", 0)
    problems = []
    for check in sys.argv[5:]:
        if check == "top_rest":
            # Fully shown or fully hidden, never halfway.
            full = base.get("appbar_h", 0)
            h = m["appbar_h"]
            if full and not (h >= full - SLACK or h <= sb + SLACK):
                problems.append(f"top bar halfway: visible {h}px of {full}px")
        elif check == "top_open":
            full = base.get("appbar_h", 0)
            if full and m["appbar_h"] < full - SLACK:
                problems.append(f"top bar not fully open: {m['appbar_h']}px of {full}px")
        elif check == "pill_below_status":
            if m["tabs_top"] is not None and m["tabs_top"] < sb - SLACK:
                problems.append(f"category tabs under the status bar: top {m['tabs_top']} < {sb}")
        elif check == "first_below_top":
            # At the top of a list the first item starts below what floats above it.
            edge = m["tabs_bottom"] if m["tabs_bottom"] is not None else m["search_bottom"]
            first = m["list_first_top"]
            if edge is not None and first is not None and first < edge - SLACK:
                problems.append(f"first item under the top bar: item top {first} < bar bottom {edge}")
        elif check == "no_gap_top":
            # And not far below it either (the gap seen after switching the setting).
            edge = m["tabs_bottom"] if m["tabs_bottom"] is not None else m["search_bottom"]
            first = m["list_first_top"]
            limit = ctx.get("max_gap", 96)
            if edge is not None and first is not None and first - edge > limit:
                problems.append(f"gap above the list: item top {first}, bar bottom {edge} ({first - edge}px)")
        elif check == "last_above_bar":
            bar_top = base.get("nav_top")
            last = m["list_last_bottom"]
            if bar_top is not None and last is not None and last > bar_top + SLACK:
                problems.append(f"last item under the bottom bar: item bottom {last} > bar top {bar_top}")
        elif check == "bottom_rest":
            # The docked bar snaps: fully shown or hidden.
            full = base.get("nav_item_h", 0)
            h = m["nav_item_h"]
            if full and m["nav_items"] and not (h >= full - SLACK or h <= SLACK):
                problems.append(f"bottom bar halfway: item visible {h}px of {full}px")
        elif check == "bottom_shown":
            full = base.get("nav_item_h", 0)
            if full and (m["nav_item_h"] < full - SLACK or m["nav_top"] is None or
                         abs(m["nav_top"] - base.get("nav_top", m["nav_top"])) > SLACK):
                problems.append(f"bottom bar not fully shown: top {m['nav_top']} (rest {base.get('nav_top')}), "
                                f"item {m['nav_item_h']}px of {full}px")
        else:
            problems.append(f"unknown check {check}")
    if problems:
        print("; ".join(problems))


if __name__ == "__main__":
    main()
