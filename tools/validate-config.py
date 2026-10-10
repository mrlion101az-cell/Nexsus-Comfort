#!/usr/bin/env python3
"""Offline check of NexusComfort's config.yml and plugin.yml against what the code actually uses.

  python3 tools/validate-config.py

Fails if config.yml lacks a key ComfortConfig reads (so a typo can never silently fall back), has a key nothing reads,
or has a value that differs from the built-in default; if the code uses a permission or command plugin.yml does not
declare; and if a method the code calls on NexusWorldWar by reflection is not in its real API
(looked up in the sibling plugin folder, so run it from the original location).
Needs: pip install pyyaml
"""
import os, re, sys
import yaml

here = os.path.dirname(os.path.abspath(__file__))
root = os.path.join(here, "..")
res = os.path.join(root, "src", "main", "resources")
code = os.path.join(root, "src", "main", "java", "com", "nexuscraft", "nexuscomfort")
read = lambda *p: open(os.path.join(*p), encoding="utf-8").read()
cfg_src = read(code, "ComfortConfig.java")
cfg = yaml.safe_load(read(res, "config.yml"))
plug = yaml.safe_load(read(res, "plugin.yml"))
all_src = {f: read(code, f) for f in os.listdir(code) if f.endswith(".java")}
errors = []

def flat(d, prefix=""):
    out = {}
    for k, v in d.items():
        p = prefix + str(k)
        if isinstance(v, dict):
            out.update(flat(v, p + "."))
        else:
            out[p] = v
    return out

have = flat(cfg)
body = cfg_src[cfg_src.index("void load("):]
read_keys = set(re.findall(r'"([a-z]+(?:-[a-z]+)*\.[a-z]+(?:-[a-z]+)*)"', body))
for k in sorted(read_keys):
    if k not in have:
        errors.append(f"code reads '{k}' but config.yml does not define it")
for k in sorted(have):
    if k not in read_keys:
        errors.append(f"config.yml defines '{k}' but the code never reads it")

# defaults: field initialisers against config.yml values
decl = {}
for m in re.finditer(r'^\s{4}(?:String|int|boolean|List<String>)\s+(\w+)\s*=\s*([^;]+);', cfg_src, re.M):
    decl[m.group(1)] = m.group(2).strip()

def lit(v):
    if v in ("true", "false"):
        return v == "true"
    if v.startswith('"') and v.endswith('"'):
        return v[1:-1]
    if re.fullmatch(r'-?\d+', v):
        return int(v)
    m = re.fullmatch(r'new ArrayList<>\((?:List\.of\((.*)\))?\)', v)
    if m:
        return re.findall(r'"([^"]*)"', m.group(1) or "")
    return None

assign = {}
for stmt in re.findall(r'(\w+)\s*=\s*[^;]*?"([a-z]+(?:-[a-z]+)*\.[a-z]+(?:-[a-z]+)*)"[^;]*;', body):
    assign.setdefault(stmt[1], stmt[0])
assign["schedule.days"] = "scheduleDays"
for key, field in sorted(assign.items()):
    if key not in have or field not in decl:
        continue
    d = lit(decl[field])
    if d is None:
        continue
    if isinstance(d, list):
        d = [x.lower() for x in d]
    if have[key] != d:
        errors.append(f"config.yml has {key}={have[key]!r} but the built-in default is {d!r}")

# plugin.yml against the code
if not os.path.exists(os.path.join(code, plug["main"].split(".")[-1] + ".java")):
    errors.append("plugin.yml main class has no source file")
declared_perms = set(plug.get("permissions", {}))
used_perms = set(re.findall(r'"(nexuscomfort\.[a-z.]+)"', "\n".join(all_src.values())))
for p in sorted(used_perms - declared_perms):
    errors.append(f"code uses permission '{p}' that plugin.yml does not declare")
cmds = set(plug.get("commands", {}))
used_cmds = set(re.findall(r'getCommand\("(\w+)"\)', all_src["ComfortPlugin.java"]))
if used_cmds != cmds:
    errors.append(f"commands differ: code {sorted(used_cmds)} vs plugin.yml {sorted(cmds)}")
for name, c in plug.get("commands", {}).items():
    if c.get("permission") not in declared_perms:
        errors.append(f"command {name} uses undeclared permission {c.get('permission')}")
if plug.get("softdepend") != ["NexusWorldWar"]:
    errors.append("softdepend should be NexusWorldWar")

# the real APIs the reflection calls
apis = {
    "WarBridge.java": (os.path.join(root, "..", "NexusWorldWar", "src", "main", "java", "com", "nexuscraft", "nexusworldwar", "WorldWarApi.java"),
                       "com.nexuscraft.nexusworldwar.WorldWarApi"),
}
for src_name, (path, cls) in apis.items():
    if cls not in all_src[src_name]:
        errors.append(f"{src_name} does not name {cls}")
    if not os.path.exists(path):
        errors.append(f"cannot find the real API at {path}")
        continue
    api = read(path)
    for method, params in re.findall(r'getMethod\("(\w+)",\s*new Class<\?>\[\] \{([^}]*)\}', all_src[src_name]):
        want = [p.strip().replace(".class", "") for p in params.split(",") if p.strip()]
        m = re.search(r'\b(\w[\w<>,\s]*?)\s+' + method + r'\(([^)]*)\)\s*;', api)
        if not m:
            errors.append(f"{src_name}: real API has no method {method}")
            continue
        have_params = [p.strip().split()[0] for p in m.group(2).split(",") if p.strip()]
        if have_params != want:
            errors.append(f"{src_name}: {method} takes {have_params} in the real API, code passes {want}")

if errors:
    print("\n".join("ERROR: " + e for e in errors))
    sys.exit(1)
print(f"config OK ({len(have)} settings, {len(declared_perms)} permissions, {len(cmds)} command)")
