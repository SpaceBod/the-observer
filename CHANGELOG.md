# Changelog

All notable changes to Herald will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Initial version.
- A weekly newspaper (configurable period), one edition for everyone, built from every player's vanilla statistics, deaths and advancements, with a Founding Edition covering everything before it. Players who are offline are read from the world's statistics files.
- News desk: scored candidate stories with a varied line-up, firsts (about 60 milestones), new habits and record weeks against each player's history, an "In Brief" column, opinion pieces and almanac items to fill the page.
- Reading screen styled as an open, stapled newspaper in three spreads: the news, the Exchange, and the Leaderboard with the Hall of Fame. No text is ever cut off.
- The Exchange: six notices per reader from named townsfolk of fourteen trades and the Watch, worked out from the edition and the reader's UUID, priced from vanilla trade rates, checked and paid by the server, one hand-in each until the next edition.
- Leaderboards: over 30 candidate boards plus one per creature killed, chosen each edition for activity, close races and variety, since the last edition and of all time.
- A delivery dog (red collar, the paper rolled up in its mouth) that appears out of sight, trots over, drops the paper and runs off; falls back to the inventory when there is no route. Offline players get the paper on their next join.
- Optional on-device writing model (llama.cpp, bundled): a singleplayer download offer, `/herald model download` for servers, a tunable press profile (`config/herald-press.json`: persona, prompt templates, examples, sampling, JSON Schema, checks) and a sub-editor on every story (schema, sanitising, grounding, policy, copying) with a fallback to the templates.
- `/herald`, `/herald reprint`, `/herald publish`, `/herald status`; publish and status also work for the owner of a singleplayer world without cheats.
- Scenario test suite and a readable gallery of generated editions.
- Builds for Minecraft 26.1, 26.2 and 26.3, on Fabric and NeoForge.
