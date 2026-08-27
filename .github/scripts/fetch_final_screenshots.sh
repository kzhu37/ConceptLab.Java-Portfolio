#!/usr/bin/env bash
set -euo pipefail
mkdir -p docs/media

curl -fL --retry 3 --retry-delay 2 'https://media.canva.com/v2/image-resize/format:PNG/height:1040/quality:100/uri:ifs%3A%2F%2FM%2Ffa8a6a53-1723-451d-bb85-1c22f746b02f/watermark:F/width:1920?csig=AAAAAAAAAAAAAAAAAAAAAGsZ0ZJJ83XAj_f78l3qpR-CFkoB3a-PycGC4khTAXNS&exp=1787857715&osig=AAAAAAAAAAAAAAAAAAAAAMtN8SYOIDavKrA5HRO6-86YlZJVvf8ubTUrVa571BNe&signer=media-rpc&x-canva-quality=thumbnail' -o docs/media/app-dashboard.png
curl -fL --retry 3 --retry-delay 2 'https://media.canva.com/v2/image-resize/format:PNG/height:1040/quality:100/uri:ifs%3A%2F%2FM%2Ff4570d1e-5bab-470c-b52f-1ee7845ec56f/watermark:F/width:1920?csig=AAAAAAAAAAAAAAAAAAAAAMl0jSNX26Ij3gq7hX-tf6pf9CGMpSWfJFy9pCuWIqZW&exp=1787858511&osig=AAAAAAAAAAAAAAAAAAAAAJ99-pYASA4-4A3HggcXqDXw7lfpjkzFmZVIaC6wLmEl&signer=media-rpc&x-canva-quality=thumbnail' -o docs/media/app-create-study-set.png
curl -fL --retry 3 --retry-delay 2 'https://media.canva.com/v2/image-resize/format:PNG/height:1040/quality:100/uri:ifs%3A%2F%2FM%2F040c211c-fd1d-4bb7-bf10-84bc402c1195/watermark:F/width:1920?csig=AAAAAAAAAAAAAAAAAAAAAFQPc8eOxh7Fqp-H-n4jgIR-pIdiHSvwqHXn94tP00n_&exp=1787860841&osig=AAAAAAAAAAAAAAAAAAAAABcKE6kbJnhABRjC42swkI2uqgFC17ncoamXAeNKm5wS&signer=media-rpc&x-canva-quality=thumbnail' -o docs/media/app-practice-settings.png
curl -fL --retry 3 --retry-delay 2 'https://media.canva.com/v2/image-resize/format:PNG/height:1040/quality:100/uri:ifs%3A%2F%2FM%2F4160b72b-fde5-47aa-a0f3-b6c3cfefa140/watermark:F/width:1920?csig=AAAAAAAAAAAAAAAAAAAAACrjZO0oTitwCghuHthYyBaWKin2_GBMZIWeV6SIILJE&exp=1787860198&osig=AAAAAAAAAAAAAAAAAAAAAE9txrvox3WK_yogTMFPNLpN0V-PKTKiLvqm4y34z-h0&signer=media-rpc&x-canva-quality=thumbnail' -o docs/media/app-feedback-question.png
curl -fL --retry 3 --retry-delay 2 'https://media.canva.com/v2/image-resize/format:PNG/height:1040/quality:100/uri:ifs%3A%2F%2FM%2F3fbd8c28-574c-4691-862f-9d48adf37c00/watermark:F/width:1920?csig=AAAAAAAAAAAAAAAAAAAAAIjhAKJpZSuGgEAU_5sYPVYmZku9BKKZobFwqmaN7Zs_&exp=1787857494&osig=AAAAAAAAAAAAAAAAAAAAADWWF9F1jYU9k5gH3X35o1UkdLvpDsBRvB7nAA-EzG9d&signer=media-rpc&x-canva-quality=thumbnail' -o docs/media/app-feedback-review.png
curl -fL --retry 3 --retry-delay 2 'https://media.canva.com/v2/image-resize/format:PNG/height:960/quality:100/uri:ifs%3A%2F%2FM%2F5cc762eb-1e38-4765-a9bf-126b01063439/watermark:F/width:1920?csig=AAAAAAAAAAAAAAAAAAAAAMPJU8wG0f31V0TSCYVkAUQMaWgM7t39v3Cug2_8k_du&exp=1787860927&osig=AAAAAAAAAAAAAAAAAAAAAJ-Ap09LVbabfDHpzXs42-otUPMQhqSNv1fFalzP8ptb&signer=media-rpc&x-canva-quality=thumbnail' -o docs/media/browser-live-quiz.png

python3 - <<'PY'
from pathlib import Path
import struct
expected = {
    'docs/media/app-dashboard.png': (1920, 1040),
    'docs/media/app-create-study-set.png': (1920, 1040),
    'docs/media/app-practice-settings.png': (1920, 1040),
    'docs/media/app-feedback-question.png': (1920, 1040),
    'docs/media/app-feedback-review.png': (1920, 1040),
    'docs/media/browser-live-quiz.png': (1920, 960),
}
for filename, dimensions in expected.items():
    data = Path(filename).read_bytes()
    if len(data) < 24 or data[:8] != b'\x89PNG\r\n\x1a\n':
        raise SystemExit(f'{filename} is not a valid PNG')
    width, height = struct.unpack('>II', data[16:24])
    print(f'{filename}: {width}x{height}, {len(data)} bytes')
    if (width, height) != dimensions:
        raise SystemExit(f'{filename} has unexpected dimensions {(width, height)}')
    if len(data) < 25_000:
        raise SystemExit(f'{filename} is unexpectedly small')
PY
