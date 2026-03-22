# 2026-03-22 Multimodal Tool Results Plugin

## What
- 공식 `multimodal_tool_results_plugin`에 대응하는 Kotlin plugin을 추가한다.
- 한 invocation 안에서 tool callback과 model request callback이 비문자 객체를 공유할 수 있도록 ephemeral storage를 넣는다.
- attachment-bearing tool result를 수집해 다음 model request의 마지막 conversation message로 재주입한다.
- plugin이 실제로 multimodal attachment를 다음 model call로 전달하는 테스트를 추가한다.

## Why
- 공식 ADK는 tool이 반환한 multimodal part를 다음 model context에 직접 연결하는 plugin을 제공한다.
- 현재 `adk-kotlin`은 session state가 `Map<String, String>`이라 attachment 같은 비문자 payload를 plugin 사이에서 넘길 방법이 없다.
- CLI/dev server 이전에 multimodal tool chaining semantics를 맞춰 두면 provider adapter 구현이 훨씬 단순해진다.
