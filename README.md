# 🛡️ TubeShield

**Privacy YouTube browser for Android. WebView-based, blocks ads & trackers.**

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## Why?

YouTube tracks everything. The official app loads trackers from Google, DoubleClick, 
and a dozen ad networks before playing a single video.

TubeShield wraps the mobile YouTube site with:
- **Tracker blocking** at the request level
- **Ad removal** via JavaScript injection
- **No permissions** except internet
- **No background services**
- **No telemetry, no analytics, no crash reporters**

## How It Works

`m.youtube.com` → Android WebView → Privacy filters → Clean YouTube

