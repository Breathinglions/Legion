# PrideQuest

Server-side quest and progression engine for Cobblemon on Fabric 1.21.1.

## v0.2 foundation

- Multiple active quests with one tracked bossbar objective
- Story, side, daily, weekly, endgame and event categories
- Completed quest history and prerequisites
- Repeatable quest reset windows (daily / weekly)
- Weighted quest pools for rotating content
- Server-side location objectives
- Structured rewards plus v0.1 completion-command compatibility
- Config validation
- Atomic player-state saves with `.bak` backups
- Automatic migration from v0.1 single-quest player state
- Smaller objective-complete presentation
- `/q` player tracking commands and expanded `/pq` admin tools

Cobblemon event hooks currently include starter choice, Pokémon capture and Apricorn harvesting.
RCT, tournaments, economy and richer Pokémon filters are planned integration layers.
