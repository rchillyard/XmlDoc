# Git workflow

- Use `/Users/lady_penguin/Documents/3-way-merge` as this project's shared working directory.
- Manage tasks and MRs as Git branches in this repository. Use `git switch` in this directory to change branches.
- Use the `kaining/` prefix for new task branches unless the user requests another name.
- Do not create a separate folder, clone, or Git worktree for a branch or MR unless the user explicitly requests an additional working directory.
- Before switching branches, inspect `git status` and preserve staged, unstaged, and untracked work. Never discard changes to make a branch switch succeed.
- Work sequentially in this shared directory. Do not let concurrent tasks switch or edit different branches in it.
- Before consolidating an old working directory, check active tasks, tracked changes, untracked files, and ignored files. Preserve its branches, commits, and needed local files.
- Keep research reference materials in `doc/3-way-merge-paper/`. This is a materials directory within the repository, not a branch checkout.
