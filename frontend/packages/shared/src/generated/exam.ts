export interface paths {
    "/api/v2/services/exam/questions/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询题目详情 */
        get: operations["queryQuestionDetailById"];
        /** 修改题目 */
        put: operations["updateQuestion"];
        post?: never;
        /** 删除题目 */
        delete: operations["deleteQuestionById"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/exam/questions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 新增题目 */
        post: operations["addQuestion"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/exam/question-biz/list": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 批量保存题目和业务关系 */
        post: operations["saveQuestionBizInfoBatch"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/teacher/exam-attempts/{id}/grades": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["grade"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/exam-papers/{id}/attempts": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["start"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/exam-attempts/{id}/submit": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["submit"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/operation-failures/exam/{id}/replay": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["replay"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/exam-papers": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["publish"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/events/exam/{id}/replay": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["replay_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/consumer-failures/exam/{id}/replay": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["replay_2"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/exam/questions/scores": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询题目分值 */
        get: operations["queryQuestionScores"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/exam/questions/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 分页查询题目 */
        get: operations["queryQuestionByPage"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/exam/questions/numOfTeacher": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询老师出题数量 */
        get: operations["countSubjectNumOfTeacher"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/exam/questions/list": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询题目列表 */
        get: operations["queryQuestionByIds"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/exam/questions/listOfBiz": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询业务关联的题目列表 */
        get: operations["queryQuestionByBizId"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/exam/questions/checkName": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 校验名称是否有效，存在则无效返回false，不存在返回true */
        get: operations["checkNameValid"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/exam/question-biz/scores": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询业务下的题目分数和 */
        get: operations["queryQuestionScoresByBizIds"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/exam/question-biz/biz/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询与业务有关的题目id */
        get: operations["queryQuestionIdsByBizId"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/exam/question-biz/biz/list": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 批量查询与业务有关的题目id */
        get: operations["queryQuestionIdsByBizIds"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/teacher/exam-attempts": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["pending"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/teacher/exam-attempts/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["grading"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/operations/exam/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["get"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/exam-papers": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["papers"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/exam-attempts/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["attempt"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/operation-failures/exam": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/events/exam/failures": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["failures"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/consumer-failures/exam": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        /**
         * @description 考试题目表单实体
         * @default null
         */
        QuestionFormDTO: {
            /**
             * @description 题目id，新增不用填写
             * @default
             */
            id: string;
            /**
             * @description 题目名称，题干
             * @default
             */
            name: string;
            /**
             * Format: int32
             * @description 题目类型，1：单选题，2：多选题，3：不定向选择题，4：判断题，5：主观题
             * @default
             */
            type: number;
            /**
             * @description 课程三级分类的id集合
             * @default
             */
            cateIds: string[];
            /**
             * Format: int32
             * @description 难易度，1：简单，2：中等，3：困难
             * @default
             */
            difficulty: number;
            /**
             * Format: int32
             * @description 分值
             * @default
             */
            score: number;
            /**
             * @description 选择题选项，json数组格式
             * @default
             */
            options: string[];
            /**
             * @description 选择题正确答案1到10，如果有多个答案，中间使用逗号隔开，如果是判断题，1：代表正确，其他代表错误
             * @default
             */
            answer: string;
            /**
             * @description 答案解析
             * @default
             */
            analysis: string;
        };
        /**
         * @description 题目与业务关联信息
         * @default null
         */
        QuestionBizDTO: {
            /**
             * @description 业务id，要关联问题的某业务id，例如小节id
             * @default
             */
            bizId: string;
            /**
             * @description 题目id
             * @default
             */
            questionId: string;
        };
        Command: {
            action?: string;
            paperId?: string;
            attemptId?: string;
            lessonId?: string;
            answers?: {
                [key: string]: string;
            };
            questionId?: string;
            /** Format: int32 */
            score?: number;
            version?: string;
            feedback?: string;
            actorRole?: string;
        };
        View: {
            operationId?: string;
            status?: string;
            result?: unknown;
            errorCode?: string;
            errorMessage?: string;
        };
        PublishRequest: {
            courseId?: string;
            sectionId?: string;
            questionIds?: string[];
            /** Format: int32 */
            passPercent?: number;
            graders?: string[];
        };
        /**
         * @description 考试详情数据
         * @default null
         */
        QuestionDetailVO: {
            /**
             * @description 题目id
             * @default
             */
            id: string;
            /**
             * @description 题目名称，题干
             * @default
             */
            name: string;
            /**
             * Format: int32
             * @description 题目类型，1：单选题，2：多选题，3：不定向选择题，4：判断题，5：主观题
             * @default
             */
            type: number;
            /**
             * @description 1级课程分类id
             * @default
             */
            cateId1: string;
            /**
             * @description 2级课程分类id
             * @default
             */
            cateId2: string;
            /**
             * @description 3级课程分类id
             * @default
             */
            cateId3: string;
            /**
             * @description 课程三级分类的名称集合
             * @default
             */
            categories: string[];
            /**
             * Format: int32
             * @description 难易度，1：简单，2：中等，3：困难
             * @default
             */
            difficulty: number;
            /**
             * Format: int32
             * @description 分值
             * @default
             */
            score: number;
            /**
             * Format: int32
             * @description 引用次数
             * @default
             */
            useTimes: number;
            /**
             * Format: int32
             * @description 回答正确次数
             * @default
             */
            correctTimes: number;
            /**
             * Format: int32
             * @description 回答次数
             * @default
             */
            answerTimes: number;
            /**
             * @description 更新人
             * @default
             */
            updater: string;
            /**
             * Format: date-time
             * @description 更新时间
             * @default
             */
            updateTime: string;
            /**
             * @description 选择题选项，json数组格式
             * @default
             */
            options: string[];
            /**
             * @description 选择题正确答案1到10，如果有多个答案，中间使用逗号隔开，如果是判断题，1：代表正确，其他代表错误
             * @default
             */
            answer: string;
            /**
             * @description 答案解析
             * @default
             */
            analysis: string;
        };
        /**
         * @description 题目分页查询条件
         * @default null
         */
        QuestionPageQuery: {
            /** Format: int32 */
            pageNo?: number;
            /** Format: int32 */
            pageSize?: number;
            isAsc?: boolean;
            sortBy?: string;
            /**
             * @description 三级分类id集合
             * @default
             */
            cateIds: string[];
            /**
             * @description 题目类型，1：单选题，2：多选题，3：不定向选择题，4：判断题，5：主观题
             * @default
             */
            types: number[];
            /**
             * Format: int32
             * @description 难易度，1：简单，2：中等，3：困难
             * @default
             */
            difficulty: number;
            /**
             * @description 题目名称关键字
             * @default
             */
            keyword: string;
            /**
             * @description 题目录入者id
             * @default
             */
            creater: string;
        };
        /**
         * @description 分页结果
         * @default null
         */
        PageDTOQuestionPageVO: {
            /**
             * @description 总条数
             * @default
             */
            total: string;
            /**
             * @description 总页码数
             * @default
             */
            pages: string;
            /**
             * @description 当前页数据
             * @default
             */
            list: components["schemas"]["QuestionPageVO"][];
        };
        /**
         * @description 考试题目分页数据
         * @default null
         */
        QuestionPageVO: {
            /**
             * @description 题目id
             * @default
             */
            id: string;
            /**
             * @description 题目名称，题干
             * @default
             */
            name: string;
            /**
             * Format: int32
             * @description 题目类型，1：单选题，2：多选题，3：不定向选择题，4：判断题，5：主观题
             * @default
             */
            type: number;
            /**
             * @description 课程三级分类的名称集合
             * @default
             */
            categories: string[];
            /**
             * Format: int32
             * @description 难易度，1：简单，2：中等，3：困难
             * @default
             */
            difficulty: number;
            /**
             * Format: int32
             * @description 分值
             * @default
             */
            score: number;
            /**
             * Format: int32
             * @description 引用次数
             * @default
             */
            useTimes: number;
            /**
             * Format: int32
             * @description 回答次数
             * @default
             */
            answerTimes: number;
            /**
             * @description 更新人
             * @default
             */
            updater: string;
            /**
             * Format: date-time
             * @description 更新时间
             * @default
             */
            updateTime: string;
        };
        /**
         * @description 题目数据
         * @default null
         */
        QuestionDTO: {
            /**
             * @description 题目id
             * @default
             */
            id: string;
            /**
             * @description 题目名称，题干
             * @default
             */
            name: string;
            /**
             * @description 题目类型，1：单选题，2：多选题，3：不定向选择题，4：判断题，5：主观题
             * @default
             */
            type: string;
            /**
             * Format: int32
             * @description 难易度，1：简单，2：中等，3：困难
             * @default
             */
            difficulty: number;
            /**
             * Format: int32
             * @description 分值
             * @default
             */
            score: number;
            /**
             * @description 选择题选项，json数组格式
             * @default
             */
            options: string[];
            /**
             * @description 选择题正确答案1到10，如果有多个答案，中间使用逗号隔开，如果是判断题，1：代表正确，其他代表错误
             * @default
             */
            answer: string;
            /**
             * @description 答案解析
             * @default
             */
            analysis: string;
        };
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    queryQuestionDetailById: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 要查询的题目的id */
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        code: number;
                        msg: string;
                        requestId: string;
                        data: components["schemas"]["QuestionDetailVO"];
                    };
                };
            };
        };
    };
    updateQuestion: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 要修改的题目的id */
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["QuestionFormDTO"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        code: number;
                        msg: string;
                        requestId: string;
                        data: null;
                    };
                };
            };
        };
    };
    deleteQuestionById: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 要删除的题目的id */
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        code: number;
                        msg: string;
                        requestId: string;
                        data: null;
                    };
                };
            };
        };
    };
    addQuestion: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["QuestionFormDTO"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        code: number;
                        msg: string;
                        requestId: string;
                        data: null;
                    };
                };
            };
        };
    };
    saveQuestionBizInfoBatch: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["QuestionBizDTO"][];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        code: number;
                        msg: string;
                        requestId: string;
                        data: null;
                    };
                };
            };
        };
    };
    grade: {
        parameters: {
            query?: never;
            header: {
                "Idempotency-Key": string;
            };
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Command"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        code: number;
                        msg: string;
                        requestId: string;
                        data: components["schemas"]["View"];
                    };
                };
            };
        };
    };
    start: {
        parameters: {
            query?: never;
            header: {
                "Idempotency-Key": string;
            };
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        code: number;
                        msg: string;
                        requestId: string;
                        data: components["schemas"]["View"];
                    };
                };
            };
        };
    };
    submit: {
        parameters: {
            query?: never;
            header: {
                "Idempotency-Key": string;
            };
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Command"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        code: number;
                        msg: string;
                        requestId: string;
                        data: components["schemas"]["View"];
                    };
                };
            };
        };
    };
    replay: {
        parameters: {
            query: {
                version: string;
            };
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        code: number;
                        msg: string;
                        requestId: string;
                        data: null;
                    };
                };
            };
        };
    };
    publish: {
        parameters: {
            query?: never;
            header: {
                "Idempotency-Key": string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PublishRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        code: number;
                        msg: string;
                        requestId: string;
                        data: components["schemas"]["View"];
                    };
                };
            };
        };
    };
    replay_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        code: number;
                        msg: string;
                        requestId: string;
                        data: null;
                    };
                };
            };
        };
    };
    replay_2: {
        parameters: {
            query: {
                version: string;
            };
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        code: number;
                        msg: string;
                        requestId: string;
                        data: null;
                    };
                };
            };
        };
    };
    queryQuestionScores: {
        parameters: {
            query: {
                /** @description 要查询的题目的id集合 */
                ids: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        code: number;
                        msg: string;
                        requestId: string;
                        data: {
                            [key: string]: number;
                        };
                    };
                };
            };
        };
    };
    queryQuestionByPage: {
        parameters: {
            query: {
                query: components["schemas"]["QuestionPageQuery"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        code: number;
                        msg: string;
                        requestId: string;
                        data: components["schemas"]["PageDTOQuestionPageVO"];
                    };
                };
            };
        };
    };
    countSubjectNumOfTeacher: {
        parameters: {
            query: {
                /** @description 要查询的老师的集合 */
                ids: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        code: number;
                        msg: string;
                        requestId: string;
                        data: {
                            [key: string]: number;
                        };
                    };
                };
            };
        };
    };
    queryQuestionByIds: {
        parameters: {
            query: {
                /** @description 要查询的题目的id集合 */
                ids: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        code: number;
                        msg: string;
                        requestId: string;
                        data: components["schemas"]["QuestionDTO"][];
                    };
                };
            };
        };
    };
    queryQuestionByBizId: {
        parameters: {
            query: {
                /** @description 要查询的题目的id集合 */
                bizId: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        code: number;
                        msg: string;
                        requestId: string;
                        data: components["schemas"]["QuestionDTO"][];
                    };
                };
            };
        };
    };
    checkNameValid: {
        parameters: {
            query: {
                name: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        code: number;
                        msg: string;
                        requestId: string;
                        data: boolean;
                    };
                };
            };
        };
    };
    queryQuestionScoresByBizIds: {
        parameters: {
            query: {
                ids: string[];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        code: number;
                        msg: string;
                        requestId: string;
                        data: {
                            [key: string]: number;
                        };
                    };
                };
            };
        };
    };
    queryQuestionIdsByBizId: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 业务id */
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        code: number;
                        msg: string;
                        requestId: string;
                        data: components["schemas"]["QuestionBizDTO"][];
                    };
                };
            };
        };
    };
    queryQuestionIdsByBizIds: {
        parameters: {
            query: {
                /** @description 业务id集合 */
                ids: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        code: number;
                        msg: string;
                        requestId: string;
                        data: components["schemas"]["QuestionBizDTO"][];
                    };
                };
            };
        };
    };
    pending: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        code: number;
                        msg: string;
                        requestId: string;
                        data: {
                            [key: string]: unknown;
                        }[];
                    };
                };
            };
        };
    };
    grading: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        code: number;
                        msg: string;
                        requestId: string;
                        data: {
                            [key: string]: unknown;
                        };
                    };
                };
            };
        };
    };
    get: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        code: number;
                        msg: string;
                        requestId: string;
                        data: components["schemas"]["View"];
                    };
                };
            };
        };
    };
    papers: {
        parameters: {
            query: {
                courseId: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        code: number;
                        msg: string;
                        requestId: string;
                        data: {
                            [key: string]: unknown;
                        }[];
                    };
                };
            };
        };
    };
    attempt: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        code: number;
                        msg: string;
                        requestId: string;
                        data: {
                            [key: string]: unknown;
                        };
                    };
                };
            };
        };
    };
    list: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        code: number;
                        msg: string;
                        requestId: string;
                        data: Record<string, never>;
                    };
                };
            };
        };
    };
    failures: {
        parameters: {
            query?: {
                limit?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        code: number;
                        msg: string;
                        requestId: string;
                        data: {
                            [key: string]: unknown;
                        }[];
                    };
                };
            };
        };
    };
    list_1: {
        parameters: {
            query?: {
                limit?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        code: number;
                        msg: string;
                        requestId: string;
                        data: {
                            [key: string]: unknown;
                        }[];
                    };
                };
            };
        };
    };
}
