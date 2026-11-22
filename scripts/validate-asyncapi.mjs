import { createRequire } from 'node:module';
import { readFileSync } from 'node:fs';
const require = createRequire(process.argv[2] + '/package.json');
const { Parser, DiagnosticSeverity } = require('@asyncapi/parser');
const parser = new Parser();
for (const file of process.argv.slice(3)) {
  const { document, diagnostics } = await parser.parse(readFileSync(file, 'utf8'));
  const errors = diagnostics.filter(d => d.severity === DiagnosticSeverity.Error);
  const warnings = diagnostics.filter(d => d.severity === DiagnosticSeverity.Warning);
  const valid = document !== undefined && errors.length === 0;
  if (!valid) process.exitCode = 1;
  console.log(`== ${file.split('/').slice(-4).join('/')}`);
  console.log(`valid=${valid} errors=${errors.length} warnings=${warnings.length}`);
  for (const d of [...errors, ...warnings]) console.log(`  ${d.severity === 0 ? 'ERROR' : 'WARN '} ${d.code}: ${d.message} @ ${d.path.join('.')}`);
  if (document) {
    console.log(`  channels=${document.channels().length} operations=${document.operations().length} messages=${document.allMessages().length}`);
    for (const op of document.operations()) console.log(`  ${op.action().padEnd(7)} ${op.channels()[0]?.address()}  ${op.messages().all().map(m => m.name()).join(', ')}`);
  }
}
