# Module kardx-annotations

kardx 이 읽는 어노테이션. 의존성이 없다. 앱 코드(main)에는 `compileOnly` 로 넣는다.

- `io.github.kardx.annotation.Description`: 필드·파라미터 설명. 추출 함수(`responseFieldsOf` 등)가 표와 스키마에 쓴다
- `io.github.kardx.annotation.Masked`: 값이 마스킹되어 나가는 프로퍼티 표시 (`x-masked`)
- `io.github.kardx.annotation.EnumCode`: Enum 코드 문서에 넣을 Enum (JUnit·Kotest 의 `EnumDocumentationExtension`)
- `io.github.kardx.annotation.AsciiDescription`: 테스트 클래스·메서드에 붙여 생성 AsciiDoc 문서·섹션 제목 뒤에 붙일 설명.
  테스트 클래스의 값은 OpenAPI 태그 설명도 된다
