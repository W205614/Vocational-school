export function questionOptions(value:unknown):string[]{
 if(value==null)return [];
 if(!Array.isArray(value))throw new Error('题目选项格式无效，请重新加载');
 return value;
}
