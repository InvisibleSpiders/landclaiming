# LandClaims

LandClaims is a Paper land claiming plugin that protects land by chunks. It uses a charged golden hoe claim tool, configurable flags, player and admin claims, SQLite or MySQL/MariaDB storage, MiniMessage messages, and optional economy over-limit claiming.

This branch is an MVP foundation. The current build includes the shell for claim-tool selection and the service/configuration groundwork for claims, protection, dialogs, storage, permissions, and admin management. Full management menus, confirmation dialogs, and admin command flows are scaffolded and planned for the next implementation steps.

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
5. Restart the server after configuration changes. Runtime reload via `/claims admin reload` is planned but not complete in this build.

## Quick Start

- Run `/claims tool` to receive the claim tool.
- Right-click two chunks with the tool to record selection corners.
- Claim confirmation dialogs, `/claims` management menus, sneak + swap hand shortcuts, and admin commands are coming next.
