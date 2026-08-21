# Performance Report

Date: 2026-07-25

Measurements were collected by Android instrumentation on 2 GB x86_64 AVDs.
`peak_delta_kb` is the process PSS increase observed around rendering, not a
device-wide peak. Times are wall-clock milliseconds.

## API 26

| Document | Source | Bitmap | First | Cached repeat | PSS delta | Zoom 3072 | OOM |
|---|---:|---:|---:|---:|---:|---:|---|
| A4 PDF | 595×842 | 1447×2048 | 34 ms | 0 ms | 76,630 KB | 92 ms | No |
| A3 PDF | 842×1191 | 1448×2048 | 14 ms | 1 ms | 66,512 KB | 54 ms | No |
| A0-like PDF | 2384×3370 | 1449×2048 | 11 ms | 0 ms | 40,512 KB | 24 ms | No |
| Large PNG | 4096×3072 | 2048×1536 | 99 ms | 2 ms | 96,997 KB | 227 ms | No |

Engine stress: 8,000 measurement evaluations in 38 ms; 100 snap searches
against 2,000 measurements in 57 ms.

## API 35

| Document | Source | Bitmap | First | Cached repeat | PSS delta | Zoom 3072 | OOM |
|---|---:|---:|---:|---:|---:|---:|---|
| A4 PDF | 595×842 | 1447×2048 | 18 ms | 1 ms | 31,662 KB | 29 ms | No |
| A3 PDF | 842×1191 | 1448×2048 | 13 ms | 1 ms | 37,576 KB | 27 ms | No |
| A0-like PDF | 2384×3370 | 1449×2048 | 12 ms | 1 ms | 37,592 KB | 25 ms | No |
| Large PNG | 4096×3072 | 2048×1536 | 96 ms | 1 ms | 39,257 KB | 106 ms | No |

Engine stress: 8,000 evaluations in 44 ms; 100 snap searches against 2,000
measurements in 369 ms.

No OOM occurred. The adaptive renderer remains capped at 4096 pixels and keeps a
16 MB LRU cache. Since 2026-08-10 deep zoom no longer relies on a larger page
bitmap: above the page render density the visible rectangle is re-rendered in
512 pt tiles (edge capped at 2048 px, 12 tiles per viewport, 24 MB LRU), so the
peak allocation stays bounded regardless of zoom. The measurements below
supported deferring tiled rendering to P1 for
the tested document sizes; they do not claim unlimited-size support.
