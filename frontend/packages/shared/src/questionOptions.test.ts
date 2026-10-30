import {describe,it,expect} from 'vitest';
import {questionOptions} from './questionOptions';

describe('question options API boundary',()=>{
 it('keeps decoded options',()=>{const options=['甲','乙'];expect(questionOptions(options)).toBe(options);});
 it('allows absent options for judgment and subjective questions',()=>{expect(questionOptions(null)).toEqual([]);expect(questionOptions(undefined)).toEqual([]);});
 it('rejects the obsolete encoded string format',()=>{expect(()=>questionOptions('["甲","乙"]')).toThrow('题目选项格式无效');});
 it('rejects an invalid response instead of displaying no choices',()=>{expect(()=>questionOptions({})).toThrow('题目选项格式无效');});
});
