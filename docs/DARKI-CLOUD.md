# DARKI Cloud — Product Specification

## 1. Purpose

DARKI Cloud is a personal cloud storage application designed to provide a Google Drive / Google Photos-style experience on Android devices that do not have Google Play Services.

The primary target is the Lenovo Tab 6 A101LV running LineageOS 15 / Android 15 without GApps.

## 2. Core Principle

DARKI Cloud owns the virtual filesystem. Telegram is used as a remote storage layer for file objects.

A folder in DARKI Cloud is metadata, not a Telegram channel. Do not create a Telegram channel for every folder.

## 3. User Experience

The user should experience:

- Home dashboard
- My Drive
- Photos
- Search
- Settings
- Upload and download
- File previews
- Folders and albums
- Multi-device synchronization
- Offline cached files
- Storage and sync indicators

Telegram should be mostly invisible during normal use.

## 4. Authentication

Authentication is based on Telegram. Google Sign-In, Firebase, Google Drive and Google Photos are not required.

Authentication secrets and Telegram credentials must remain server-side or in secure runtime configuration and must never be committed to the public repository.

## 5. File Operations

Required operations:

- Create folder
- Rename
- Move
- Copy
- Upload
- Download
- Delete
- Open
- Preview
- Search
- Share

## 6. Preview

Initial preview targets:

- Images
- PDF
- Plain text
- Audio
- Video

Other formats may be downloaded and opened with an external application until dedicated rendering is added.

## 7. Photos

Photos should provide a timeline, albums, image grid, fullscreen viewing, video playback, favorites, thumbnails and metadata-based search.

Albums are virtual collections in the DARKI Cloud metadata database.

## 8. Synchronization

Changes made on one device should become available on another device through the backend.

The sync engine must handle uploads, deletions, renames, moves, folder creation and metadata changes. It must provide retry behavior and explicit sync states.

## 9. Offline Mode

The client maintains a local cache of metadata and user-selected/downloaded files. Pending operations are synchronized when connectivity returns.

## 10. UI Direction

The interface uses an Apple-inspired Liquid Glass visual language:

- Translucent surfaces
- Background blur
- Soft borders
- Rounded corners
- Subtle depth
- Smooth motion
- Dark appearance
- Strong readability

The design should avoid excessive decoration that harms performance on the target tablet.

## 11. Development Rule

Build incrementally. Do not generate the whole system blindly. Each major subsystem must compile and be tested before the next subsystem is introduced.
