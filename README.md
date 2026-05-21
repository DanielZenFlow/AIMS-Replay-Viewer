# AIMS Replay Viewer

AIMS Replay Viewer is a standalone recorder and browser-based viewer for the
MAvis Hospital domain. It is meant for debugging MAvis clients without asking
other people to adapt their own JSON generation code.

The core idea is simple: the MAvis server already speaks a stable text protocol
with every Hospital client. This tool sits between `server.jar` and the user's
normal client, forwards the protocol in both directions, records what happened,
and writes a replay JSON file that can be inspected in a local HTML viewer.

## What This Tool Does

AIMS Replay Viewer can:

- run the real MAvis `server.jar` with a user's existing client;
- record the actual server/client interaction;
- save replay JSON under this tool folder, not inside the user's source tree;
- group replay files by level name;
- generate an HTML viewer that opens directly in a browser;
- convert an existing level plus action log into replay JSON;
- run a small built-in self-check example.

It does not require users to change, import, or compile against this project.
Their client only needs to run through the standard MAvis server protocol.

## Intended Use Case

This is useful when a developer has a Hospital-domain client that already runs
with commands such as:

```cmd
java -jar server.jar -l levels\SAsimple0.lvl -c "java -Xmx4g -cp target\classes mapf.client.Client" -g -s 500 -t 180
```

Instead of asking the user to produce replay JSON directly, AIMS Replay Viewer
observes the existing run and produces the JSON externally.

## Requirements

For the one-click runner, the user's project should have this simple layout:

```text
user-project/
  server.jar
  target/
    classes/
  levels/
  complevels/
```

The default client command is:

```cmd
java -Xmx4g -cp <project-root>\target\classes mapf.client.Client
```

The default server arguments are:

```cmd
-g -s 500 -t 180
```

The runner also sets:

```cmd
MAVIS_TIMEOUT_MS=180000
```

## Quick Start

Download or clone this repository, then run:

```cmd
run.cmd
```

On the first run, the tool asks for one value:

```text
Enter MAvis Hospital client project root:
```

Enter the folder that contains the user's `server.jar`, `target/classes`,
`levels/`, and `complevels/`.

After that, the tool shows a confirmation menu:

```text
Run? [Y]es / [L]evel / [C]onfig / [Q]uit:
```

Options:

```text
Y  run with the shown configuration
L  choose another level by name or full .lvl path
C  change the saved configuration
Q  exit without running
```

Level names are searched in:

```text
<project-root>\levels
<project-root>\complevels
```

For example, typing `SAsimple0` finds `SAsimple0.lvl` if it exists in either
folder. Other folders, such as `complevels26`, are not scanned by default.

## Opening the Viewer

Run:

```cmd
open-viewer.cmd
```

If a viewer has already been generated, this opens it. If no viewer exists yet,
the script creates an empty viewer first and then opens it.

The generated viewer entry point is:

```text
AIMS-Replay-Viewer.html
```

The generated viewer assets are:

```text
viewer-assets/
  latest-replay.js
  viewer.css
  viewer.js
```

The viewer supports:

- loading the latest generated replay automatically;
- loading another replay JSON file manually;
- drag-and-drop JSON loading;
- step-by-step playback;
- play/pause;
- playback speed control;
- object tracking by agent or box;
- coordinate highlighting;
- basic reachability inspection;
- suspicious event listing.

## Output Layout

Replay JSON is written under this standalone tool folder:

```text
replays/
  <level-name>/
    <level-name>__<timestamp>__<outcome>__<steps>-steps.json
```

Example:

```text
replays\brAIn\brAIn__2026-05-19T07-23-35__partial__722-steps.json
```

This keeps replay artifacts out of the user's source project.

## How It Works

During a one-click run, the process layout is:

```text
server.jar <-> AIMS proxy-client <-> user client
```

The proxy client:

1. starts the user's normal client command;
2. receives the level from `server.jar`;
3. forwards the level to the user's client;
4. reads each joint action from the user's client;
5. forwards the action to `server.jar`;
6. reads the server's `true|false` response;
7. forwards the response back to the user's client;
8. records the accepted actions and board states;
9. writes replay JSON;
10. refreshes the HTML viewer.

Because the tool observes the official server protocol, it does not need direct
access to the user's internal planner code.

## Commands

Interactive one-click runner:

```cmd
run.cmd
```

Run a specific level without prompts:

```cmd
run.cmd SAsimple0 --project-root C:\path\to\user-project --yes
```

Create an empty viewer:

```cmd
java -jar aims-replay-viewer.jar init-viewer --viewer-dir .
```

Convert a level plus action log into replay JSON:

```cmd
java -jar aims-replay-viewer.jar convert ^
  --level examples\SAsimple0.lvl ^
  --actions examples\SAsimple0.actions.txt ^
  --out-dir replays ^
  --viewer-dir .
```

Record a client without the real server, using the built-in Hospital semantics:

```cmd
java -jar aims-replay-viewer.jar record ^
  --level examples\SAsimple0.lvl ^
  --client "powershell -NoProfile -ExecutionPolicy Bypass -File examples\simple-client.ps1" ^
  --client-cwd . ^
  --out-dir replays ^
  --viewer-dir .
```

Run the proxy directly as a `server.jar -c` client command:

```cmd
java -jar aims-replay-viewer.jar proxy-client ^
  --client "java -Xmx4g -cp C:\path\to\user-project\target\classes mapf.client.Client" ^
  --client-cwd C:\path\to\user-project ^
  --out-dir replays ^
  --viewer-dir .
```

For Windows quoting, prefer `run.cmd`.

## JSON Replay Contents

The generated replay JSON contains:

- schema version;
- generation timestamp;
- summary information;
- level metadata;
- walls;
- goals;
- agent colors;
- box colors;
- frame-by-frame agent positions;
- frame-by-frame box positions;
- canonical action strings;
- accepted/rejected action results.

The viewer uses this JSON directly. The user client does not need to know the
JSON format.

## Examples

The `examples/` folder is only a small self-check fixture:

```text
examples\SAsimple0.lvl
examples\SAsimple0.actions.txt
examples\simple-client.ps1
examples\proxy-client-example.cmd
```

It is not required when recording a real user project.

## Action Log Format

Use one joint action per line:

```text
Move(S)
NoOp|Move(E)
Push(E,E)|Pull(N,W)
```

Blank lines and lines starting with `#` are ignored.

Action callouts such as `Move(N)@message` are accepted; the message is ignored
by the converter.

## Build From Source

This project uses Java 17 and Maven.

Build:

```cmd
mvn -q -DskipTests package
```

The Maven build writes:

```text
target\aims-replay-viewer-0.1.0.jar
```

For a simple release folder, copy that jar to:

```text
aims-replay-viewer.jar
```

## Generated Files

These files are generated locally and are intentionally ignored by git:

```text
target/
config.properties
replays/
logs/
AIMS-Replay-Viewer.html
viewer-assets/
```

`config.properties` stores the local project root chosen during setup. It should
not be committed because it is specific to one machine.

## Troubleshooting

If `run.cmd` cannot find `server.jar`, check that the configured project root is
the folder that directly contains `server.jar`.

If the client cannot start, check that the user's project has already been
compiled and that `target/classes` exists.

If `open-viewer.cmd` opens an empty viewer, no replay has been recorded yet.
Run `run.cmd` first, or load a replay JSON manually from the viewer.

If Windows quoting becomes painful, use the one-click runner instead of calling
`proxy-client` manually.
