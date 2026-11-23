# Module kardx-annotations

Annotations that kardx reads. No dependencies. Add them to app code (main) as `compileOnly`.

- `io.github.kardx.annotation.Description`: description of a field or parameter. The extraction functions (`responseFieldsOf` and others) use it in tables and schemas
- `io.github.kardx.annotation.Masked`: marks a property whose value is masked in the output (`x-masked`)
- `io.github.kardx.annotation.EnumCode`: an enum to include in the enum code document (`EnumDocumentationExtension` for JUnit and Kotest)
- `io.github.kardx.annotation.AsciiDescription`: put on a test class or method to add a description after the generated AsciiDoc document or section title.
  The value on a test class also becomes the OpenAPI tag description
