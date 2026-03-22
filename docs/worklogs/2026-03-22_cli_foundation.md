# 2026-03-22 CLI Foundation

## What
- 공식 CLI 계열에 대응하는 Kotlin console runtime foundation을 추가한다.
- `AgentLoader`와 `StaticAgentLoader`를 통해 app/model/runtime dependencies를 로드하는 surface를 만든다.
- `AdkCli.run(...)`과 `AdkCli.runInteractive(...)`를 추가해 non-streaming/streaming console execution과 session reuse를 지원한다.
- reflection 기반 `main(args)` entrypoint를 추가해 외부 JVM app이 loader class 하나만 주면 CLI를 실행할 수 있게 한다.
- CLI event formatting과 interactive session reuse를 검증하는 테스트를 추가한다.

## Why
- 공식 ADK는 CLI와 web/dev server가 모두 runner의 상위 surface로 존재한다.
- 지금 `adk-kotlin`은 core runtime은 갖췄지만, 사람이 직접 실행하고 살펴보는 dev-facing entrypoint가 없다.
- streaming runner foundation 위에 console CLI를 먼저 올리면, 다음 단계인 HTTP/SSE dev server도 같은 formatting/load/session semantics를 재사용할 수 있다.
