# LandClaims

LandClaims is a Paper land claiming plugin that protects land by chunks. It uses a charged golden hoe claim tool, configurable flags, player and admin claims, SQLite or MySQL/MariaDB storage, MiniMessage messages, and optional economy over-limit claiming.

This branch is an MVP foundation. Some documented commands and flows describe the intended MVP experience while implementation continues.

## Requirements

- Paper 26.1.2
- Java 25
- Optional: VaultUnlocked for paid over-limit claiming
- Optional: LuckPerms for permission management

## Install

1. Download the latest `LandClaims` jar from GitHub Releases.
2. Place the jar in your Paper server `plugins` folder.
3. Restart the server.
4. Edit generated files in `plugins/LandClaims/`.
5. Run `/claims admin reload` after configuration changes.

## Quick Start

- Craft the claim tool.
- Right-click two chunks with the tool.
- Confirm the claim setup flow.
- Use `/claims` or sneak + swap hand to manage claims.
