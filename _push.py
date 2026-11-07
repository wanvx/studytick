import base64
import json
import os
import subprocess
import urllib.request

TOKEN = os.environ["GH_TOKEN"]
OWNER, REPO = "wanvx", "studytick"
API = "https://api.github.com"
MESSAGE = os.environ.get("COMMIT_MSG", "docs: 更新到 1.0.1")


def req(method, path, data=None):
    url = API + path
    body = json.dumps(data).encode() if data is not None else None
    r = urllib.request.Request(url, data=body, method=method)
    r.add_header("Authorization", "Bearer " + TOKEN)
    r.add_header("Accept", "application/vnd.github+json")
    r.add_header("X-GitHub-Api-Version", "2022-11-28")
    if body is not None:
        r.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(r, timeout=180) as resp:
        return json.loads(resp.read().decode() or "{}")


base = req("GET", f"/repos/{OWNER}/{REPO}/commits/main")
base_sha = base["sha"]
base_tree = base["commit"]["tree"]["sha"]
print("remote base:", base_sha[:7])

tree_resp = req("GET", f"/repos/{OWNER}/{REPO}/git/trees/{base_tree}?recursive=1")
remote_map = {x["path"]: x["sha"] for x in tree_resp["tree"] if x["type"] == "blob"}

entries = []
for line in subprocess.check_output(["git", "ls-files", "-s"], text=True).splitlines():
    meta, path = line.split("\t", 1)
    mode = meta.split()[0]
    local_sha = subprocess.check_output(["git", "hash-object", path], text=True).strip()
    if remote_map.get(path) == local_sha:
        continue
    with open(path, "rb") as fh:
        content = fh.read()
    blob = req("POST", f"/repos/{OWNER}/{REPO}/git/blobs", {
        "content": base64.b64encode(content).decode(),
        "encoding": "base64",
    })
    entries.append({"path": path, "mode": mode, "type": "blob", "sha": blob["sha"]})
    print("  changed:", path)

if not entries:
    print("nothing to commit")
    raise SystemExit(0)

new_tree = req("POST", f"/repos/{OWNER}/{REPO}/git/trees", {
    "base_tree": base_tree,
    "tree": entries,
})
commit = req("POST", f"/repos/{OWNER}/{REPO}/git/commits", {
    "message": MESSAGE,
    "tree": new_tree["sha"],
    "parents": [base_sha],
})
ref = req("PATCH", f"/repos/{OWNER}/{REPO}/git/refs/heads/main", {
    "sha": commit["sha"],
    "force": False,
})
print("new commit:", ref["object"]["sha"][:7], "| files:", len(entries))
