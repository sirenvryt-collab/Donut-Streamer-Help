# Donut Streamer Tracker (Fabric, Minecraft 1.21.11)

Client-side session tracker for DonutSMP gambling, in the style of Gambler Plus.
It reads payment messages from chat and keeps your profit automatically. No hotkeys, nothing to click.

## What it reads (chat only)
| Message | Meaning |
|---|---|
| `name paid you $ amount` | money received (counts as a win) |
| `You paid name $ amount` | money sent (counts as a loss) |
| `$ amount` on its own (selling) | sale income, tracked separately |

The space after `$` is optional.

Net profit = received - sent. Sales are shown separately; `/ds sells` (or the HUD editor) adds them to net.
If your server shows sales in the action bar instead of chat, run `/ds sellsource`.
Payment lines must start with the pattern above, so a player typing a fake line in public chat is ignored.

## Doubled payments
If a player pays you and then pays you exactly double without you paying them in between
(doubling after a loss), the payment message in chat gets a gold tag, for example
`[DOUBLED: 10K -> 20K]`, or `[DOUBLED x2 in a row: 10K -> 20K -> 40K]` for a chain.
Paying that player resets it. Toggle with `/ds doubles` or the HUD editor.

## Tracker window
`/ds` opens a window with net profit, record, timer, and a per-player table
(what each player sent you, what you sent them, net, and rakeback owed if `/ds rakeback 5` is set).

## HUD
Separate elements, each on/off: net profit, W-L record, streak, last payment, timer, profit graph,
total received, total sent, sold, biggest in/out, best/worst streak, profit per hour, balance, loss limit.
Open chat (T): drag any element with the left mouse button, and use the **HUD editor** button (top right)
to toggle elements, show net as a % of balance, count sells, and more.

## Alerts
- `/ds tilt 5`: warning after 5 payments sent in a row with nothing received (2-20)
- `/ds limit 500k`: loss limit warning, `/ds goal 2m`: take-profit alert

## Other commands
`/ds in|out <amt> [name]` and `/ds sell <amt>` add something the reader missed, `/ds undo`,
`/ds reset` (twice), `/ds summary` (copies to clipboard), `/ds players`, `/ds balance <amt>`,
`/ds percent`, `/ds names` (hide player names for stream), `/ds sound`, `/ds hud toggle|reset|scale <n>|<element>`.
Amounts accept k/m/b/t (e.g. `1.5m`).

## Safety
No packets sent by the mod, no automation, no input simulation, no world/inventory reads.
It only reads chat you can already see, keeps numbers, and draws a HUD.

## Files
- `config/donutstreamer.json`: session + settings
- `<game dir>/donutstreamer/bets.csv`: every payment, for post-stream review

## Build on GitHub
Upload everything (including `.github`), open the Actions tab, wait for "Build mod", download the
`donutstreamer-jar` artifact, and put the jar in `mods` with Fabric Loader 0.18.x + Fabric API (1.21.11).
