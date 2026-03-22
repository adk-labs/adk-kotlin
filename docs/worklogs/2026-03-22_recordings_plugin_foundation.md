# 2026-03-22 Recordings Plugin Foundation

## What
- 공식 `RecordingsPlugin`에 대응하는 Kotlin plugin foundation을 추가한다.
- `_adk_recordings_config` session state를 읽어 recording mode를 활성화하고, invocation 단위 LLM/tool interaction을 수집한다.
- recordings와 session snapshot을 JSON 파일로 저장하는 schema를 추가한다.
- recording enabled/disabled 동작과 output file naming을 검증하는 테스트를 추가한다.

## Why
- conformance와 replay 계열 기능은 recordings가 먼저 있어야 의미 있게 올라간다.
- 이미 `Runner`, plugin lifecycle, web server state injection이 있으므로, 지금 recordings를 붙이면 다음 단계의 replay/conformance client를 자연스럽게 이어갈 수 있다.
- state-driven config를 먼저 맞추면 server `/run`과 `/run_sse`에서 official-style로 recording mode를 켤 수 있다.
