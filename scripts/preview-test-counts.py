import glob
import json
import os
import xml.etree.ElementTree as ET

result = {"source": os.environ["GITHUB_SHA"], "suites": {}}
for label, pattern in (
    ("unit", "/app/build/test-results/testDebugUnitTest/*.xml"),
    ("android", "/app/build/outputs/androidTest-results/connected/**/*.xml"),
):
    paths = glob.glob(os.environ["RUNNER_TEMP"] + "/futon-preview" + pattern, recursive=True)
    counts = dict(tests=0, failures=0, errors=0, skipped=0)
    failures = []
    for path in paths:
        root = ET.parse(path).getroot()
        suites = [root] if root.tag == "testsuite" else root.findall(".//testsuite")
        for suite in suites:
            for key in counts:
                counts[key] += int(suite.get(key, 0))
            for case in suite.findall("testcase"):
                for kind in ("failure", "error"):
                    element = case.find(kind)
                    if element is not None:
                        failures.append(dict(classname=case.get("classname"), name=case.get("name"),
                                             kind=kind, message=element.get("message")))
    result["suites"][label] = dict(xml_files=len(paths), counts=counts, failures=failures)
print(json.dumps(result, indent=2))
os.makedirs("preview-artifact", exist_ok=True)
with open("preview-artifact/TEST-RESULTS.json", "w") as out:
    json.dump(result, out, indent=2)
