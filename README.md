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
- Normal objective completion moved to the compact action bar
- `/q` player tracking commands and expanded `/pq` admin tools
- Bundled starter quest configs are seeded on first boot without overwriting admin-edited files

## Pride Region starter story

The original all-in-one `first_steps` quest is being split into clean story chapters:

1. `first_steps` — starter, Sequoia, Avery, first capture and Apricorn training
2. `road_to_thorn` — travel to Thorn's Grass Gym
3. `thorn_challenge` — defeat Thorn and earn the Grass Badge

Location and Gym-win objectives are temporarily trigger-based until the live Pride Region coordinates and RCT integration are wired in.

## Rotating content foundation

Example `daily_catch_5` and `weekly_apricorn_25` configs are included. They use reset policies, weighted pools and the same Cobblemon event stream as story quests, allowing one capture/harvest event to progress multiple relevant quests at once.

Cobblemon event hooks currently include starter choice, Pokémon capture and Apricorn harvesting.
RCT, tournaments, economy and richer Pokémon filters are planned integration layers.
