# 2026-03-22 Streaming Runner Foundation

## What
- `Runner`에 실시간 event sink를 전달할 수 있는 streaming 실행 경로를 추가한다.
- 기존 `run(...)` API는 유지하고, `run(..., onEvent = ...)` overload와 `stream(...)` Flow API를 함께 제공한다.
- event emission이 한 군데에서 일어나도록 `emitEvent(...)` 경로에 sink 호출을 결합한다.
- streaming 순서와 최종 `RunResult` 보존을 검증하는 테스트를 추가한다.

## Why
- 공식 ADK 계열의 상위 기능은 event 중심 실행 모델 위에 쌓인다.
- 다음 단계인 CLI, dev server, browser UI는 실행 도중 event를 바로 소비할 수 있어야 구현이 자연스럽다.
- 기존 non-streaming 호출자를 깨지 않고 foundation을 먼저 올려 두는 편이 이후 확장 비용이 낮다.
