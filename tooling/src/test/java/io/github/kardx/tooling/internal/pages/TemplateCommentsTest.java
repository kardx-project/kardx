package io.github.kardx.tooling.internal.pages;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** 템플릿 주석 지우기({@link TemplateComments}). */
class TemplateCommentsTest {

    @Test
    void removesHtmlCommentsAndCommentOnlyScriptLines() {
        String template = """
            <body>
              <!-- 한 줄 주석 -->
              <!-- 여러 줄
                   주석 -->
              <div>a</div> <!-- 줄 끝 주석 -->
              <style>
                /* 스타일 설명 */
                body { margin: 0; }
              </style>
              <script>
                // 스크립트 설명
                //
                run();
              </script>
            </body>
            """;

        assertEquals("""
            <body>
              <div>a</div>\s
              <style>
                body { margin: 0; }
              </style>
              <script>
                run();
              </script>
            </body>
            """, TemplateComments.strip(template));
    }

    @Test
    void keepsConditionalCommentsUrlsAndCodeWithTrailingSlashes() {
        String template = """
            <!--[if lt IE 9]><script src="https://cdn.example.com/shim.js"></script><![endif]-->
            <!--[if !IE]><!--><p>modern</p><!--<![endif]-->
            <script src="//cdn.example.com/app.js"></script>
            <script>
              const url = 'https://example.com/a//b'; // 줄 끝 주석은 해석하지 않으므로 남는다
              const text = '<!-' + '- not a comment -' + '->';
              //cdn.example.com/protocol-relative
            </script>
            """;

        assertEquals(template, TemplateComments.strip(template));
    }
}
