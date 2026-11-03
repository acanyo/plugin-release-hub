#!/usr/bin/env python3
"""Read-only acceptance checks against a running Halo; credentials are optional environment vars."""
import base64
import json
import os
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8098").rstrip("/")
PUBLIC = "/apis/api.release-hub.xhhao.com/v1alpha1"
ADMIN = "/apis/console.api.release-hub.xhhao.com/v1alpha1"
FORBIDDEN = {"email", "credentialSecret", "tokenVersion", "passwordSecret", "webhookUrl", "stringData", "password", "token"}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


opener = urllib.request.build_opener(NoRedirect)


def request(path, auth=False):
    headers = {"Accept": "application/json"}
    if auth:
        credentials = os.environ["RELEASE_HUB_SMOKE_USER"] + ":" + os.environ["RELEASE_HUB_SMOKE_PASSWORD"]
        headers["Authorization"] = "Basic " + base64.b64encode(credentials.encode()).decode()
    return opener.open(urllib.request.Request(BASE + path, headers=headers), timeout=30)


def public_keys(value):
    if isinstance(value, dict):
        assert not (FORBIDDEN & value.keys()), "Public API exposed a private field"
        for child in value.values():
            public_keys(child)
    elif isinstance(value, list):
        for child in value:
            public_keys(child)


def json_get(path, auth=False):
    with request(path, auth) as response:
        assert "json" in response.headers["Content-Type"], "Expected JSON, received a page or login redirect"
        return json.load(response)


projects = json_get(PUBLIC + "/projects")
public_keys(projects)
for channel in ["all", "stable", "prerelease"]:
    page = json_get(PUBLIC + "/releases?channel=" + channel + "&page=1&size=10")
    public_keys(page)
    if channel == "stable":
        assert all(not item["prerelease"] for item in page["items"])
    if channel == "prerelease":
        assert all(item["prerelease"] for item in page["items"])
    if page["items"]:
        public_keys(json_get(PUBLIC + "/releases/" + page["items"][0]["name"]))
    print("PASS public releases:", channel, "total", page["total"])

for path in ["/releases/rss.xml", "/releases/atom.xml"]:
    with request(path) as response:
        tree = ET.fromstring(response.read())
        count = len(tree.findall(".//item")) or len(tree.findall("{http://www.w3.org/2005/Atom}entry"))
        assert count <= 50
        print("PASS feed:", path, "entries", count)

for path in [ADMIN + "/subscriptions", ADMIN + "/stats", ADMIN + "/accounts", ADMIN + "/accounts/test/repositories", "/apis/release-hub.xhhao.com/v1alpha1/hubsubscriptions", "/apis/release-hub.xhhao.com/v1alpha1/repositoryaccounts", "/api/v1alpha1/secrets"]:
    try:
        request(path)
        raise AssertionError("Anonymous access must be rejected: " + path)
    except urllib.error.HTTPError as error:
        assert error.code in [302, 401, 403], (path, error.code)
        print("PASS anonymous access denied:", path, error.code)

if os.getenv("RELEASE_HUB_SMOKE_USER") and os.getenv("RELEASE_HUB_SMOKE_PASSWORD"):
    stats = json_get(ADMIN + "/stats", auth=True)
    print("PASS authenticated stats:", stats)
    print("PASS delivery records accessible:", json_get(ADMIN + "/deliveries", auth=True)["total"])
    accounts = json_get(ADMIN + "/accounts", auth=True)
    assert all(not ({"token", "password", "stringData", "data"} & account.keys()) for account in accounts), "Account list exposed credential contents"
    print("PASS account list has no credentials:", len(accounts))

print("Acceptance checks passed.")
