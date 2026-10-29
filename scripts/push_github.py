"""Publish APISentinel Java project to GitHub as a new public repository."""
import os
import sys
import subprocess
from pathlib import Path

# Add github-daily-agent to sys.path to leverage existing auth configuration
agent_dir = r"C:\Users\RAM\Desktop\github-daily-agent"
if agent_dir not in sys.path:
    sys.path.insert(0, agent_dir)

import config
from github import Auth, Github, GithubException

cfg = config.load_config()
if not cfg.token:
    print("[-] Error: GitHub token not found in config.")
    sys.exit(1)

gh = Github(auth=Auth.Token(cfg.token))
user = gh.get_user()

repo_name = "api-sentinel"
description = "Autonomous API & Microservice Health Monitor, Latency Tracker, and Incident Management Engine in Java 17"
project_dir = Path(__file__).resolve().parent.parent

print(f"[*] Authenticated as: {user.login}")

# Check if repo already exists
repo = None
try:
    repo = user.get_repo(repo_name)
    print(f"[*] Found existing repository: {repo.html_url}")
except GithubException:
    print(f"[*] Creating new repository: {repo_name}...")
    repo = user.create_repo(
        repo_name,
        description=description,
        private=False,
        auto_init=False
    )
    print(f"[+] Created repository: {repo.html_url}")

# Authenticated push URL
auth_url = f"https://x-access-token:{cfg.token}@github.com/{repo.full_name}.git"
clean_url = f"https://github.com/{repo.full_name}.git"

try:
    # Set remote to authenticated URL
    subprocess.run(["git", "remote", "set-url", "origin", auth_url], cwd=project_dir, check=True)

    # Push main branch
    print(f"[*] Pushing main branch to {clean_url}...")
    res = subprocess.run(["git", "push", "-u", "origin", "main", "--force"], cwd=project_dir, capture_output=True, text=True)
    if res.returncode != 0:
        print(f"[-] Push error: {res.stderr}")
        sys.exit(1)
    print("[+] Successfully pushed to GitHub!")
finally:
    # Always reset remote origin to clean URL without token
    subprocess.run(["git", "remote", "set-url", "origin", clean_url], cwd=project_dir)

print("=" * 60)
print(f"SUCCESS: Repository is live at: {repo.html_url}")
print("=" * 60)
