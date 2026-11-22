// 생성된 OpenAPI 스펙을 Redocly 권장 규칙(recommended)으로 검사한다.
// 사용: node scripts/validate-openapi.mjs <@redocly/openapi-core 를 설치한 디렉터리> <openapi3.yaml> [...]
// 규칙별 error·warning 개수와 error 위치를 출력하고, error 가 하나라도 있으면 종료 코드 1 로 끝난다.
import { createRequire } from 'node:module';
const require = createRequire(process.argv[2] + '/package.json');
const { lint, loadConfig } = require('@redocly/openapi-core');
const config = await loadConfig({});
for (const file of process.argv.slice(3)) {
  const problems = await lint({ ref: file, config });
  const errors = problems.filter(p => p.severity === 'error');
  const warnings = problems.filter(p => p.severity === 'warn');
  if (errors.length > 0) process.exitCode = 1;
  console.log(`== ${file.split('/').slice(-4).join('/')}`);
  console.log(`errors=${errors.length} warnings=${warnings.length}`);
  const byRule = new Map();
  for (const p of problems) {
    const key = `${p.severity === 'error' ? 'ERROR' : 'WARN '} ${p.ruleId}`;
    byRule.set(key, (byRule.get(key) ?? 0) + 1);
  }
  for (const [rule, count] of [...byRule].sort()) console.log(`  ${rule}: ${count}`);
  for (const p of errors) console.log(`  ERROR ${p.ruleId}: ${p.message} @ ${p.location[0]?.pointer}`);
}
