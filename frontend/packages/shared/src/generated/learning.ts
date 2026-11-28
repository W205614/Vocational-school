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
    "/api/v2/teacher/learning-hub/practice/{courseId}/submissions/{id}/feedback": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["feedback"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/teacher/learning-hub/classrooms/{id}/activities/{activity}/submissions/{submission}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["review"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/teacher/learning-hub/classrooms/{id}/activities/{activity}/grades": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["changeGrade"];
        post: operations["publishGrade"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/teacher/learning-hub/classrooms/{id}/activities/{activity}/extension": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["extend"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/teacher/learning-hub/classrooms/{id}/activities/{activity}/correction": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["correct"];
        post?: never;
        delete?: never;
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
    "/api/v2/learning-hub/admin/reward-rules/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["rule"];
        post?: never;
        delete?: never;
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
    "/api/v2/admin/learning-hub/practice/{courseId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["configuration"];
        put: operations["configure"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/learning-hub/practice/{courseId}/corrections": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["correct_1"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/learning-hub/classrooms/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["update_1"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/learning-hub/classrooms/{id}/state": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["state"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/learning-hub/classrooms/{id}/staff/{user}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["assign"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/learning-hub/classrooms/{id}/members/{member}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["move"];
        post?: never;
        delete?: never;
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
    "/api/v2/services/learning/learning-records/playback-sessions": {
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
    "/api/v2/services/learning/learning-records/playback-sessions/heartbeat": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["heartbeat"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/learning/learning-records/playback-sessions/end": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["end"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/teacher/learning-hub/classrooms/{id}/discussion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["teachingDiscussion"];
        put?: never;
        post: operations["teachingReply"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/teacher/learning-hub/classrooms/{id}/archive": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["archive"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/teacher/learning-hub/classrooms/{id}/activities": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["activities"];
        put?: never;
        post: operations["publish"];
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
    "/api/v2/learning-hub/questions/{id}/accept": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["accept"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/learning-hub/practice/{courseId}/submissions": {
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
    "/api/v2/learning-hub/classrooms/{id}/enrollment": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["enroll"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/learning-hub/classrooms/{id}/discussion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["discussion"];
        put?: never;
        post: operations["discuss"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/learning-hub/classrooms/{id}/activities/{activity}/submissions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["submit_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/learning-hub/admin/rewards/{requestId}/retry": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["retry"];
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
    "/api/v2/admin/learning-hub/classrooms": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["administered"];
        put?: never;
        post: operations["create_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/learning-hub/classrooms/{id}/members": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["add"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/learning-hub/classrooms/{id}/copy-schedule": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["copy"];
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
    "/api/v2/teacher/learning-hub": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["overview"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/teacher/learning-hub/practice/{courseId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["teachingAssignment"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/teacher/learning-hub/practice/{courseId}/submissions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["teachingWork"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/teacher/learning-hub/practice/{courseId}/submissions/{studentId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["teachingHistory"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/teacher/learning-hub/practice/pending": {
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
    "/api/v2/teacher/learning-hub/classrooms": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["assigned"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/teacher/learning-hub/classrooms/{id}/staff": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["staff"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/teacher/learning-hub/classrooms/{id}/sections": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["sections"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/teacher/learning-hub/classrooms/{id}/pending": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["pending_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/teacher/learning-hub/classrooms/{id}/papers": {
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
    "/api/v2/teacher/learning-hub/classrooms/{id}/members": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["roster"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/teacher/learning-hub/classrooms/{id}/grades": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["teachingGrades"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/teacher/learning-hub/classrooms/{id}/grades/export": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["export_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/teacher/learning-hub/classrooms/{id}/activities/{activity}/versions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["teachingVersions"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/teacher/learning-hub/classrooms/{id}/activities/{activity}/submissions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["submissions"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/public/courses/{course}/classrooms": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["available"];
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
    "/api/v2/learning-hub/teaching/questions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["teachingQuestions"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/learning-hub/seasons": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["seasons"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/learning-hub/rewards": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["rewards"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/learning-hub/reward-rules": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["publicRules"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/learning-hub/practice": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["assignments"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/learning-hub/practice/{courseId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["assignment"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/learning-hub/points": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["points"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/learning-hub/overview": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["overview_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/learning-hub/classrooms": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["mine"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/learning-hub/classrooms/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["study"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/learning-hub/classrooms/{id}/members/{member}/grades": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["ownGrades"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/learning-hub/classrooms/{id}/activities/{activity}/versions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["versions"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/learning-hub/classrooms/{id}/activities/{activity}/submissions/{member}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["ownSubmissions"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/learning-hub/board": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["board"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/learning-hub/admin/seasons": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["adminSeasons"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/learning-hub/admin/rewards": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["adminRewards"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/learning-hub/admin/reward-rules": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["rules"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/learning-hub/admin/reconciliations": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["reconciliations"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/learning-hub/admin/points-statistics": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["statistics"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/learning-hub/achievements": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["achievements"];
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
    "/api/v2/admin/operation-failures/learning/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["page"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/events/learning/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["page_1"];
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
    "/api/v2/admin/consumer-failures/learning/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["page_2"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/audit/learning": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["page_3"];
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
        Feedback: {
            /** @enum {string} */
            status?: "SUBMITTED" | "NEEDS_REVISION" | "PASSED";
            feedback?: string;
        };
        Submission: {
            id?: string;
            userId?: string;
            /** Format: int32 */
            revision?: number;
            /** Format: int32 */
            assignmentVersion?: number;
            description?: string;
            workUrl?: string;
            /** @enum {string} */
            status?: "SUBMITTED" | "NEEDS_REVISION" | "PASSED";
            feedback?: string;
            /** Format: date-time */
            submittedAt?: string;
            /** Format: date-time */
            reviewedAt?: string;
        };
        Review: {
            /** @enum {string} */
            state?: "SUBMITTED" | "NEEDS_REVISION" | "REVIEWED";
            /** Format: int32 */
            score?: number;
            feedback?: string;
        };
        Change: {
            memberId?: string;
            /** Format: int32 */
            expectedScore?: number;
            /** Format: int32 */
            score?: number;
            feedback?: string;
            reason?: string;
        };
        Extension: {
            memberId?: string;
            /** Format: date-time */
            closesAt?: string;
            reason?: string;
        };
        Correction: {
            /** Format: int32 */
            expectedVersion?: number;
            title?: string;
            instructions?: string;
            reason?: string;
        };
        Activity: {
            id?: string;
            classId?: string;
            /** @enum {string} */
            kind?: "LESSON" | "ASSIGNMENT" | "EXAM" | "ANNOUNCEMENT";
            sectionId?: string;
            paperId?: string;
            title?: string;
            /** Format: date-time */
            scheduledAt?: string;
            /** Format: date-time */
            closesAt?: string;
            /** Format: int32 */
            weight?: number;
            /** Format: int32 */
            maxAttempts?: number;
            /** Format: int32 */
            currentVersion?: number;
            instructions?: string;
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
        RuleForm: {
            month?: string;
            /** Format: int32 */
            rankFrom?: number;
            /** Format: int32 */
            rankTo?: number;
            couponId?: string;
            enabled?: boolean;
            version?: string;
        };
        Configuration: {
            title?: string;
            instructions?: string;
            publish?: boolean;
        };
        Assignment: {
            courseId?: string;
            title?: string;
            instructions?: string;
            /** Format: date-time */
            publishedAt?: string;
            /** Format: int32 */
            version?: number;
        };
        AssignmentVersion: {
            /** Format: int32 */
            version?: number;
            title?: string;
            instructions?: string;
            changeReason?: string;
            /** Format: date-time */
            effectiveAt?: string;
        };
        AssignmentView: {
            assignment?: components["schemas"]["Assignment"];
            versions?: components["schemas"]["AssignmentVersion"][];
        };
        Setup: {
            courseId?: string;
            name?: string;
            /** Format: date-time */
            registrationOpens?: string;
            /** Format: date-time */
            registrationCloses?: string;
            /** Format: date-time */
            startsAt?: string;
            /** Format: date-time */
            endsAt?: string;
            /** Format: int32 */
            capacity?: number;
            /** Format: int32 */
            passPercent?: number;
        };
        Classroom: {
            id?: string;
            courseId?: string;
            name?: string;
            /** @enum {string} */
            state?: "DRAFT" | "OPEN" | "CLOSED" | "ARCHIVED" | "CANCELED";
            /** Format: date-time */
            registrationOpens?: string;
            /** Format: date-time */
            registrationCloses?: string;
            /** Format: date-time */
            startsAt?: string;
            /** Format: date-time */
            endsAt?: string;
            /** Format: int32 */
            capacity?: number;
            /** Format: int32 */
            passPercent?: number;
            /** Format: int32 */
            remaining?: number;
        };
        StateChange: {
            /** @enum {string} */
            state?: "DRAFT" | "OPEN" | "CLOSED" | "ARCHIVED" | "CANCELED";
            reason?: string;
        };
        StaffChange: {
            /** @enum {string} */
            duty?: "ADMIN" | "TEACHER" | "ASSISTANT";
        };
        MoveMember: {
            targetClassId?: string;
            reason?: string;
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
        Start: {
            lessonId?: string;
            sectionId?: string;
        };
        Heartbeat: {
            sessionId?: string;
            sequence?: string;
            positionMillis?: string;
            /** Format: double */
            rate?: number;
            state?: string;
        };
        Cleanup: {
            requestId?: string;
            userId?: string;
            policyVersion?: string;
            cutoffs?: {
                [key: string]: string;
            };
        };
        DiscussionForm: {
            parentId?: string;
            sectionId?: string;
            title?: string;
            body?: string;
        };
        Discussion: {
            id?: string;
            parentId?: string;
            sectionId?: string;
            title?: string;
            body?: string;
            staffReply?: boolean;
            /** Format: date-time */
            postedAt?: string;
        };
        Form: {
            /** @enum {string} */
            kind?: "LESSON" | "ASSIGNMENT" | "EXAM" | "ANNOUNCEMENT";
            sectionId?: string;
            paperId?: string;
            title?: string;
            instructions?: string;
            /** Format: date-time */
            scheduledAt?: string;
            /** Format: date-time */
            closesAt?: string;
            /** Format: int32 */
            weight?: number;
            /** Format: int32 */
            maxAttempts?: number;
        };
        Publication: {
            memberId?: string;
            feedback?: string;
        };
        Acceptance: {
            replyId?: string;
        };
        Submit: {
            description?: string;
            workUrl?: string;
            /** Format: uuid */
            submissionKey?: string;
            /** Format: int32 */
            assignmentVersion?: number;
        };
        Membership: {
            id?: string;
            classId?: string;
            courseId?: string;
            userId?: string;
            orderId?: string;
            /** @enum {string} */
            state?: "HELD" | "ACTIVE" | "WITHDRAWN" | "TRANSFERRED" | "REFUNDED" | "RELEASED" | "COMPLETED";
            /** Format: date-time */
            joinedAt?: string;
        };
        AddMember: {
            userId?: string;
            reason?: string;
        };
        CopySchedule: {
            sourceClassId?: string;
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
            month?: string;
            todaySigned?: boolean;
            /** Format: int32 */
            daysInMonth?: number;
            rewards?: {
                [key: string]: number;
            }[];
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
            courseId?: string;
            sectionId?: string;
            acceptedReplyId?: string;
            /** Format: date-time */
            acceptedAt?: string;
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
            /** Format: int32 */
            typeCode?: number;
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
             * Format: int32
             * @description 当前课程状态，0-未学习，1-学习中，2-已学完，3-权益已失效
             * @default
             */
            status: number;
            /**
             * Format: date-time
             * @description 当前有效权益期限，永久权益或无有效权益时为空；结合status判断
             * @default
             */
            expireTime: string;
            /**
             * Format: int32
             * @description 已学习章节数，权益失效后仍保留
             * @default
             */
            learnedSections: number;
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
            watchedMillis?: string;
            creditedMillis?: string;
            completionSource?: string;
        };
        Dataset: {
            name?: string;
            select?: string;
            count?: string;
            eligible?: string;
            cleanup?: string;
        };
        Definition: {
            domain?: string;
            note?: string;
            datasets?: components["schemas"]["Dataset"][];
        };
        Page: {
            domain?: string;
            dataset?: string;
            rows?: {
                [key: string]: unknown;
            }[];
            /** Format: int32 */
            nextOffset?: number;
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
            month?: string;
            status?: string;
            total?: string;
            /** Format: int32 */
            gapToPrevious?: number;
            legacyReadonly?: boolean;
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
        StudentWork: {
            userId?: string;
            name?: string;
            latest?: components["schemas"]["Submission"];
        };
        SubmissionPage: {
            list?: components["schemas"]["StudentWork"][];
            total?: string;
        };
        PendingPage: {
            list?: components["schemas"]["PendingWork"][];
            total?: string;
            /** Format: date-time */
            oldestPendingAt?: string;
        };
        PendingWork: {
            submissionId?: string;
            courseId?: string;
            studentId?: string;
            studentName?: string;
            assignmentTitle?: string;
            /** Format: date-time */
            submittedAt?: string;
        };
        Staff: {
            userId?: string;
            name?: string;
            /** @enum {string} */
            duty?: "ADMIN" | "TEACHER" | "ASSISTANT";
        };
        Section: {
            id?: string;
            name?: string;
        };
        Pending: {
            /** Format: int32 */
            assignments?: number;
            /** Format: int32 */
            exams?: number;
            /** Format: int32 */
            questions?: number;
            /** Format: int32 */
            grades?: number;
        };
        Paper: {
            id?: string;
            courseId?: string;
            sectionId?: string;
            /** Format: int32 */
            version?: number;
            /** Format: int32 */
            totalScore?: number;
        };
        RosterRow: {
            memberId?: string;
            userId?: string;
            name?: string;
            /** @enum {string} */
            state?: "HELD" | "ACTIVE" | "WITHDRAWN" | "TRANSFERRED" | "REFUNDED" | "RELEASED" | "COMPLETED";
            /** Format: int32 */
            learnedSections?: number;
            /** Format: int32 */
            courseSections?: number;
        };
        Item: {
            activityId?: string;
            title?: string;
            /** @enum {string} */
            kind?: "LESSON" | "ASSIGNMENT" | "EXAM" | "ANNOUNCEMENT";
            /** Format: int32 */
            weight?: number;
            /** @enum {string} */
            state?: "NOT_SUBMITTED" | "NEEDS_REVISION" | "IN_PROGRESS" | "PENDING_REVIEW" | "READY" | "PUBLISHED";
            /** Format: int32 */
            score?: number;
            feedback?: string;
        };
        Report: {
            memberId?: string;
            userId?: string;
            name?: string;
            /** @enum {string} */
            membershipState?: "HELD" | "ACTIVE" | "WITHDRAWN" | "TRANSFERRED" | "REFUNDED" | "RELEASED" | "COMPLETED";
            items?: components["schemas"]["Item"][];
            result?: components["schemas"]["Result"];
        };
        Result: {
            totalPercent?: number;
            passed?: boolean;
            /** Format: date-time */
            finalizedAt?: string;
        };
        Version: {
            /** Format: int32 */
            version?: number;
            title?: string;
            instructions?: string;
            reason?: string;
            /** Format: date-time */
            changedAt?: string;
        };
        TeachingQuestionPage: {
            list?: {
                [key: string]: unknown;
            }[];
            total?: string;
            pendingTotal?: string;
            /** Format: date-time */
            oldestPendingAt?: string;
        };
        StudentAssignment: {
            courseId?: string;
            title?: string;
        };
        StudentView: {
            assignment?: components["schemas"]["Assignment"];
            versions?: components["schemas"]["AssignmentVersion"][];
            historyOnly?: boolean;
            canSubmit?: boolean;
            submissions?: components["schemas"]["Submission"][];
        };
        ClassMembership: {
            classroom?: components["schemas"]["Classroom"];
            member?: components["schemas"]["Membership"];
        };
        StudyView: {
            classroom?: components["schemas"]["Classroom"];
            member?: components["schemas"]["Membership"];
            activities?: components["schemas"]["Activity"][];
        };
        FailurePage: {
            list?: {
                [key: string]: unknown;
            }[];
            total?: string;
            /** Format: int32 */
            pageNo?: number;
            /** Format: int32 */
            pageSize?: number;
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
    feedback: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                courseId: string;
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Feedback"];
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
                        data: components["schemas"]["Submission"];
                    };
                };
            };
        };
    };
    review: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
                activity: string;
                submission: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Review"];
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
                        data: components["schemas"]["Submission"];
                    };
                };
            };
        };
    };
    changeGrade: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
                activity: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Change"];
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
    publishGrade: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
                activity: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Publication"];
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
    extend: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
                activity: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Extension"];
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
    correct: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
                activity: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Correction"];
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
                        data: components["schemas"]["Activity"];
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
    rule: {
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
                "application/json": components["schemas"]["RuleForm"];
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
    configuration: {
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
                        data: components["schemas"]["AssignmentView"];
                    };
                };
            };
        };
    };
    configure: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                courseId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Configuration"];
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
                        data: components["schemas"]["Assignment"];
                    };
                };
            };
        };
    };
    correct_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                courseId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Correction"];
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
                        data: components["schemas"]["AssignmentView"];
                    };
                };
            };
        };
    };
    update_1: {
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
                "application/json": components["schemas"]["Setup"];
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
                        data: components["schemas"]["Classroom"];
                    };
                };
            };
        };
    };
    state: {
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
                "application/json": components["schemas"]["StateChange"];
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
                        data: components["schemas"]["Classroom"];
                    };
                };
            };
        };
    };
    assign: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
                user: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["StaffChange"];
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
    move: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
                member: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["MoveMember"];
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
    start: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Start"];
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
    heartbeat: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Heartbeat"];
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
    end: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Heartbeat"];
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
    teachingDiscussion: {
        parameters: {
            query?: {
                before?: string;
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
                        data: components["schemas"]["Discussion"][];
                    };
                };
            };
        };
    };
    teachingReply: {
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
                "application/json": components["schemas"]["DiscussionForm"];
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
                        data: components["schemas"]["Discussion"];
                    };
                };
            };
        };
    };
    archive: {
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
    activities: {
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
                        data: components["schemas"]["Activity"][];
                    };
                };
            };
        };
    };
    publish: {
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
                "application/json": components["schemas"]["Form"];
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
                        data: components["schemas"]["Activity"];
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
                keyword?: string;
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
    accept: {
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
                "application/json": components["schemas"]["Acceptance"];
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
    submit: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                courseId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Submit"];
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
                        data: components["schemas"]["Submission"];
                    };
                };
            };
        };
    };
    enroll: {
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
                        data: components["schemas"]["Membership"];
                    };
                };
            };
        };
    };
    discussion: {
        parameters: {
            query?: {
                before?: string;
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
                        data: components["schemas"]["Discussion"][];
                    };
                };
            };
        };
    };
    discuss: {
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
                "application/json": components["schemas"]["DiscussionForm"];
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
                        data: components["schemas"]["Discussion"];
                    };
                };
            };
        };
    };
    submit_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
                activity: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Submit"];
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
                        data: components["schemas"]["Submission"];
                    };
                };
            };
        };
    };
    retry: {
        parameters: {
            query?: never;
            header: {
                "Idempotency-Key": string;
            };
            path: {
                requestId: string;
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
    administered: {
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
                        data: components["schemas"]["Classroom"][];
                    };
                };
            };
        };
    };
    create_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Setup"];
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
                        data: components["schemas"]["Classroom"];
                    };
                };
            };
        };
    };
    add: {
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
                "application/json": components["schemas"]["AddMember"];
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
                        data: components["schemas"]["Membership"];
                    };
                };
            };
        };
    };
    copy: {
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
                "application/json": components["schemas"]["CopySchedule"];
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
                        data: components["schemas"]["Activity"][];
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
    overview: {
        parameters: {
            query: {
                courseId: string;
                userId?: string;
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
                        data: Record<string, never>;
                    };
                };
            };
        };
    };
    teachingAssignment: {
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
                        data: components["schemas"]["AssignmentView"];
                    };
                };
            };
        };
    };
    teachingWork: {
        parameters: {
            query: {
                page: components["schemas"]["PageQuery"];
            };
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
                        data: components["schemas"]["SubmissionPage"];
                    };
                };
            };
        };
    };
    teachingHistory: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                courseId: string;
                studentId: string;
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
                        data: components["schemas"]["Submission"][];
                    };
                };
            };
        };
    };
    pending: {
        parameters: {
            query: {
                courseId?: string;
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
                        data: components["schemas"]["PendingPage"];
                    };
                };
            };
        };
    };
    assigned: {
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
                        data: components["schemas"]["Classroom"][];
                    };
                };
            };
        };
    };
    staff: {
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
                        data: components["schemas"]["Staff"][];
                    };
                };
            };
        };
    };
    sections: {
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
                        data: components["schemas"]["Section"][];
                    };
                };
            };
        };
    };
    pending_1: {
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
                        data: components["schemas"]["Pending"];
                    };
                };
            };
        };
    };
    papers: {
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
                        data: components["schemas"]["Paper"][];
                    };
                };
            };
        };
    };
    roster: {
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
                        data: components["schemas"]["RosterRow"][];
                    };
                };
            };
        };
    };
    teachingGrades: {
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
                        data: components["schemas"]["Report"][];
                    };
                };
            };
        };
    };
    export_1: {
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
                    "text/csv": string;
                };
            };
        };
    };
    teachingVersions: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
                activity: string;
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
                        data: components["schemas"]["Version"][];
                    };
                };
            };
        };
    };
    submissions: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
                activity: string;
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
                        data: components["schemas"]["Submission"][];
                    };
                };
            };
        };
    };
    available: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                course: string;
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
                        data: components["schemas"]["Classroom"][];
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
    teachingQuestions: {
        parameters: {
            query: {
                courseId?: string;
                pendingOnly?: boolean;
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
                        data: components["schemas"]["TeachingQuestionPage"];
                    };
                };
            };
        };
    };
    seasons: {
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
    rewards: {
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
                        data: Record<string, never>;
                    };
                };
            };
        };
    };
    publicRules: {
        parameters: {
            query?: {
                month?: string;
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
                        data: Record<string, never>;
                    };
                };
            };
        };
    };
    assignments: {
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
                        data: components["schemas"]["StudentAssignment"][];
                    };
                };
            };
        };
    };
    assignment: {
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
                        data: components["schemas"]["StudentView"];
                    };
                };
            };
        };
    };
    points: {
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
                        data: Record<string, never>;
                    };
                };
            };
        };
    };
    overview_1: {
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
    mine: {
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
                        data: components["schemas"]["ClassMembership"][];
                    };
                };
            };
        };
    };
    study: {
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
                        data: components["schemas"]["StudyView"];
                    };
                };
            };
        };
    };
    ownGrades: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
                member: string;
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
                        data: components["schemas"]["Report"];
                    };
                };
            };
        };
    };
    versions: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
                activity: string;
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
                        data: components["schemas"]["Version"][];
                    };
                };
            };
        };
    };
    ownSubmissions: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
                activity: string;
                member: string;
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
                        data: components["schemas"]["Submission"][];
                    };
                };
            };
        };
    };
    board: {
        parameters: {
            query: {
                month?: string;
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
                        data: Record<string, never>;
                    };
                };
            };
        };
    };
    adminSeasons: {
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
    adminRewards: {
        parameters: {
            query: {
                month?: string;
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
                        data: Record<string, never>;
                    };
                };
            };
        };
    };
    rules: {
        parameters: {
            query: {
                month: string;
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
                        data: Record<string, never>;
                    };
                };
            };
        };
    };
    reconciliations: {
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
                        data: Record<string, never>;
                    };
                };
            };
        };
    };
    statistics: {
        parameters: {
            query?: {
                month?: string;
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
                        data: Record<string, never>;
                    };
                };
            };
        };
    };
    achievements: {
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
    page: {
        parameters: {
            query?: {
                pageNo?: number;
                pageSize?: number;
                identifier?: string;
                errorCode?: string;
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
                        data: components["schemas"]["FailurePage"];
                    };
                };
            };
        };
    };
    page_1: {
        parameters: {
            query?: {
                pageNo?: number;
                pageSize?: number;
                identifier?: string;
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
                        data: components["schemas"]["FailurePage"];
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
    page_2: {
        parameters: {
            query?: {
                pageNo?: number;
                pageSize?: number;
                identifier?: string;
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
                        data: components["schemas"]["FailurePage"];
                    };
                };
            };
        };
    };
    page_3: {
        parameters: {
            query?: {
                pageNo?: number;
                pageSize?: number;
                actor?: string;
                result?: string;
                requestId?: string;
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
