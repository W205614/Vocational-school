export interface paths {
    "/api/v2/services/message/sms-platforms/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 根据id查询短信平台信息 */
        get: operations["querySmsThirdPlatform"];
        /** 更新短信平台信息 */
        put: operations["updateSmsThirdPlatform"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/message/notice-templates/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 根据id查询模板 */
        get: operations["queryNoticeTemplate"];
        /** 更新通知模板 */
        put: operations["updateNoticeTemplate"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/message/notice-tasks/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 根据id查询任务 */
        get: operations["queryNoticeTask"];
        /** 更新通知任务 */
        put: operations["updateNoticeTask"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/message/message-templates/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 根据id查询短信模板 */
        get: operations["queryMessageTemplate"];
        /** 更新短信模板 */
        put: operations["updateMessageTemplate"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/message/inboxes/{id}/read": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["read"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/message/sms/message": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 同步发送短信 */
        post: operations["sendMessage"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/message/sms-platforms": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 分页查询短信平台信息 */
        get: operations["querySmsThirdPlatforms"];
        put?: never;
        /** 新增短信平台信息 */
        post: operations["saveSmsThirdPlatform"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/message/notice-templates": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 分页查询通知模板 */
        get: operations["queryNoticeTemplates"];
        put?: never;
        /** 新增通知模板 */
        post: operations["saveNoticeTemplate"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/message/notice-tasks": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 分页查询通知任务 */
        get: operations["queryNoticeTasks"];
        put?: never;
        /** 新增通知任务 */
        post: operations["saveNoticeTask"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/message/message-templates": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 分页查询短信模板 */
        get: operations["queryMessageTemplates"];
        put?: never;
        /** 新增短信模板 */
        post: operations["saveMessageTemplate"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/message/inboxes": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 分页查询收件箱 */
        get: operations["queryUserInBoxesPage"];
        put?: never;
        /** 发送私信 */
        post: operations["sentMessageToUser"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/operation-failures/message/{id}/replay": {
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
    "/api/v2/admin/events/message/{id}/replay": {
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
    "/api/v2/admin/deliveries/{kind}/{id}/replay": {
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
    "/api/v2/admin/consumer-failures/message/{id}/replay": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["replay_3"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/operations/message/{id}": {
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
    "/api/v2/admin/operation-failures/message": {
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
    "/api/v2/admin/events/message/failures": {
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
    "/api/v2/admin/deliveries/{kind}": {
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
    "/api/v2/admin/consumer-failures/message": {
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
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        /**
         * @description 短信第三方平台信息的表单实体
         * @default null
         */
        SmsThirdPlatformFormDTO: {
            /**
             * @description 短信平台id，新增时无需填写
             * @default
             */
            id: string;
            /**
             * @description 短信平台名称
             * @default
             */
            name: string;
            /**
             * @description 短信平台代码，例如：ali
             * @default
             */
            code: string;
            /**
             * Format: int32
             * @description 数字越小优先级越高，最小为0
             * @default
             */
            priority: number;
            /**
             * Format: int32
             * @description 短信平台状态：0-禁用，1-启用
             * @default
             */
            status: number;
        };
        /**
         * @description 短信模板表单实体
         * @default null
         */
        MessageTemplateFormDTO: {
            /**
             * @description 信发送模板id，新增时无需填写
             * @default
             */
            id: string;
            /**
             * @description 第三方短信平台代号
             * @default
             */
            platformCode: string;
            /**
             * @description 第三方平台短信签名
             * @default
             */
            signName: string;
            /**
             * @description 第三方平台短信模板code
             * @default
             */
            thirdTemplateCode: string;
            /**
             * Format: int32
             * @description 模板状态:  0-草稿，1-使用中，2-停用
             * @default
             */
            status: number;
            /**
             * @description 短信模板名称，如果是通知模板下的短信模板，则无需填写
             * @default
             */
            name: string;
            /**
             * @description 短信模板内容预览，如果是通知模板下的短信模板，则无需填写
             * @default
             */
            content: string;
        };
        /**
         * @description 通知模板表单实体
         * @default null
         */
        NoticeTemplateFormDTO: {
            /**
             * @description 主键id，新增时无需填写
             * @default
             */
            id: string;
            /**
             * @description 模板名称
             * @default
             */
            name: string;
            /**
             * @description 模板代号，例如：VERIFY_CODE
             * @default
             */
            code: string;
            /**
             * Format: int32
             * @description 通知类型：0-系统通知，1-笔记通知，2-问答通知，3-其它通知
             * @default
             */
            type: number;
            /**
             * Format: int32
             * @description 模板状态:  0-草稿，1-使用中，2-停用
             * @default
             */
            status: number;
            /**
             * @description 通知标题
             * @default
             */
            title: string;
            /**
             * @description 通知内容
             * @default
             */
            content: string;
            /**
             * @description 是否是短信模板，默认false
             * @default false
             */
            isSmsTemplate: boolean;
            /**
             * @description 如果需要添加短信模板时填写这个，可以包含不同短信渠道
             * @default
             */
            messageTemplates: components["schemas"]["MessageTemplateFormDTO"][];
            /**
             * @description 如果需要删除已添加的短信模板时填写这个，只填id
             * @default
             */
            deleteMessageTemplates: string[];
        };
        /**
         * @description 通知任务的表单实体
         * @default null
         */
        NoticeTaskFormDTO: {
            /**
             * @description 任务id，新增时无需填写
             * @default
             */
            id: string;
            /**
             * @description 任务要发送的通知模板id
             * @default
             */
            templateId: string;
            /**
             * @description 任务名称
             * @default
             */
            name: string;
            /**
             * @description true-指定部分用户;false-通知所有人。默认false
             * @default false
             */
            partial: boolean;
            userIds?: string[];
            /**
             * Format: date-time
             * @description 任务预期执行时间，如果为null，或者小于等于当前时间，则立刻执行
             * @default
             */
            pushTime: string;
            /**
             * Format: int32
             * @description 任务重复执行次数上限，0则不重复
             * @default
             */
            maxTimes: number;
            /**
             * @description 任务重复执行时间间隔，单位是分钟
             * @default
             */
            interval: string;
            /**
             * Format: date-time
             * @description 任务失效时间
             * @default
             */
            expireTime: string;
        };
        /**
         * @description 短信发送参数
         * @default null
         */
        SmsInfoDTO: {
            templateCode?: string;
            phones?: unknown;
            templateParams?: {
                [key: string]: string;
            };
        };
        /**
         * @description 用户私信表单实体
         * @default null
         */
        UserInboxFormDTO: {
            /**
             * @description 目标用户id
             * @default
             */
            userId: string;
            /**
             * @description 私信内容
             * @default
             */
            content: string;
        };
        /**
         * @description 通知模板查询对象
         * @default null
         */
        SmsThirdPlatformPageQuery: {
            /** Format: int32 */
            pageNo?: number;
            /** Format: int32 */
            pageSize?: number;
            isAsc?: boolean;
            sortBy?: string;
            /** Format: int32 */
            status?: number;
            keyword?: string;
        };
        /**
         * @description 分页结果
         * @default null
         */
        PageDTOSmsThirdPlatformDTO: {
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
            list: components["schemas"]["SmsThirdPlatformDTO"][];
        };
        /**
         * @description 短信第三方平台信息
         * @default null
         */
        SmsThirdPlatformDTO: {
            /**
             * @description 短信平台id，新增时无需填写
             * @default
             */
            id: string;
            /**
             * @description 短信平台名称
             * @default
             */
            name: string;
            /**
             * @description 短信平台代码，例如：ali
             * @default
             */
            code: string;
            /**
             * Format: int32
             * @description 数字越小优先级越高，最小为0
             * @default
             */
            priority: number;
            /**
             * Format: int32
             * @description 短信平台状态：0-禁用，1-启用
             * @default
             */
            status: number;
        };
        /**
         * @description 通知模板查询对象
         * @default null
         */
        NoticeTemplatePageQuery: {
            /** Format: int32 */
            pageNo?: number;
            /** Format: int32 */
            pageSize?: number;
            isAsc?: boolean;
            sortBy?: string;
            /** Format: int32 */
            status?: number;
            keyword?: string;
        };
        /**
         * @description 通知模板实体
         * @default null
         */
        NoticeTemplateDTO: {
            /**
             * @description 主键id，新增时无需填写
             * @default
             */
            id: string;
            /**
             * @description 模板名称
             * @default
             */
            name: string;
            /**
             * @description 模板代号，例如：VERIFY_CODE
             * @default
             */
            code: string;
            /**
             * Format: int32
             * @description 通知类型：0-系统通知，1-笔记通知，2-问答通知，3-其它通知
             * @default
             */
            type: number;
            /**
             * Format: int32
             * @description 模板状态:  0-草稿，1-使用中，2-停用
             * @default
             */
            status: number;
            /**
             * @description 通知标题
             * @default
             */
            title: string;
            /**
             * @description 通知内容
             * @default
             */
            content: string;
            /**
             * @description 是否是短信模板，默认false
             * @default false
             */
            isSmsTemplate: boolean;
        };
        /**
         * @description 分页结果
         * @default null
         */
        PageDTONoticeTemplateDTO: {
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
            list: components["schemas"]["NoticeTemplateDTO"][];
        };
        /**
         * @description 通知模板查询对象
         * @default null
         */
        NoticeTaskPageQuery: {
            /** Format: int32 */
            pageNo?: number;
            /** Format: int32 */
            pageSize?: number;
            isAsc?: boolean;
            sortBy?: string;
            finished?: boolean;
            keyword?: string;
            /** Format: date-time */
            minPushTime?: string;
            /** Format: date-time */
            maxPushTime?: string;
        };
        /**
         * @description 通知任务
         * @default null
         */
        NoticeTaskDTO: {
            /**
             * @description 任务id，新增时无需填写
             * @default
             */
            id: string;
            /**
             * @description 任务要发送的通知模板id
             * @default
             */
            templateId: string;
            /**
             * @description 任务名称
             * @default
             */
            name: string;
            /**
             * @description true-通知指定用户;false-全员公告。默认false
             * @default false
             */
            partial: boolean;
            userIds?: string[];
            /**
             * Format: date-time
             * @description 任务预期执行时间
             * @default
             */
            pushTime: string;
            /**
             * Format: int32
             * @description 任务重复执行次数上限，0则不重复
             * @default
             */
            maxTimes: number;
            /**
             * @description 任务重复执行时间间隔，单位是分钟
             * @default
             */
            interval: string;
            /**
             * Format: date-time
             * @description 任务失效时间
             * @default
             */
            expireTime: string;
            /**
             * @description 任务是否已经完成。默认false
             * @default false
             */
            finished: boolean;
        };
        /**
         * @description 分页结果
         * @default null
         */
        PageDTONoticeTaskDTO: {
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
            list: components["schemas"]["NoticeTaskDTO"][];
        };
        /**
         * @description 通知模板查询对象
         * @default null
         */
        MessageTemplatePageQuery: {
            /** Format: int32 */
            pageNo?: number;
            /** Format: int32 */
            pageSize?: number;
            isAsc?: boolean;
            sortBy?: string;
            thirdPlatformId?: string;
            /** Format: int32 */
            status?: number;
            keyword?: string;
        };
        /**
         * @description 短信模板
         * @default null
         */
        MessageTemplateDTO: {
            /**
             * @description 信发送模板id，新增时无需填写
             * @default
             */
            id: string;
            /**
             * @description 第三方短信推送渠道id
             * @default
             */
            platformCode: string;
            /**
             * @description 第三方短信推送渠道名称
             * @default
             */
            platformName: string;
            /**
             * @description 短信模板名称
             * @default
             */
            name: string;
            /**
             * @description 短信模板预览内容
             * @default
             */
            content: string;
            /**
             * @description 第三方平台短信签名
             * @default
             */
            signName: string;
            /**
             * @description 第三方平台短信模板code
             * @default
             */
            thirdTemplateCode: string;
            /**
             * Format: int32
             * @description 模板状态:  0-草稿，1-使用中，2-停用
             * @default
             */
            status: number;
        };
        /**
         * @description 分页结果
         * @default null
         */
        PageDTOMessageTemplateDTO: {
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
            list: components["schemas"]["MessageTemplateDTO"][];
        };
        /**
         * @description 通知模板查询对象
         * @default null
         */
        UserInboxQuery: {
            /** Format: int32 */
            pageNo?: number;
            /** Format: int32 */
            pageSize?: number;
            isAsc?: boolean;
            sortBy?: string;
            isRead?: boolean;
            /** Format: int32 */
            type?: number;
        };
        /**
         * @description 分页结果
         * @default null
         */
        PageDTOUserInboxDTO: {
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
            list: components["schemas"]["UserInboxDTO"][];
        };
        /**
         * @description 用户收件箱消息
         * @default null
         */
        UserInboxDTO: {
            /**
             * @description 收件箱消息id
             * @default
             */
            id: string;
            /**
             * Format: int32
             * @description 通知类型：0-系统通知，1-笔记通知，2-问答通知，3-其它通知，4-私信
             * @default
             */
            type: number;
            /**
             * @description 通知标题
             * @default
             */
            title: string;
            /**
             * @description 通知或私信内容
             * @default
             */
            content: string;
            /**
             * @description 是否已读
             * @default false
             */
            isRead: boolean;
            /**
             * @description 消息发送者id
             * @default
             */
            publisher: string;
            /**
             * Format: date-time
             * @description 收件箱消息id
             * @default
             */
            pushTime: string;
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
    querySmsThirdPlatform: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /**
                 * @description 短信平台id
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
                        data: components["schemas"]["SmsThirdPlatformDTO"];
                    };
                };
            };
        };
    };
    updateSmsThirdPlatform: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /**
                 * @description 短信平台id
                 * @example 1
                 */
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SmsThirdPlatformFormDTO"];
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
    queryNoticeTemplate: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /**
                 * @description 模板id
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
                        data: components["schemas"]["NoticeTemplateDTO"];
                    };
                };
            };
        };
    };
    updateNoticeTemplate: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /**
                 * @description 模板id
                 * @example 1
                 */
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["NoticeTemplateFormDTO"];
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
    queryNoticeTask: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /**
                 * @description 任务id
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
                        data: components["schemas"]["NoticeTaskDTO"];
                    };
                };
            };
        };
    };
    updateNoticeTask: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /**
                 * @description 任务id
                 * @example 1
                 */
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["NoticeTaskFormDTO"];
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
    queryMessageTemplate: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /**
                 * @description 模板id
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
                        data: components["schemas"]["MessageTemplateDTO"];
                    };
                };
            };
        };
    };
    updateMessageTemplate: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /**
                 * @description 短信模板id
                 * @example 1
                 */
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["MessageTemplateFormDTO"];
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
    read: {
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
    sendMessage: {
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
                "application/json": components["schemas"]["SmsInfoDTO"];
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
    querySmsThirdPlatforms: {
        parameters: {
            query: {
                pageQuery: components["schemas"]["SmsThirdPlatformPageQuery"];
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
                        data: components["schemas"]["PageDTOSmsThirdPlatformDTO"];
                    };
                };
            };
        };
    };
    saveSmsThirdPlatform: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SmsThirdPlatformFormDTO"];
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
    queryNoticeTemplates: {
        parameters: {
            query: {
                pageQuery: components["schemas"]["NoticeTemplatePageQuery"];
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
                        data: components["schemas"]["PageDTONoticeTemplateDTO"];
                    };
                };
            };
        };
    };
    saveNoticeTemplate: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["NoticeTemplateFormDTO"];
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
    queryNoticeTasks: {
        parameters: {
            query: {
                pageQuery: components["schemas"]["NoticeTaskPageQuery"];
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
                        data: components["schemas"]["PageDTONoticeTaskDTO"];
                    };
                };
            };
        };
    };
    saveNoticeTask: {
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
                "application/json": components["schemas"]["NoticeTaskFormDTO"];
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
    queryMessageTemplates: {
        parameters: {
            query: {
                pageQuery: components["schemas"]["MessageTemplatePageQuery"];
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
                        data: components["schemas"]["PageDTOMessageTemplateDTO"];
                    };
                };
            };
        };
    };
    saveMessageTemplate: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["MessageTemplateFormDTO"];
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
    queryUserInBoxesPage: {
        parameters: {
            query: {
                query: components["schemas"]["UserInboxQuery"];
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
                        data: components["schemas"]["PageDTOUserInboxDTO"];
                    };
                };
            };
        };
    };
    sentMessageToUser: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UserInboxFormDTO"];
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
                kind: string;
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
    replay_3: {
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
            query?: never;
            header?: never;
            path: {
                kind: string;
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
                        data: Record<string, never>;
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
}
