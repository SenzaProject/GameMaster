# Updating GitHub with phase 5 (Termux)

Repo: https://github.com/SenzaProject/GameMaster

First time on this phone, set your identity:

```bash
git config --global user.name "SenzaProject"
git config --global user.email "dionoberutu06@gmail.com"
```

Then, from your existing local copy of the repo (the one you pushed phase 4 from):

```bash
cd ~/GameMaster                 # change to the folder where your repo is
git status                      # should say "working tree clean"

# 1. Unpack phase 5 somewhere else
cd ~
unzip -o GameMaster_Phase05.zip -d phase5_tmp

# 2. Make the repo folder match phase 5 exactly (keeps the .git folder)
cd ~/GameMaster
find . -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
cp -a ~/phase5_tmp/SenzaGameSpace/. .
rm -rf ~/phase5_tmp

# 3. Check what changed, commit, push
git add -A
git status                      # review the list
git commit -m "Phase 5: floating panel, dock, game modes, toolbox, statistics"
git push
```

Notes:
- Step 2 deletes every file in the repo folder except `.git` and then copies phase 5 in, so files removed in phase 5 are removed on GitHub too. If you added private files there (keystores, notes), copy them out first.
- If `git push` asks for a password, use a personal access token (GitHub > Settings > Developer settings > Tokens) or run `gh auth login`.
- If you have no local copy: `git clone https://github.com/SenzaProject/GameMaster.git`, then do step 2 from inside it.
- After the push, open the Actions tab. The first build verifies the code; paste me any error and I will fix it.
- When the build finishes, commit the generated `app/schemas` folder (Room database schema) if it is not already in the repo.
