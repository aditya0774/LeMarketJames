# LEAP demo: how to use the demo files

Everything the team needs to run the 30-minute Business Requirements review. The demo runs on the
**Windows laptop** (native stack, no Docker). The Mac (Docker stack) is the fallback.

**Golden rule: never copy commands out of a PDF.** PDFs break underscores and line breaks. Load the
command file once per window, then type the short command names (`totals`, `fault_on`, ...).

## Contents

- [What's here](#whats-here)
- [Windows: first-time setup](#windows-first-time-setup)
- [Windows: every time](#windows-every-time)
- [On the day](#on-the-day)
- [Command reference](#command-reference)
- [If something goes wrong](#if-something-goes-wrong)
- [Mac fallback](#mac-fallback)
- [What was tested](#what-was-tested)

## What's here

All in this `demo` folder:

| File | What it is |
|---|---|
| `LEAP_Demo_Script_Windows.pdf` | **The script for the day**: who does what, minute by minute, what to say, what the reviewer should see, and the answers to "where does it live" questions |
| `demo.ps1` | Every demo command for Windows PowerShell |
| `lemarket-demo.dump` | A copy of the demo database: Client A (Lebron, account 49), Client B (Bronny, account 50), the rejected order 62 and a Ready overnight report. Test data only |
| `LEAP_Demo_Script.pdf` | The Mac version of the script (fallback) |
| `demo.sh` | Every demo command for the Mac (fallback) |

Roles: **Amara** drives, **Johan** narrates, **Aditya and Wali** answer design questions,
**Joseph and Will** take notes.

Logins (all passwords `Pass123!`):

| Who | Login |
|---|---|
| Client A | `Lebron@lemarket.com` (account 49) |
| Client B | `Bronny@lemarket.com` (account 50) |
| Trading Ops (staff app) | `ops@seed.lemarket.com` |
| Analyst (staff app) | `analyst@seed.lemarket.com` |

## Windows: first-time setup

Do this well before the demo: installing the tools and the first build take the longest.

**1. Install and check the tools** (each in a new PowerShell window):

| Tool | Check |
|---|---|
| Git | `git --version` |
| Java 21 (JDK) | `java -version` says 21 |
| Maven | `mvn -v` |
| Node.js 24 | `node -v` |
| PostgreSQL 16 or newer | Installed as a Windows service. Remember the `postgres` password you chose |

**2. Get the code:** clone the repo, or `git pull` on `main` if it's already there.

**3. Load the commands.** Open PowerShell in the repo folder:

```powershell
cd $HOME\LeMarketJames                    # wherever the repo is
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
. .\demo\demo.ps1                         # dot, space, then the path: loads the commands into this window
```

You should see `LEAP demo commands loaded (repo: ..., Client A = account 49)`. The first load
creates the repo's `.env` with new secrets. Git ignores it: **never commit it** (`git status`
should not list it).

**4. Create the database logins and load the demo database.** Both ask for the `postgres` password:

```powershell
.\scripts\windows\setup-db.ps1
restore_demo_db
```

`restore_demo_db` ends by printing Client A: account 49, $78,850.52, 13 filled orders, 14 orders in total.

**5. Start the stack:**

```powershell
start_stack
```

It builds the backend, opens one window per service and waits until all four services are UP. The
first run also installs the web apps' packages and takes several minutes. If Windows asks
**"Allow Java / Node.js to communicate on networks?"**, choose **Cancel**: the demo still works on
the laptop, and other computers can't reach the services.

**6. Open the apps** and accept the certificate warning in each:
https://localhost:4200 (trading app) and https://localhost:4201 (staff app).

**7. Take one backup** so there's one to show in act 3: `backup_now`, then `backups`.

**8. Rehearse act 4 twice** with a stopwatch (it must fit in 8 minutes), and do one full 30-minute
practice run with everyone in their roles.

## Windows: every time

In each new PowerShell window:

```powershell
cd $HOME\LeMarketJames
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
. .\demo\demo.ps1
```

Then, if the stack isn't running:

```powershell
start_stack -SkipBuild     # -SkipBuild: no rebuild, much faster
health                     # 8082, 8084, 8085, 8086 all UP
totals                     # Client A's numbers
```

Stop everything with `stop_stack`.

**Always start the stack with `start_stack` from a window where `demo.ps1` is loaded.** It sets
the demo settings: orders fill at any hour, and sessions end after 5 idle minutes instead of 1.
Started any other way, orders made outside US market hours sit as DELAYED.

**Never close a service window** (`orders-service :8085` and the others): closing one stops that
service.

## On the day

Follow `LEAP_Demo_Script_Windows.pdf`. The short version:

1. **One hour before:** start and check (above), arrange the browser windows (PDF step 12), run the
   readiness checklist (PDF Appendix D). Keep the laptop plugged in with sleep off, and quit heavy apps.
2. **Five minutes before:** accept the certificate warnings in every browser window, including the
   Guest window (Guest windows forget them).
3. **At 0:00:** the Driver types `capture_old`. It saves a session token that will have expired by act 4.
4. **During the demo:** each step in the PDF names the command to type. Keep the `orders-service :8085`
   window visible: it's the live log of every order event (act 2.4).

## Command reference

Type `demo_help` for the short list.

| Command | What it does | Where in the script |
|---|---|---|
| `start_stack` / `start_stack -SkipBuild` | Starts every service and both web apps with the demo settings, waits until UP | Setup |
| `stop_stack` | Stops everything | Setup |
| `health` | UP or DOWN for each of the four services | Any time |
| `listening` | Which ports are listening, and on which address | Appendix A (network) |
| `totals` | Client A's cash, filled orders, all orders and holdings | 4.0, 4.2, 4.4, 4.6 |
| `last_order` / `last_order 3` | Client A's newest order(s) | 4.1 |
| `trace 62` | One order's full record and every audit step | 3.3 |
| `try_change 62` | Tries to change a final order and delete its audit trail; both are refused | 3.4 |
| `reports` | The nightly report summary | 5.3 |
| `backup_now` / `backups` | Takes a database backup / lists them | Setup, 3.4 |
| `capture_old` | Saves a session token to test expiry later | 0.1 |
| `exec_pause` / `exec_resume` | Stops / restarts order execution | 4.1 |
| `restart_orders` | Closes the orders-service window and starts a new one | 4.1 |
| `bad_deploy` | Starts orders-service with a wrong database password, so it fails | 4.2 |
| `deploy_status` | Shows orders-service is down and why | 4.2 |
| `rollback` | Starts orders-service normally again | 4.2 |
| `tokens` | Expired, valid, tampered and signed-out tokens; prints each HTTP status | 4.3 |
| `fault_on` / `fault_check` / `fault_off` | Makes Client A's settlements fail part-way / counts failed attempts / back to normal | 4.4 |
| `feed_down` / `feed_reset` | Takes the price feed down / brings it back | 4.5 |
| `feed_stale` | Makes every quote stale (only for re-creating a rejected order) | Prep |
| `all_clear` | Puts every test switch back to normal and shows health | Rescue |
| `restore_demo_db` | Replaces the database with the demo copy (all changes since are lost) | Setup, rescue |
| `run_overnight_now` | Runs the overnight report now, only if the reports page has no Ready run | Prep |

If a reviewer asks what a command really does, show its code: `(Get-Command fault_on).Definition`.

## If something goes wrong

| Symptom | Fix |
|---|---|
| "The term 'totals' is not recognized" | This window hasn't loaded the commands: `. .\demo\demo.ps1` from the repo folder |
| "running scripts is disabled on this system" | `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass`, then load again |
| "Repo not found at ..." when loading | Load it with `. .\demo\demo.ps1` from the repo folder. For a copy kept outside the repo: `$env:LMJ_REPO = 'C:\path\to\LeMarketJames'`, then load again |
| Quotes missing or frozen | `feed_reset` |
| Client A's orders stay PENDING | `fault_off` |
| No order fills | `exec_resume`. If orders show DELAYED: `stop_stack`, then `start_stack -SkipBuild` from a window with `demo.ps1` loaded |
| orders-service down after the failed-deploy test | `rollback` |
| A service window was closed | `stop_stack`, then `start_stack -SkipBuild` |
| A browser was signed out | Sign in again: that's the 5-minute idle timeout (BR-03) working |
| Anything else | `all_clear` |
| Demo data messed up by rehearsals | `stop_stack`, `restore_demo_db`, `start_stack -SkipBuild`: back to the copy exactly |
| `restore_demo_db` says the role "lemarket" doesn't exist | Run `.\scripts\windows\setup-db.ps1` first |

## Mac fallback

If Windows misbehaves close to the demo, switch to the Mac rather than debugging live. The Mac runs
the Docker stack, with Kafka.

```bash
cd ~/LeMarketJames
source demo/demo.sh     # once per terminal
start_stack             # demo settings: market hours off, 5-minute timeout
health
```

Follow `LEAP_Demo_Script.pdf`. The command names are the same as on Windows, plus `kafka_feed`
(the live event feed) and `logs`. The Mac also needs the repo's git-ignored `.env` with real
secrets; Johan's Mac already has one.

## What was tested

- **The demo database copy:** restored into a PostgreSQL set up the way `setup-db.ps1` sets one up.
  All data came across, the app login can read but not delete audit records, and the trade-record
  locks refuse changes even for the owner.
- **`demo.ps1`** in PowerShell 7.4 against the running stack: `health`, every test switch,
  `capture_old`, `tokens`, `all_clear`, `totals`, `last_order`, `trace`, `try_change` and `reports`.
- **`demo.sh` and every act 4 test** on the Mac stack.
- **Not yet tested on real Windows:** `start_stack`, `stop_stack`, `restart_orders`, `bad_deploy`,
  `rollback` and `run_overnight_now` (they open and close service windows), `restore_demo_db` on
  the laptop's PostgreSQL, and Windows PowerShell 5.1. The Windows rehearsal is where these get
  their first run.
