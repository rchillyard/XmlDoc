# Three-way merge paper — reference materials

Research reference material for the three-way XML merge work, per `AGENTS.md`. This is
a materials directory, not a branch checkout: nothing here is read by any Scala, sbt,
Python or workflow file, and the build does not depend on it.

## Omitted from `main`

One file from this directory was deliberately left out when
`kaining/real-world-idml-tests` was merged into `main`, because an 11 MiB audio file is
a large and permanent addition to a repository whose next largest file is 421 KB.

| File | Size | Blob |
| --- | --- | --- |
| `Blue Bossa (Remastered).mp3` | 11.18 MiB | `1720567a65db7c54c155fef37cd8942fbd033492` |

It was added by Keny Teen on 2026-11-12, in commit `f5cbf38` ("mixed content \ pcs") on
the `kaining/real-world-idml-tests` branch. It accompanies `Blue Bossa.pdf` and
`Blue Bossa with Chords.pdf`, which are both small and are still here.

### Restoring a local copy

From the branch, which is the normal route:

```
git checkout kaining/real-world-idml-tests -- 'doc/3-way-merge-paper/Blue Bossa (Remastered).mp3'
```

Or straight from the object, which works from any commit that can still reach it and
does not need the branch to exist:

```
git cat-file blob 1720567a65db7c54c155fef37cd8942fbd033492 > 'doc/3-way-merge-paper/Blue Bossa (Remastered).mp3'
```

A restored copy is ignored by `.gitignore`, so it will not be committed back by
accident. Note what that does *not* do: ignore rules never apply to a file that is
already tracked on another branch, so a working tree switching from
`kaining/real-world-idml-tests` to `main` will still have the file removed. Restore it
afterwards with one of the commands above.
