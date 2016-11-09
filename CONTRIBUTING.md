# Contributing to claportal

## Getting Started
For detailed information on how to get started with the project, refer to [README.md](README.md).

## Contribution Flow

This is a rough outline of what a contributor's workflow looks like:

- [Fork](https://help.github.com/articles/fork-a-repo/) the main claportal repository.
- Clone your fork and set the upstream remote to the main claportal repository
- Set your name and e-mail in the Git configuration
- Create a topic branch from where you want to base your work
- Make commits of logical units
- Make sure your commit messages are in the proper format (see below)
- Push your changes to a topic branch in your fork of the repository
- [Submit a pull request](https://help.github.com/articles/about-pull-requests/)

Example:

``` shell
# Clone your forked repository
git clone git@github.com:<github username>/claportal.git

# Navigate to the directory
cd claportal

# Set name and e-mail configuration
git config user.name "John Doe"
git config user.email johndoe@example.com

# Setup the upstream remote
git remote add upstream https://github.com/vmware/claportal.git

# Create a topic branch for your changes
git checkout -b my-new-feature master

# After making the desired changes, commit and push to your fork
git commit -a -s
git push origin my-new-feature
```

### Staying In Sync With Upstream

When your branch gets out of sync with the master branch, use the following to update:

``` shell
git checkout my-new-feature
git fetch -a
git pull --rebase upstream master
git push --force-with-lease origin my-new-feature
```

### Updating Pull Requests

If your PR requires changes based on code review, you'll most likely want to squash these changes into existing commits.

If your pull request contains a single commit, or your changes are related to the most recent commit, you can amend the commit.

``` shell
git add .
git commit --amend
git push --force-with-lease origin my-new-feature
```

If you need to squash changes into an earlier commit, use the following:

``` shell
git add .
git commit --fixup <commit>
git rebase -i --autosquash master
git push --force-with-lease origin my-new-feature
```

Make sure you add a comment to the PR indicating that your changes are ready to review. GitHub does not generate a notification when you use git push.

### Formatting Commit Messages

We follow the conventions on [How to Write a Git Commit Message](http://chris.beams.io/posts/git-commit/).

Be sure to include any related GitHub issue references in the commit message.  See
[GFM syntax](https://guides.github.com/features/mastering-markdown/#GitHub-flavored-markdown) for referencing issues
and commits.

## Reporting Bugs and Creating Issues

When opening a new issue, try to roughly follow the commit message format conventions above.
