import {pathToFileURL,fileURLToPath} from 'node:url';import fs from 'node:fs';import path from 'node:path';import openapiTS,{astToString} from 'openapi-typescript';
const root=fileURLToPath(new URL('../openapi',import.meta.url)),target=fileURLToPath(new URL('../packages/shared/src/generated',import.meta.url));fs.mkdirSync(target,{recursive:true});
for(const file of fs.readdirSync(root).filter(f=>f.endsWith('.json'))){const ast=await openapiTS(pathToFileURL(path.join(root,file)));fs.writeFileSync(path.join(target,file.replace('.json','.ts')),astToString(ast));console.log('Generated '+file);}
