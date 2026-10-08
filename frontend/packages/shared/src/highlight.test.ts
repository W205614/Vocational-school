import {expect,test} from 'vitest';
import {plainTitle,titleParts} from './highlight';

test('highlights terms while keeping encoded HTML as text',()=>{
 const input='<em>Java</em> &lt;img src=x onerror=bad&gt; &amp; SQL';
 expect(titleParts(input,true)).toEqual([{text:'Java',highlight:true},{text:' <img src=x onerror=bad> & SQL',highlight:false}]);
 expect(plainTitle(input,true)).toBe('Java <img src=x onerror=bad> & SQL');
});

test('plain course names keep literal markup and entities',()=>{
 const input='<em>Course title</em> &amp; C++';
 expect(titleParts(input,false)).toEqual([{text:input,highlight:false}]);
});

test('untrusted tags and malformed entities never become formatting',()=>{
 expect(titleParts('<em class="bad">x</em> &#x1f600; &#999999999; &unknown;',true)).toEqual([
  {text:'<em class="bad">x',highlight:false},{text:' 😀 &#999999999; &unknown;',highlight:false}
 ]);
});
