#!/usr/bin/env bash
set -euo pipefail
mkdir -p docs/media

curl -fL --retry 3 --retry-delay 2 'https://media.canva.com/v2/image-resize/format:PNG/height:1040/quality:100/uri:ifs%3A%2F%2FM%2Ffa8a6a53-1723-451d-bb85-1c22f746b02f/watermark:F/width:1920?csig=AAAAAAAAAAAAAAAAAAAAAGhwSxfCGwzH01FpPUXXis74CJjKNkcsH5pCoVdRa5JP&exp=1787821715&osig=AAAAAAAAAAAAAAAAAAAAABFTbB4VTy4TndL0IMQHinYB4Df4IlL4XqPRCCOOL-ny&signer=media-rpc&x-canva-quality=thumbnail' -o docs/media/app-dashboard.png
curl -fL --retry 3 --retry-delay 2 'https://media.canva.com/v2/image-resize/format:PNG/height:1040/quality:100/uri:ifs%3A%2F%2FM%2Ff4570d1e-5bab-470c-b52f-1ee7845ec56f/watermark:F/width:1920?csig=AAAAAAAAAAAAAAAAAAAAAOYcCtGaKvMP2Cn8F9zGiuWTqrBn_bR6dYQUIEmigawV&exp=1787822511&osig=AAAAAAAAAAAAAAAAAAAAAEzn2yqmeEdeN1JYlMGW7SlNmeLbgNHkov3JMBirYC9F&signer=media-rpc&x-canva-quality=thumbnail' -o docs/media/app-create-study-set.png
curl -fL --retry 3 --retry-delay 2 'https://media.canva.com/v2/image-resize/format:PNG/height:1040/quality:100/uri:ifs%3A%2F%2FM%2F040c211c-fd1d-4bb7-bf10-84bc402c1195/watermark:F/width:1920?csig=AAAAAAAAAAAAAAAAAAAAAFzmrMT6unfjy_a37q0WWf1JQCDpUxf7zZ_h9j3M4CAX&exp=1787821241&osig=AAAAAAAAAAAAAAAAAAAAAGXxbhTyiihq5y7JPQ4VIuB4W8GsYyGp-Tps74L78JBY&signer=media-rpc&x-canva-quality=thumbnail' -o docs/media/app-practice-settings.png
curl -fL --retry 3 --retry-delay 2 'https://media.canva.com/v2/image-resize/format:PNG/height:1040/quality:100/uri:ifs%3A%2F%2FM%2F4160b72b-fde5-47aa-a0f3-b6c3cfefa140/watermark:F/width:1920?csig=AAAAAAAAAAAAAAAAAAAAALP0U_Rkckt_Bvp18sXxeuh1VgsrdmP3kmxUuusyaTaE&exp=1787824198&osig=AAAAAAAAAAAAAAAAAAAAAHvib9b1ufDyxTdEcs80uxOrvTSjWmEDzAIgLkcagl7f&signer=media-rpc&x-canva-quality=thumbnail' -o docs/media/app-feedback-question.png
curl -fL --retry 3 --retry-delay 2 'https://media.canva.com/v2/image-resize/format:PNG/height:1040/quality:100/uri:ifs%3A%2F%2FM%2F3fbd8c28-574c-4691-862f-9d48adf37c00/watermark:F/width:1920?csig=AAAAAAAAAAAAAAAAAAAAAJntuowuSTO7s90AD1_6Slr1vMHdpOk6Z5ZFfmpm1IRp&exp=1787821494&osig=AAAAAAAAAAAAAAAAAAAAAPcUqQIDpU88KM82H7e22MtRapDDBkD2rntmn7tj1jId&signer=media-rpc&x-canva-quality=thumbnail' -o docs/media/app-feedback-review.png
curl -fL --retry 3 --retry-delay 2 'https://media.canva.com/v2/image-resize/format:PNG/height:960/quality:100/uri:ifs%3A%2F%2FM%2F5cc762eb-1e38-4765-a9bf-126b01063439/watermark:F/width:1920?csig=AAAAAAAAAAAAAAAAAAAAAIS64uDhTCrd68jHSxhvk_yqY0qbaDr1ErRbJdF19Y-b&exp=1787821327&osig=AAAAAAAAAAAAAAAAAAAAAHuU1CcEezbFgCDyi0UxcKGc7EdB5h1DBFWSKuewD1-o&signer=media-rpc&x-canva-quality=thumbnail' -o docs/media/browser-live-quiz.png

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
    if len(data) < 50_000:
        raise SystemExit(f'{filename} is unexpectedly small')
PY
