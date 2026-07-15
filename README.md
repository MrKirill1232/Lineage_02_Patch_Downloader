# Lineage 2 Patch Downloader

A command-line downloader for NCSoft **Lineage 2** client patches. It fetches a version's file
list from the configured source, downloads the files (single connection, self-split byte ranges,
or CDN parts — held in memory or streamed through temp files), decompresses them (LZMA / ZIP) and
stores them, with optional post-store hash / size verification.

## Sources (`-cdn`)

| Value               | Region / channel                                             |
|---------------------|--------------------------------------------------------------|
| `NC_SOFT_JAPANESE`  | L2 Japan — `ncj-L2-asset.ncsoft.jp`                          |
| `NC_SOFT_AMERICA`   | L2 North America — `d35293xeakkyq4.cloudfront.net`          |
| `NC_SOFT_TAIWAN`    | L2 Taiwan — `mmorepo.cdn.plaync.com.tw`                     |
| `NC_SOFT_KOREAN`    | L2 Korea — `l2kor.ncupdate.com`                             |
| `UP_NOVA_LAUNCHER`  | UpNova-based private servers (requires `-upnova_url`)        |
| `AKUMU`             | akumu.ru HTTP mirror (torrent-based file list, behind Anubis)|

## Requirements

- **Java 25** (Amazon Corretto 25 or compatible).
- Bundled dependencies in `libs/`: `xz` (LZMA), `dom4j`, `json-simple`, `ConfigFieldParser`.

## Build & run

The runnable jar is produced from the IntelliJ artifact definitions in `.idea/artifacts/`
(`Lineage_02_Patch_Downloader` — thin jar; `..._AIO` — all-in-one with dependencies bundled).

Run with the config file (`work/config/Main.ini`) and/or command-line arguments — CLI arguments
override the config:

```bat
java -jar Lineage_02_Patch_Downloader.jar -cdn NC_SOFT_JAPANESE -version 215
```

Run `-help` for the full argument list.

## Key command-line flags

| Flag                     | Purpose                                                         |
|--------------------------|----------------------------------------------------------------|
| `-cdn <source>`          | Download source (see the table above).                         |
| `-version <n>`           | Patch version to download.                                     |
| `-last_version`          | Query the source's CURRENT version live, print it, and exit.   |
| `-path <dir>`            | Output folder.                                                  |
| `-download_mode <mode>`  | `ALL_MEMORY` / `HYBRID` / `ALL_TEMP` (RAM vs temp-file streaming). |
| `-restore`               | Skip files already present and valid on disk.                  |
| `-hash` / `-size`        | Verify downloaded files by hash / by size.                     |
| `-source_compare <dir>`  | Verify / seed against a local copy before hitting the CDN.     |
| `-help`                  | Print every argument and exit.                                 |

### `-last_version`

Resolves the **current** patch version of an NC source over the NCSoft "Purple" update protocol
(a small binary request over `TCP:27500`) and exits — it downloads nothing. Supported for the NC
regions (TW / KR / JP / NA). Pass `-cdn` before it; it runs from command-line arguments alone, no
config file required:

```bat
java -jar Lineage_02_Patch_Downloader.jar -cdn NC_SOFT_AMERICA -last_version
```

## Configuration

Defaults live in `work/config/Main.ini` (documented inline, EN + RU). Every value can be
overridden on the command line.

---

## Development note

**Version 01.07.01 and later were developed with the help of AI.**
