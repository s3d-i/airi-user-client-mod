# Reference Docs

## Intention

This directory holds implementation reference material.

These docs are not target architecture docs and they are not the source of truth. They exist to capture useful findings from code reading, multi-agent research, and adjacent repository inspection when that work would otherwise be expensive to repeat.

## What Belongs Here

- snapshot-specific protocol notes
- external repository integration findings
- implementation constraints discovered during research
- file maps for important upstream or adjacent codepaths
- explicit open questions that still block safe implementation

## What Does Not Belong Here

- aspirational architecture that belongs in [`../design/`](../design/README.md)
- current in-repo status that belongs in [`../dev/`](../dev/README.md)
- speculative guidance without a concrete code snapshot behind it

## Required Format For Reference Docs

Each reference doc should start with frontmatter that records the research context.

At minimum, frontmatter should include:

- the topic
- the date the research snapshot was taken
- the current repository branch, commit, and worktree status
- the external or compared repository branch, commit, and worktree status
- the intended implementation context or consumer

## Reading Rule

Treat these docs as high-signal research notes pinned to specific commits.

If either referenced repository has moved in a way that could affect behavior, verify the relevant code again before treating the doc as current.
