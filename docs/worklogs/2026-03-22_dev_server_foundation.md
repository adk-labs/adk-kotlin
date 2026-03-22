# 2026-03-22 Dev Server Foundation

## What
- 공식 web/dev surface에 대응하는 `AdkWebServer` foundation을 추가한다.
- `AgentLoader`를 재사용해 app 목록, session lifecycle, JSON `/run`, SSE `/run_sse` endpoint를 노출한다.
- web request/response DTO와 event/session payload serializer를 추가한다.
- local loopback HTTP 테스트로 session 생성/조회, non-streaming run, SSE run을 검증한다.

## Why
- 공식 ADK는 CLI만이 아니라 dev server와 web UI가 핵심 상위 surface다.
- 이미 `Runner`, session store, event streaming foundation이 있으므로, 그 위에 최소 web API를 올리는 것이 현재 gap을 가장 빠르게 줄인다.
- JSON run endpoint와 SSE endpoint를 먼저 맞춰 두면 이후 browser UI, conformance, recordings 쪽을 같은 contract 위에서 확장할 수 있다.
