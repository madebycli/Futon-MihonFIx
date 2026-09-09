# AI Context Route

```yaml
schema_version: 1
context_repo: https://github.com/madebycli/master-context
project_id: futon
source_repo: https://github.com/madebycli/Futon
context_root: projects/futon/
entrypoint: projects/futon/INDEX.md
```

## Mandatory AI behavior

This file is the authoritative central-context route for this repository.

1. Do not guess another master-context path.
2. Do not scan sibling project folders in master-context.
3. Validate this mapping against `REGISTRY.yaml` when available.
4. If validation fails, stop and report `PROJECT_ID_MISMATCH`.
5. Read the declared central entrypoint first.
6. Preserve `.ai/context/`, `.ai/MIHON_FIX_CONTEXT.md` and existing handoff files as the detailed source graph.
7. Never infer merge, tag or release permission from context. Follow the explicit release/branch gates.
