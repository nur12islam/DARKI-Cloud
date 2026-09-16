# DARKI Cloud

> A personal cloud storage application with a Google Drive / Google Photos-style experience, designed to work on Android devices without Google Play Services.

## Vision

DARKI Cloud provides a virtual cloud filesystem while using Telegram as the remote storage layer. Telegram remains an implementation detail; folders, metadata, search, previews, synchronization, and the user experience belong to DARKI Cloud.

## Target Environment

- Primary target: Lenovo Tab 6 A101LV
- OS: LineageOS 15 / Android 15
- Google Play Services: not required
- Google Apps: not required
- Development device: iQOO Z10x

## Planned Features

- Telegram-based authentication
- Drive-style folders and files
- Upload and download
- Image, PDF, text, audio, and video previews
- Photos timeline and albums
- Global search
- Multi-device synchronization
- Offline cache
- Storage and sync status
- Apple-inspired Liquid Glass visual language

## Architecture

```text
Android App
    |
    | HTTPS
    v
DARKI Cloud Backend
    |
    +---- Metadata Database
    |
    +---- Telegram Storage
```

DARKI Cloud owns the virtual filesystem. Telegram stores the actual file objects.

## Security

Secrets must never be committed to this repository. Telegram credentials, API secrets, backend secrets, database credentials, session strings, and signing keys must remain outside source control.

## Development

The project is being developed incrementally. Each subsystem should compile and be tested before the next subsystem is introduced.

See [`docs/DARKI-CLOUD.md`](docs/DARKI-CLOUD.md) for the product specification and [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the technical architecture.

## License

MIT License. See [`LICENSE`](LICENSE).
