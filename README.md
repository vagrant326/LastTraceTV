# LastTraceTV

An Android TV app for [The Last Trace](https://ostatnislad.rastwork.pl), a cooperative detective
game for adults. The TV becomes the game's shared screen and players join from their phones with
the QR code. No keyboard, mouse or game controller needed.

## What it does

- **First launch:** shows the game's own 18+ screen. One press of OK on the remote confirms it,
  and the app remembers the answer for good.
- **Every launch:** opens a new room straight away and shows the host screen with its QR code.
  The game is started from the coordinator's phone, not from the TV.
- Keeps the screen on, starts the host's sound without a click, and keeps inside the TV's safe
  area so the room code in the footer is never cropped.

The app is a wrapper only. The game itself, its server and its content belong to its author.

## Installing

The TV installs releases from this repository through a sideloader app:

| Channel | Download |
|---|---|
| Production | `https://github.com/vagrant326/LastTraceTV/releases/download/latest/LastTraceTV.apk` |
| Dev | `https://github.com/vagrant326/LastTraceTV/releases/download/latest-dev/LastTraceTV-dev.apk` |

The dev channel installs as a separate app, *The Last Trace dev*, with an amber accent in its
icon.

## Building

The toolchain runs in the programme's Docker image. From this directory:

```bash
MSYS_NO_PATHCONV=1 docker compose -f ../docker/compose.yaml run --rm -w /work/LastTraceTV dev ./gradlew check lint assembleProdDebug
```

## Releasing

A push to `develop` publishes a dev build, and a push to `main` publishes production. Signing
uses this repository's own keystore through the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD` and
`KEY_PASSWORD` secrets.
