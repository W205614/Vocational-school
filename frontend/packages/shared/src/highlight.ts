export type TitlePart={text:string;highlight:boolean};

function decodedText(text:string):string {
 const entities:Record<string,string>={amp:'&',lt:'<',gt:'>',quot:'"',apos:"'"};
 return text.replace(/&(amp|lt|gt|quot|apos|#\d+|#x[0-9a-f]+);/gi,(entity:string,name:string)=>{
  const key=name.toLowerCase();
  if(entities[key]!==undefined)return entities[key]!;
  const number=key.startsWith('#x')?Number.parseInt(key.slice(2),16):Number.parseInt(key.slice(1),10);
  return number>0 && number<=0x10ffff && !(number>=0xd800 && number<=0xdfff)?String.fromCodePoint(number):entity;
 });
}

// Only the highlighter's exact <em> delimiters affect formatting. Every other
// character remains Vue text, including decoded HTML and user-supplied tags.
export function titleParts(value:string|undefined,highlighted:boolean):TitlePart[] {
 const text=value||'';
 if(!highlighted)return [{text,highlight:false}];
 let active=false;const parts:TitlePart[]=[];
 for(const part of text.split(/(<em>|<\/em>)/g)) {
  if(part==='<em>')active=true;
  else if(part==='</em>')active=false;
  else if(part)parts.push({text:decodedText(part),highlight:active});
 }
 return parts;
}

export function plainTitle(value:string|undefined,highlighted:boolean):string {
 return titleParts(value,highlighted).map(part=>part.text).join('');
}
