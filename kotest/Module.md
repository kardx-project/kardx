# Module kardx-kotest

Kotest 스펙에서 Spring REST Docs 스니펫과 스펙별 AsciiDoc 문서를 만드는 확장. Kotest 6.x 용이다
(Kotest 5.x 는 같은 패키지·API 의 `kardx-kotest5`. 둘 중 하나만 넣는다).

- `io.github.kardx.kotest.RestDocsExtension`: 스펙(`extension(RestDocsExtension())`)이나 프로젝트 설정에 등록한다.
  리프 테스트(`then`, `it`, `test`)마다 스니펫을 만들고, 스펙이 끝나면 `<spec-name>.adoc` 을 쓴다.
  `provider` 를 `restDocsMockMvc`·`restDocsWebTestClient`·`MessageDocumentation` 에 넘긴다
- `io.github.kardx.kotest.TestNames`: 테스트 이름에서 섹션 제목과 스니펫 자리 이름을 만드는 기본 규칙
- `io.github.kardx.kotest.EnumDocumentationExtension`: `@EnumCode` Enum 의 코드 참조 문서

DSL(`andDocument { }`)과 추출 함수는 core(`kardx`)의 것을 그대로 쓴다.
