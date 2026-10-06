export interface paths {
    "/api/v2/services/user/users": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** 更新当前登录用户信息，可修改密码 */
        put: operations["updateCurrentUser"];
        /** 新增用户，一般是员工或教师 */
        post: operations["saveUser"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/user/users/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 根据id查询用户信息 */
        get: operations["queryUserById"];
        /** 更新用户信息 */
        put: operations["updateUser"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/user/users/{id}/status/{status}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** 修改用户状态, status=0为禁用，status=1为正常 */
        put: operations["updateUserStatus"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/user/users/{id}/password/default": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** 重置密码 */
        put: operations["resetPassword"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/user/students/password": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** 修改学员密码 */
        put: operations["updateMyPassword"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/user/students/register": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 学员注册 */
        post: operations["registerStudent"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/operation-failures/user/{id}/replay": {
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
    "/api/v2/admin/events/user/{id}/replay": {
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
    "/api/v2/admin/consumer-failures/user/{id}/replay": {
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
    "/api/v2/services/user/users/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 获取当前登录用户信息 */
        get: operations["me"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/user/users/checkCellphone": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 检查用户手机号是否存在 */
        get: operations["checkCellPhone"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/user/teachers/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 分页查询教师信息 */
        get: operations["queryTeacherPage"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/user/students/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 分页查询学生信息 */
        get: operations["queryStudentPage"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/user/staffs/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 分页查询员工信息 */
        get: operations["queryStaffPage"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/operations/user/{id}": {
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
    "/api/v2/admin/operation-failures/user": {
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
    "/api/v2/admin/events/user/failures": {
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
    "/api/v2/admin/consumer-failures/user": {
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
         * @description 修改用户信息的表单，带有密码
         * @default null
         */
        UserFormDTO: {
            /**
             * @description 用户id
             * @default
             * @example 1
             */
            id: string;
            /**
             * @description 手机
             * @default
             * @example 13890011009
             */
            cellPhone: string;
            /**
             * @description 用户名称/昵称
             * @default
             * @example 李四
             */
            name: string;
            /**
             * Format: int32
             * @description 用户类型，1-其他员工,2-普通学员，3-老师
             * @default
             * @example 2
             */
            type: number;
            /**
             * @description 角色id，老师和学生不用填
             * @default
             * @example 5
             */
            roleId: string;
            /**
             * @description 头像
             * @default
             * @example default-user-icon.jpg
             */
            icon: string;
            /**
             * @description 岗位
             * @default
             * @example 讲师
             */
            job: string;
            /**
             * @description 个人介绍
             * @default
             * @example 黑马高级Java讲师
             */
            intro: string;
            /**
             * @description 形象照地址
             * @default
             * @example default-teacher-photo.jpg
             */
            photo: string;
            /**
             * @description 用户名
             * @default
             * @example 13800010004
             */
            username: string;
            /**
             * Format: email
             * @description 邮箱
             * @default
             */
            email: string;
            /**
             * @description QQ号码
             * @default
             */
            qq: string;
            /**
             * @description 省
             * @default
             */
            province: string;
            /**
             * @description 市
             * @default
             */
            city: string;
            /**
             * @description 区
             * @default
             */
            district: string;
            /**
             * Format: int32
             * @description 性别：0-男性，1-女性
             * @default
             * @example 0
             */
            gender: number;
            /**
             * @description 原始密码
             * @default
             * @example 123321
             */
            oldPassword: string;
            /**
             * @description 新密码
             * @default
             * @example 123321
             */
            password: string;
        };
        /**
         * @description 用户详情
         * @default null
         */
        UserDTO: {
            /**
             * @description 用户id
             * @default
             * @example 1
             */
            id: string;
            /**
             * @description 手机
             * @default
             * @example 13890011009
             */
            cellPhone: string;
            /**
             * @description 用户名称/昵称
             * @default
             * @example 李四
             */
            name: string;
            /**
             * Format: int32
             * @description 用户类型，1-其他员工,2-普通学员，3-老师
             * @default
             * @example 2
             */
            type: number;
            /**
             * @description 角色id，老师和学生不用填
             * @default
             * @example 5
             */
            roleId: string;
            /**
             * @description 头像
             * @default
             * @example default-user-icon.jpg
             */
            icon: string;
            /**
             * @description 岗位
             * @default
             * @example 讲师
             */
            job: string;
            /**
             * @description 个人介绍
             * @default
             * @example 黑马高级Java讲师
             */
            intro: string;
            /**
             * @description 形象照地址
             * @default
             * @example default-teacher-photo.jpg
             */
            photo: string;
            /**
             * @description 用户名
             * @default
             * @example 13800010004
             */
            username: string;
            /**
             * Format: email
             * @description 邮箱
             * @default
             */
            email: string;
            /**
             * @description QQ号码
             * @default
             */
            qq: string;
            /**
             * @description 省
             * @default
             */
            province: string;
            /**
             * @description 市
             * @default
             */
            city: string;
            /**
             * @description 区
             * @default
             */
            district: string;
            /**
             * Format: int32
             * @description 性别：0-男性，1-女性
             * @default
             * @example 0
             */
            gender: number;
        };
        /**
         * @description 学生注册和修改密码的表单实体
         * @default null
         */
        StudentFormDTO: {
            /**
             * @description 手机号
             * @default
             * @example 13800010004
             */
            cellPhone: string;
            /**
             * @description 密码
             * @default
             * @example 123456
             */
            password: string;
            /**
             * @description 验证码
             * @default
             * @example 645632
             */
            code: string;
        };
        /**
         * @description 用户详情
         * @default null
         */
        UserDetailVO: {
            /**
             * @description 用户id
             * @default
             * @example 1
             */
            id: string;
            /**
             * @description 名字
             * @default
             * @example 张三
             */
            name: string;
            /**
             * @description 头像
             * @default
             * @example default-icon.jpg
             */
            icon: string;
            /**
             * @description 手机号
             * @default
             * @example 13800010004
             */
            cellPhone: string;
            /**
             * @description 用户名
             * @default
             * @example 13800010004
             */
            username: string;
            /**
             * @description 邮箱
             * @default
             */
            email: string;
            /**
             * @description QQ号码
             * @default
             */
            qq: string;
            /**
             * @description 个人介绍
             * @default
             */
            intro: string;
            /**
             * @description 省
             * @default
             */
            province: string;
            /**
             * @description 市
             * @default
             */
            city: string;
            /**
             * @description 区
             * @default
             */
            district: string;
            /**
             * Format: int32
             * @description 性别：0-男性，1-女性
             * @default
             * @example 0
             */
            gender: number;
            /**
             * Format: date-time
             * @description 注册时间
             * @default
             * @example 2022-07-12
             */
            createTime: string;
            /**
             * @description 角色名称
             * @default
             * @example 教师
             */
            roleName: string;
        };
        /**
         * @description 教师分页查询条件
         * @default null
         */
        UserPageQuery: {
            /** Format: int32 */
            pageNo?: number;
            /** Format: int32 */
            pageSize?: number;
            isAsc?: boolean;
            sortBy?: string;
            /**
             * Format: int32
             * @description 账户状态
             * @default
             */
            status: number;
            /**
             * @description 教师名称
             * @default
             */
            name: string;
            /**
             * @description 手机号码
             * @default
             */
            phone: string;
        };
        /**
         * @description 分页结果
         * @default null
         */
        PageDTOTeacherPageVO: {
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
            list: components["schemas"]["TeacherPageVO"][];
        };
        /**
         * @description 分页教师信息
         * @default null
         */
        TeacherPageVO: {
            /**
             * @description 教师id，也是用户id
             * @default
             * @example 1
             */
            id: string;
            /**
             * @description 教师名称
             * @default
             * @example 罗老师
             */
            name: string;
            /**
             * @description 头像
             * @default
             * @example default-user-icon.jpg
             */
            icon: string;
            /**
             * @description 手机号
             * @default
             * @example 13980019001
             */
            cellPhone: string;
            /**
             * @description 岗位
             * @default
             * @example 讲师
             */
            job: string;
            /**
             * @description 介绍
             * @default
             * @example 黑马高级Java讲师
             */
            intro: string;
            /**
             * Format: int32
             * @description 负责的课程数量
             * @default
             * @example 10
             */
            courseAmount: number;
            /**
             * Format: int32
             * @description 出题数量
             * @default
             * @example 18
             */
            examQuestionAmount: number;
            /**
             * Format: date-time
             * @description 注册时间
             * @default
             * @example 2022-07-12
             */
            createTime: string;
            /**
             * Format: int32
             * @description 账户状态，0-禁用，1-正常
             * @default
             * @example 1
             */
            status: number;
            /**
             * @description 形象照片地址
             * @default
             * @example default-user-icon.jpg
             */
            photo: string;
        };
        /**
         * @description 分页结果
         * @default null
         */
        PageDTOStudentPageVo: {
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
            list: components["schemas"]["StudentPageVo"][];
        };
        /**
         * @description 学生信息
         * @default null
         */
        StudentPageVo: {
            /**
             * @description 学生id，也是用户id
             * @default
             * @example 1
             */
            id: string;
            /**
             * @description 学生名称
             * @default
             * @example 张三
             */
            name: string;
            /**
             * @description 头像
             * @default
             * @example default-icon.jpg
             */
            icon: string;
            /**
             * @description 手机号
             * @default
             * @example 13800010004
             */
            cellPhone: string;
            /**
             * Format: int32
             * @description 性别：0-男性，1-女性
             * @default
             * @example 0
             */
            gender: number;
            /**
             * Format: int32
             * @description 购买/报名课程数量
             * @default
             * @example 12
             */
            courseAmount: number;
            /**
             * Format: date-time
             * @description 注册时间
             * @default
             * @example 2022-07-12
             */
            createTime: string;
            /**
             * Format: int32
             * @description 账户状态，0-禁用，1-正常
             * @default
             * @example 1
             */
            status: number;
        };
        /**
         * @description 分页结果
         * @default null
         */
        PageDTOStaffVO: {
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
            list: components["schemas"]["StaffVO"][];
        };
        /**
         * @description 员工用户
         * @default null
         */
        StaffVO: {
            /**
             * @description 主键
             * @default
             * @example 1
             */
            id: string;
            /**
             * @description 头像
             * @default
             * @example default-user-icon.jpg
             */
            icon: string;
            /**
             * @description 手机号
             * @default
             * @example 13800010002
             */
            cellPhone: string;
            /**
             * @description 员工姓名
             * @default
             * @example user_138foo0002
             */
            name: string;
            /**
             * @description 角色id
             * @default
             * @example 5
             */
            roleId: string;
            /**
             * @description 角色名称
             * @default
             * @example 5
             */
            roleName: string;
            /**
             * Format: date-time
             * @description 注册时间
             * @default
             * @example 2022-07-22
             */
            createTime: string;
            /**
             * Format: int32
             * @description 账号状态
             * @default
             * @example 0
             */
            status: number;
        };
        View: {
            operationId?: string;
            status?: string;
            result?: unknown;
            errorCode?: string;
            errorMessage?: string;
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
    updateCurrentUser: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UserFormDTO"];
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
    saveUser: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UserDTO"];
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
                        data: string;
                    };
                };
            };
        };
    };
    queryUserById: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 用户id */
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
                        data: components["schemas"]["UserDTO"];
                    };
                };
            };
        };
    };
    updateUser: {
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
                "application/json": components["schemas"]["UserDTO"];
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
    updateUserStatus: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /**
                 * @description 要重置的用户的id
                 * @example 1
                 */
                id: string;
                /**
                 * @description 状态
                 * @example 1
                 */
                status: string;
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
    resetPassword: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /**
                 * @description 要重置的用户的id
                 * @example 1
                 */
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
    updateMyPassword: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["StudentFormDTO"];
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
    registerStudent: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["StudentFormDTO"];
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
    me: {
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
                        data: components["schemas"]["UserDetailVO"];
                    };
                };
            };
        };
    };
    checkCellPhone: {
        parameters: {
            query: {
                cellphone: string;
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
    queryTeacherPage: {
        parameters: {
            query: {
                pageQuery: components["schemas"]["UserPageQuery"];
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
                        data: components["schemas"]["PageDTOTeacherPageVO"];
                    };
                };
            };
        };
    };
    queryStudentPage: {
        parameters: {
            query: {
                pageQuery: components["schemas"]["UserPageQuery"];
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
                        data: components["schemas"]["PageDTOStudentPageVo"];
                    };
                };
            };
        };
    };
    queryStaffPage: {
        parameters: {
            query: {
                pageQuery: components["schemas"]["UserPageQuery"];
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
                        data: components["schemas"]["PageDTOStaffVO"];
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
