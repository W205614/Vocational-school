export interface paths {
    "/api/v2/services/learning/questions/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 根据id查询互动问题 */
        get: operations["queryQuestionById"];
        /** 修改互动问题 */
        put: operations["updateQuestion"];
        post?: never;
        /** 删除我的问题 */
        delete: operations["deleteQuestionById"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/notes/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["note"];
        put: operations["update"];
        post?: never;
        delete: operations["delete"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/favorites/{courseId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["favoriteState"];
        put: operations["favorite"];
        post?: never;
        delete: operations["unfavorite"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/learning/admin/replies/{id}/hidden/{hidden}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** 隐藏或显示回答或评论 */
        put: operations["hiddenReplyAdmin"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/learning/admin/questions/{id}/hidden/{hidden}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** 隐藏或显示问题 */
        put: operations["hiddenQuestionAdmin"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/learning/sign-records": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询签到记录 */
        get: operations["querySignRecords"];
        put?: never;
        /** 签到功能接口 */
        post: operations["addSignRecords"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/learning/replies": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 新增回答或评论 */
        post: operations["saveReply"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/learning/questions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 新增互动问题 */
        post: operations["saveQuestion"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/learning/lessons/plans": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询我的学习计划 */
        get: operations["queryMyPlans"];
        put?: never;
        /** 创建学习计划 */
        post: operations["createLearningPlans"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/learning/learning-records": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 提交学习记录 */
        post: operations["addLearningRecord"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/sign-ins": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list"];
        put?: never;
        post: operations["sign"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/notes": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["notes"];
        put?: never;
        post: operations["create"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/points-projections/{month}/rebuild": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["rebuild"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/operation-failures/learning/{id}/replay": {
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
    "/api/v2/admin/events/learning/{id}/replay": {
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
    "/api/v2/admin/consumer-failures/learning/{id}/replay": {
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
    "/api/v2/services/learning/replies/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 分页查询回答或评论列表 */
        get: operations["queryReplyVOPage"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/learning/questions/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 分页查询互动问题 */
        get: operations["queryQuestionPage"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/learning/points/today": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询我的今日积分情况 */
        get: operations["queryMyPointsToday"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/learning/lessons/{courseId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询用户课表中指定课程状态 */
        get: operations["isLessonStatus"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/learning/lessons/{courseId}/valid": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 校验当前用户是否可以学习当前课程 */
        get: operations["isLessonValid"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/learning/lessons/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询我的课表 */
        get: operations["queryMyLessons"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/learning/lessons/now": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询我正在学习的课程 */
        get: operations["queryMyCurrentLesson"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/learning/lessons/lessons/{courseId}/count": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 统计课程学习人数 */
        get: operations["countLearningLessonByCourse"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/learning/learning-records/course/{courseId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询指定课程的学习记录 */
        get: operations["queryLearningRecordByCourse"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/learning/boards": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 分页查询指定赛季的积分排行榜 */
        get: operations["queryPointsBoardBySeason"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/learning/board/seasons/list": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询赛季列表 */
        get: operations["queryPointsBoardSeasonList"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/operations/learning/{id}": {
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
    "/api/v2/favorites": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["favorites"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/points-projections": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["status"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/operation-failures/learning": {
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
    "/api/v2/admin/events/learning/failures": {
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
    "/api/v2/admin/consumer-failures/learning": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list_2"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/learning/admin/replies/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 根据id查询回答或评论详情 */
        get: operations["queryReplyVOByIdAdmin"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/learning/admin/replies/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 分页查询回答或评论列表 */
        get: operations["queryReplyVOPageAdmin"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/learning/admin/questions/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 根据id查询问题详情 */
        get: operations["queryQuestionAdminVOById"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/learning/admin/questions/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 管理端分页查询互动问题 */
        get: operations["queryQuestionPageAdmin"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/learning/lessons/lessons/{courseId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /** 删除表中的某课程 */
        delete: operations["deleteLesson"];
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
         * @description 互动问题表单信息
         * @default null
         */
        QuestionFormDTO: {
            /**
             * @description 课程id
             * @default
             */
            courseId: string;
            /**
             * @description 章id
             * @default
             */
            chapterId: string;
            /**
             * @description 小节id
             * @default
             */
            sectionId: string;
            /**
             * @description 标题
             * @default
             */
            title: string;
            /**
             * @description 互动问题描述
             * @default
             */
            description: string;
            /**
             * @description 是否匿名提问
             * @default false
             */
            anonymity: boolean;
        };
        Mutation: {
            action?: string;
            id?: string;
            courseId?: string;
            sectionId?: string;
            /** Format: int32 */
            moment?: number;
            content?: string;
            version?: string;
        };
        View: {
            operationId?: string;
            status?: string;
            result?: unknown;
            errorCode?: string;
            errorMessage?: string;
        };
        /**
         * @description 签到结果
         * @default null
         */
        SignResultVO: {
            /**
             * Format: int32
             * @description 连续签到天数
             * @default
             */
            signDays: number;
            /**
             * Format: int32
             * @description 签到得分
             * @default
             */
            signPoints: number;
            /**
             * Format: int32
             * @description 连续签到奖励积分，连续签到超过7天以上才有奖励
             * @default
             */
            rewardPoints: number;
        };
        /**
         * @description 互动回答信息
         * @default null
         */
        ReplyDTO: {
            /**
             * @description 回答内容
             * @default
             */
            content: string;
            /**
             * @description 是否匿名提问
             * @default false
             */
            anonymity: boolean;
            /**
             * @description 互动问题id
             * @default
             */
            questionId: string;
            /**
             * @description 回复的上级回答id，没有可不填
             * @default
             */
            answerId: string;
            /**
             * @description 回复的目标回复id，没有可不填
             * @default
             */
            targetReplyId: string;
            /**
             * @description 回复的目标用户id，没有可不填
             * @default
             */
            targetUserId: string;
            /**
             * @description 标记是否是学生提交的回答，默认true
             * @default false
             */
            isStudent: boolean;
        };
        /**
         * @description 学习计划表单实体
         * @default null
         */
        LearningPlanDTO: {
            /**
             * @description 课程表id
             * @default
             */
            courseId: string;
            /**
             * Format: int32
             * @description 每周学习频率
             * @default
             */
            freq: number;
        };
        /**
         * @description 学习记录
         * @default null
         */
        LearningRecordFormDTO: {
            /**
             * Format: int32
             * @description 小节类型：1-视频，2-考试
             * @default
             * @enum {integer}
             */
            sectionType: 1 | 2;
            /**
             * @description 课表id
             * @default
             */
            lessonId: string;
            /**
             * @description 对应节的id
             * @default
             */
            sectionId: string;
            /**
             * Format: int32
             * @description 视频总时长，单位秒
             * @default
             */
            duration: number;
            /**
             * Format: int32
             * @description 视频的当前观看时长，单位秒，第一次提交填0
             * @default
             */
            moment: number;
            /**
             * Format: date-time
             * @description 提交时间
             * @default
             */
            commitTime: string;
        };
        /**
         * @description 签到结果
         * @default null
         */
        SignRecordVO: {
            /**
             * Format: int32
             * @description 连续签到天数
             * @default
             */
            signDays: number;
            /**
             * @description 签到记录，例如：[0,1,1,0,1]，按照数组角标0~30，代表当月1~31号, 0代表未签到，1代表已签到
             * @default
             */
            signRecords: string[];
        };
        /**
         * @description 互动回答分页查询条件
         * @default null
         */
        ReplyPageQuery: {
            /** Format: int32 */
            pageNo?: number;
            /** Format: int32 */
            pageSize?: number;
            isAsc?: boolean;
            sortBy?: string;
            /**
             * @description 问题id，不为空则代表根据问题查询回答
             * @default
             */
            questionId: string;
            /**
             * @description 回答id，不为空则代表根据回答查询评论
             * @default
             */
            answerId: string;
        };
        /**
         * @description 分页结果
         * @default null
         */
        PageDTOReplyVO: {
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
            list: components["schemas"]["ReplyVO"][];
        };
        /**
         * @description 互动回答信息
         * @default null
         */
        ReplyVO: {
            /**
             * @description id
             * @default
             */
            id: string;
            /**
             * @description 回答内容
             * @default
             */
            content: string;
            /**
             * @description 是否匿名提问
             * @default false
             */
            anonymity: boolean;
            /**
             * @description 是否隐藏
             * @default false
             */
            hidden: boolean;
            /**
             * Format: int32
             * @description 评论数量
             * @default
             */
            replyTimes: number;
            /**
             * Format: date-time
             * @description 创建时间，也就是回答时间
             * @default
             */
            createTime: string;
            /**
             * @description 当前回复者id
             * @default
             */
            userId: string;
            /**
             * @description 当前回复者昵称
             * @default
             */
            userName: string;
            /**
             * @description 当前回复者头像
             * @default
             */
            userIcon: string;
            /**
             * Format: int32
             * @description 当前回复者类型，2-学员，其它-老师
             * @default
             */
            userType: number;
            /**
             * @description 是否点过赞
             * @default false
             */
            liked: boolean;
            /**
             * Format: int32
             * @description 点赞数量
             * @default
             */
            likedTimes: number;
            /**
             * @description 目标用户名字
             * @default
             */
            targetUserName: string;
        };
        /**
         * @description 用户端互动问题信息
         * @default null
         */
        QuestionVO: {
            /**
             * @description 主键id
             * @default
             */
            id: string;
            /**
             * @description 互动问题名称
             * @default
             */
            title: string;
            /**
             * @description 互动问题描述
             * @default
             */
            description: string;
            /**
             * Format: int32
             * @description 回答数量，0表示没有回答
             * @default
             */
            answerTimes: number;
            /**
             * Format: date-time
             * @description 创建时间
             * @default
             * @example 2022-7-18 19:52:36
             */
            createTime: string;
            /**
             * @description 是否匿名提问
             * @default false
             */
            anonymity: boolean;
            /**
             * @description 提问者id
             * @default
             */
            userId: string;
            /**
             * @description 提问者昵称
             * @default
             */
            userName: string;
            /**
             * @description 提问者头像
             * @default
             */
            userIcon: string;
            /**
             * @description 最新的回答信息
             * @default
             */
            latestReplyContent: string;
            /**
             * @description 最新的回答者昵称
             * @default
             */
            latestReplyUser: string;
        };
        /**
         * @description 互动问题分页查询条件
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
             * @description 课程id
             * @default
             */
            courseId: string;
            /**
             * @description 小节id
             * @default
             * @example 1
             */
            sectionId: string;
            /**
             * @description 是否只查询我的问题
             * @default false
             * @example 1
             */
            onlyMine: boolean;
        };
        /**
         * @description 分页结果
         * @default null
         */
        PageDTOQuestionVO: {
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
            list: components["schemas"]["QuestionVO"][];
        };
        /**
         * @description 每日积分统计实体
         * @default null
         */
        PointsStatisticsVO: {
            /**
             * @description 获取积分方式
             * @default
             */
            type: string;
            /**
             * Format: int32
             * @description 今日已获取积分值
             * @default
             */
            points: number;
            /**
             * Format: int32
             * @description 单日积分上限
             * @default
             */
            maxPoints: number;
        };
        /**
         * @description 课程表信息
         * @default null
         */
        LearningLessonVO: {
            /**
             * @description 主键lessonId
             * @default
             */
            id: string;
            /**
             * @description 课程id
             * @default
             */
            courseId: string;
            /**
             * @description 课程名称
             * @default
             */
            courseName: string;
            /**
             * @description 课程封面
             * @default
             */
            courseCoverUrl: string;
            /**
             * Format: int32
             * @description 课程章节数量
             * @default
             */
            sections: number;
            /**
             * Format: int32
             * @description 课程状态，0-未学习，1-学习中，2-已学完，3-已失效
             * @default
             * @enum {integer}
             */
            status: 0 | 1 | 2 | 3;
            /**
             * Format: int32
             * @description 总已学习章节数
             * @default
             */
            learnedSections: number;
            /**
             * Format: int32
             * @description 总已报名课程数
             * @default
             */
            courseAmount: number;
            /**
             * Format: date-time
             * @description 课程购买时间
             * @default
             */
            createTime: string;
            /**
             * Format: date-time
             * @description 课程过期时间，如果为null代表课程永久有效
             * @default
             */
            expireTime: string;
            /**
             * Format: int32
             * @description 习计划状态，0-没有计划，1-计划进行中
             * @default
             * @enum {integer}
             */
            planStatus: 0 | 1;
            /**
             * Format: int32
             * @description 计划的学习频率
             * @default
             */
            weekFreq: number;
            /**
             * @description 最近学习的小节名
             * @default
             */
            latestSectionName: string;
            /**
             * Format: int32
             * @description 最近学习的小节编号
             * @default
             */
            latestSectionIndex: number;
        };
        /**
         * @description 分页请求参数
         * @default null
         */
        PageQuery: {
            /** Format: int32 */
            pageNo?: number;
            /** Format: int32 */
            pageSize?: number;
            isAsc?: boolean;
            sortBy?: string;
        };
        /**
         * @description 学习计划分页统计结果
         * @default null
         */
        LearningPlanPageVO: {
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
            list: components["schemas"]["LearningPlanVO"][];
            /**
             * Format: int32
             * @description 本周积分值
             * @default
             */
            weekPoints: number;
            /**
             * Format: int32
             * @description 本周完成的计划数量
             * @default
             */
            weekFinished: number;
            /**
             * Format: int32
             * @description 总的计划学习数量
             * @default
             */
            weekTotalPlan: number;
        };
        /**
         * @description 课程计划信息
         * @default null
         */
        LearningPlanVO: {
            /**
             * @description 主键lessonId
             * @default
             */
            id: string;
            /**
             * @description 课程id
             * @default
             */
            courseId: string;
            /**
             * @description 课程名称
             * @default
             */
            courseName: string;
            /**
             * Format: int32
             * @description 每周计划学习章节数
             * @default
             */
            weekFreq: number;
            /**
             * Format: int32
             * @description 课程章节数量
             * @default
             */
            sections: number;
            /**
             * Format: int32
             * @description 本周已学习章节数
             * @default
             */
            weekLearnedSections: number;
            /**
             * Format: int32
             * @description 总已学习章节数
             * @default
             */
            learnedSections: number;
            /**
             * Format: date-time
             * @description 最近一次学习时间
             * @default
             */
            latestLearnTime: string;
        };
        /**
         * @description 分页结果
         * @default null
         */
        PageDTOLearningLessonVO: {
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
            list: components["schemas"]["LearningLessonVO"][];
        };
        /**
         * @description 学习课表进度信息
         * @default null
         */
        LearningLessonDTO: {
            /**
             * @description 课表id
             * @default
             */
            id: string;
            /**
             * @description 最近学习的小节id
             * @default
             */
            latestSectionId: string;
            /**
             * @description 学习过的小节的记录
             * @default
             */
            records: components["schemas"]["LearningRecordDTO"][];
        };
        /**
         * @description 小节信息及学习进度
         * @default null
         */
        LearningRecordDTO: {
            /**
             * @description 对应节的id
             * @default
             */
            sectionId: string;
            /**
             * Format: int32
             * @description 视频的当前观看时长，单位秒
             * @default
             */
            moment: number;
            /**
             * @description 是否完成学习，默认false
             * @default false
             */
            finished: boolean;
        };
        /**
         * @description 积分排行榜分页查询条件
         * @default null
         */
        PointsBoardQuery: {
            /** Format: int32 */
            pageNo?: number;
            /** Format: int32 */
            pageSize?: number;
            isAsc?: boolean;
            sortBy?: string;
            /**
             * @description 赛季id，为null或者0则代表查询当前赛季
             * @default
             */
            season: string;
        };
        /**
         * @description 积分榜单信息
         * @default null
         */
        PointsBoardItemVO: {
            /**
             * Format: int32
             * @description 积分值
             * @default
             */
            points: number;
            /**
             * Format: int32
             * @description 名次
             * @default
             */
            rank: number;
            /**
             * @description 学生姓名
             * @default
             */
            name: string;
        };
        /**
         * @description 积分榜单汇总信息
         * @default null
         */
        PointsBoardVO: {
            /**
             * Format: int32
             * @description 我的榜单排名
             * @default
             */
            rank: number;
            /**
             * Format: int32
             * @description 我的积分值
             * @default
             */
            points: number;
            /**
             * @description 前100名上榜人信息
             * @default
             */
            boardList: components["schemas"]["PointsBoardItemVO"][];
        };
        PointsBoardSeason: {
            /** Format: int32 */
            id?: number;
            name?: string;
            /** Format: date */
            beginTime?: string;
            /** Format: date */
            endTime?: string;
        };
        /**
         * @description 用户端互动问题信息
         * @default null
         */
        QuestionAdminVO: {
            /**
             * @description 主键id
             * @default
             */
            id: string;
            /**
             * @description 互动问题名称
             * @default
             */
            title: string;
            /**
             * @description 互动问题描述
             * @default
             */
            description: string;
            /**
             * Format: int32
             * @description 回答数量，0表示没有回答
             * @default
             */
            answerTimes: number;
            /**
             * Format: date-time
             * @description 创建时间
             * @default
             * @example 2022-7-18 19:52:36
             */
            createTime: string;
            /**
             * Format: int32
             * @description 管理端问题状态：0-未查看，1-已查看
             * @default
             */
            status: number;
            /**
             * @description 是否被隐藏
             * @default false
             */
            hidden: boolean;
            /**
             * @description 提问者昵称
             * @default
             */
            userName: string;
            /**
             * @description 提问者头像
             * @default
             */
            userIcon: string;
            /**
             * @description 教师名称
             * @default
             */
            teacherName: string;
            /**
             * @description 课程名称
             * @default
             */
            courseName: string;
            /**
             * @description 章名称
             * @default
             */
            chapterName: string;
            /**
             * @description 节名称
             * @default
             */
            sectionName: string;
            /**
             * @description 三级分类名称，中间使用/隔开
             * @default
             */
            categoryName: string;
        };
        /**
         * @description 互动问题管理端分页查询条件
         * @default null
         */
        QuestionAdminPageQuery: {
            /** Format: int32 */
            pageNo?: number;
            /** Format: int32 */
            pageSize?: number;
            isAsc?: boolean;
            sortBy?: string;
            /**
             * @description 课程名称搜索关键字
             * @default
             * @example Redis
             */
            courseName: string;
            /**
             * Format: int32
             * @description 管理端问题状态：0-未查看，1-已查看
             * @default
             * @example 1
             */
            status: number;
            /**
             * Format: date-time
             * @description 更新时间区间的开始时间
             * @default
             * @example 2022-7-18 19:52:36
             */
            beginTime: string;
            /**
             * Format: date-time
             * @description 更新时间区间的结束时间
             * @default
             * @example 2022-7-18 19:52:36
             */
            endTime: string;
        };
        /**
         * @description 分页结果
         * @default null
         */
        PageDTOQuestionAdminVO: {
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
            list: components["schemas"]["QuestionAdminVO"][];
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
    queryQuestionById: {
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
                        data: components["schemas"]["QuestionVO"];
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
    note: {
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
    update: {
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
                "application/json": components["schemas"]["Mutation"];
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
    delete: {
        parameters: {
            query: {
                version: string;
            };
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
    favoriteState: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                courseId: string;
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
                            [key: string]: boolean;
                        };
                    };
                };
            };
        };
    };
    favorite: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                courseId: string;
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
    unfavorite: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                courseId: string;
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
    hiddenReplyAdmin: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
                hidden: boolean;
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
    hiddenQuestionAdmin: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
                hidden: boolean;
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
    querySignRecords: {
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
                        data: components["schemas"]["SignRecordVO"];
                    };
                };
            };
        };
    };
    addSignRecords: {
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
                        data: components["schemas"]["SignResultVO"];
                    };
                };
            };
        };
    };
    saveReply: {
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
                "application/json": components["schemas"]["ReplyDTO"];
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
                        data: Record<string, never>;
                    };
                };
            };
        };
    };
    saveQuestion: {
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
                        data: Record<string, never>;
                    };
                };
            };
        };
    };
    queryMyPlans: {
        parameters: {
            query: {
                query: components["schemas"]["PageQuery"];
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
                        data: components["schemas"]["LearningPlanPageVO"];
                    };
                };
            };
        };
    };
    createLearningPlans: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["LearningPlanDTO"];
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
    addLearningRecord: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["LearningRecordFormDTO"];
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
    sign: {
        parameters: {
            query?: never;
            header: {
                "Idempotency-Key": string;
            };
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
                        data: components["schemas"]["View"];
                    };
                };
            };
        };
    };
    notes: {
        parameters: {
            query: {
                page: components["schemas"]["PageQuery"];
                courseId?: string;
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
                        };
                    };
                };
            };
        };
    };
    create: {
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
                "application/json": components["schemas"]["Mutation"];
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
    rebuild: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                month: string;
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
    queryReplyVOPage: {
        parameters: {
            query: {
                query: components["schemas"]["ReplyPageQuery"];
                isAdmin: boolean;
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
                        data: components["schemas"]["PageDTOReplyVO"];
                    };
                };
            };
        };
    };
    queryQuestionPage: {
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
                        data: components["schemas"]["PageDTOQuestionVO"];
                    };
                };
            };
        };
    };
    queryMyPointsToday: {
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
                        data: components["schemas"]["PointsStatisticsVO"][];
                    };
                };
            };
        };
    };
    isLessonStatus: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                courseId: string;
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
                        data: components["schemas"]["LearningLessonVO"];
                    };
                };
            };
        };
    };
    isLessonValid: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                courseId: string;
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
                        data: string;
                    };
                };
            };
        };
    };
    queryMyLessons: {
        parameters: {
            query: {
                query: components["schemas"]["PageQuery"];
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
                        data: components["schemas"]["PageDTOLearningLessonVO"];
                    };
                };
            };
        };
    };
    queryMyCurrentLesson: {
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
                        data: components["schemas"]["LearningLessonVO"];
                    };
                };
            };
        };
    };
    countLearningLessonByCourse: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                courseId: string;
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
                        /** Format: int32 */
                        data: number;
                    };
                };
            };
        };
    };
    queryLearningRecordByCourse: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /**
                 * @description 课程id
                 * @example 2
                 */
                courseId: string;
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
                        data: components["schemas"]["LearningLessonDTO"];
                    };
                };
            };
        };
    };
    queryPointsBoardBySeason: {
        parameters: {
            query: {
                query: components["schemas"]["PointsBoardQuery"];
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
                        data: components["schemas"]["PointsBoardVO"];
                    };
                };
            };
        };
    };
    queryPointsBoardSeasonList: {
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
                        data: components["schemas"]["PointsBoardSeason"][];
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
    favorites: {
        parameters: {
            query: {
                page: components["schemas"]["PageQuery"];
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
                        };
                    };
                };
            };
        };
    };
    status: {
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
    list_1: {
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
    list_2: {
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
    queryReplyVOByIdAdmin: {
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
                        data: components["schemas"]["ReplyVO"];
                    };
                };
            };
        };
    };
    queryReplyVOPageAdmin: {
        parameters: {
            query: {
                query: components["schemas"]["ReplyPageQuery"];
                isAdmin: boolean;
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
                        data: components["schemas"]["PageDTOReplyVO"];
                    };
                };
            };
        };
    };
    queryQuestionAdminVOById: {
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
                        data: components["schemas"]["QuestionAdminVO"];
                    };
                };
            };
        };
    };
    queryQuestionPageAdmin: {
        parameters: {
            query: {
                query: components["schemas"]["QuestionAdminPageQuery"];
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
                        data: components["schemas"]["PageDTOQuestionAdminVO"];
                    };
                };
            };
        };
    };
    deleteLesson: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                courseId: string;
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
}
