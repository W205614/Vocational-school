import { pathToFileURL, fileURLToPath } from 'node:url';
import fs from 'node:fs';
import path from 'node:path';
import openapiTS, { astToString } from 'openapi-typescript';
const root = fileURLToPath(new URL('../openapi', import.meta.url)),
  target = fileURLToPath(new URL('../packages/shared/src/generated', import.meta.url));
fs.mkdirSync(target, { recursive: true });
const selected = new Set(process.argv.slice(2));
const available = fs.readdirSync(root).filter((f) => f.endsWith('.json'));
for (const name of selected) {
  if (!available.includes(name + '.json')) throw new Error('Unknown OpenAPI service: ' + name);
}
for (const file of available.filter(
  (f) => !selected.size || selected.has(f.replace('.json', '')),
)) {
  const ast = await openapiTS(pathToFileURL(path.join(root, file)));
  fs.writeFileSync(path.join(target, file.replace('.json', '.ts')), astToString(ast));
  console.log('Generated ' + file);
}
