export interface paths {
    "/api/v2/services/promotion/user-coupons/use": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** 核销指定优惠券 */
        put: operations["writeOffCoupon"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/promotion/user-coupons/refund": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** 退还指定优惠券 */
        put: operations["refundCoupon"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/promotion/coupons/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 根据id查询优惠卷 */
        get: operations["queryCouponById"];
        /** 根据id修改优惠卷 */
        put: operations["updateCouponById"];
        post?: never;
        /** 根据id删除优惠卷 */
        delete: operations["deleteCouponById"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/promotion/coupons/{id}/pause": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** 根据id暂停发放优惠卷 */
        put: operations["pauseIssueCouponById"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/promotion/coupons/{id}/issue": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** 发放优惠卷接口 */
        put: operations["beginIssue"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/promotion/user-coupons/{couponId}/receive": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 领取优惠卷接口 */
        post: operations["receiveCoupon"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/promotion/user-coupons/{code}/exchange": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 兑换码兑换优惠券接口 */
        post: operations["exchangeCoupon"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/promotion/user-coupons/discount": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 根据券方案计算订单优惠明细 */
        post: operations["queryDiscountDetailByOrder"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/promotion/user-coupons/available": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 查询我的优惠券可用方案 */
        post: operations["findDiscountSolution"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/promotion/coupons": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 新增优惠卷接口 */
        post: operations["saveCoupon"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/coupons/{id}/claims": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["claim"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/coupon-exchanges": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["exchange"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/operation-failures/promotion/{id}/replay": {
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
    "/api/v2/admin/events/promotion/{id}/replay": {
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
    "/api/v2/admin/consumer-failures/promotion/{id}/replay": {
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
    "/api/v2/services/promotion/user-coupons/rules": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 分页查询我的优惠券接口 */
        get: operations["queryDiscountRules"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/promotion/user-coupons/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 分页查询我的优惠券 */
        get: operations["queryMyCouponPage"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/promotion/coupons/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 分页查询优惠卷接口 */
        get: operations["queryCouponByPage"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/promotion/coupons/list": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询发放中的优惠卷列表 */
        get: operations["queryIssuingCoupons"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/promotion/codes/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 分页查询兑换码 */
        get: operations["queryExchangeCodeByPage"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/operations/promotion/{id}": {
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
    "/api/v2/admin/operation-failures/promotion": {
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
    "/api/v2/admin/events/promotion/failures": {
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
    "/api/v2/admin/consumer-failures/promotion": {
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
        CouponReservationDTO: {
            userId?: string;
            couponIds?: string[];
            /** Format: date-time */
            expiresAt?: string;
        };
        /**
         * @description 优惠券表单数据
         * @default null
         */
        CouponFormDTO: {
            /**
             * @description 优惠券id，新增不需要添加，更新必填
             * @default
             */
            id: string;
            /**
             * @description 优惠券名称
             * @default
             */
            name: string;
            /**
             * @description 是否添限定使用范围，true：限定了，false：没限定
             * @default false
             */
            specific: boolean;
            /**
             * @description 优惠券使用范围
             * @default
             */
            scopes: string[];
            /**
             * Format: int32
             * @description 优惠券类型，1：每满减，2：折扣，3：无门槛，4：普通满减
             * @default
             * @enum {integer}
             */
            discountType: 1 | 2 | 3 | 4;
            /**
             * Format: int32
             * @description 折扣门槛，0代表无门槛
             * @default
             */
            thresholdAmount: number;
            /**
             * Format: int32
             * @description 折扣值，满减填抵扣金额；打折填折扣值：80标示打8折
             * @default
             */
            discountValue: number;
            /**
             * Format: int32
             * @description 最大优惠金额
             * @default
             */
            maxDiscountAmount: number;
            /**
             * Format: int32
             * @description 优惠券总量
             * @default
             */
            totalNum: number;
            /**
             * Format: int32
             * @description 每人领取的上限
             * @default
             */
            userLimit: number;
            /**
             * Format: int32
             * @description 获取方式1：手动领取，2：指定发放（通过兑换码兑换）
             * @default
             * @enum {integer}
             */
            obtainWay: 1 | 2;
        };
        /**
         * @description 优惠券发放的表单实体
         * @default null
         */
        CouponIssueFormDTO: {
            /**
             * @description 优惠券id
             * @default
             */
            id: string;
            /**
             * Format: date-time
             * @description 发放开始时间
             * @default
             */
            issueBeginTime: string;
            /**
             * Format: date-time
             * @description 发放结束时间
             * @default
             */
            issueEndTime: string;
            /**
             * Format: int32
             * @description 有效天数
             * @default
             */
            termDays: number;
            /**
             * Format: date-time
             * @description 使用有效期开始时间
             * @default
             */
            termBeginTime: string;
            /**
             * Format: date-time
             * @description 使用有效期结束时间
             * @default
             */
            termEndTime: string;
        };
        /**
         * @description 订单中课程及优惠券信息
         * @default null
         */
        OrderCouponDTO: {
            /**
             * @description 用户优惠券id
             * @default
             */
            userCouponIds: string[];
            /**
             * @description 订单中的课程列表
             * @default
             */
            courseList: components["schemas"]["OrderCourseDTO"][];
            orderId?: string;
        };
        /**
         * @description 订单中的课程信息
         * @default null
         */
        OrderCourseDTO: {
            /**
             * @description 课id
             * @default
             */
            id: string;
            /**
             * @description 课程的三级分类id
             * @default
             */
            cateId: string;
            /**
             * Format: int32
             * @description 课程价格
             * @default
             */
            price: number;
        };
        /**
         * @description 订单的可用优惠券及折扣信息
         * @default null
         */
        CouponDiscountDTO: {
            /**
             * @description 用户优惠券id集合
             * @default
             */
            ids: string[];
            /**
             * @description 优惠券规则
             * @default
             */
            rules: string[];
            /**
             * Format: int32
             * @description 本订单最大优惠金额
             * @default
             */
            discountAmount: number;
            /**
             * @description 优惠明细, key是课程id, value是课程优惠金额
             * @default
             */
            discountDetail: {
                [key: string]: number;
            };
        };
        View: {
            operationId?: string;
            status?: string;
            result?: unknown;
            errorCode?: string;
            errorMessage?: string;
        };
        Request: {
            couponId?: string;
            code?: string;
        };
        /**
         * @description 用户优惠券查询参数
         * @default null
         */
        UserCouponQuery: {
            /** Format: int32 */
            pageNo?: number;
            /** Format: int32 */
            pageSize?: number;
            isAsc?: boolean;
            sortBy?: string;
            /**
             * Format: int32
             * @description 优惠券状态，1：未使用，2：已使用，3：已过期
             * @default
             */
            status: number;
        };
        /**
         * @description 用户端优惠券信息
         * @default null
         */
        CouponVO: {
            /**
             * @description 优惠券id，新增不需要添加，更新必填
             * @default
             */
            id: string;
            /**
             * @description 优惠券名称
             * @default
             */
            name: string;
            /**
             * @description 是否限定使用范围
             * @default false
             */
            specific: boolean;
            /**
             * Format: int32
             * @description 优惠券类型，1：每满减，2：折扣，3：无门槛，4：普通满减
             * @default
             * @enum {integer}
             */
            discountType: 1 | 2 | 3 | 4;
            /**
             * Format: int32
             * @description 折扣门槛，0代表无门槛
             * @default
             */
            thresholdAmount: number;
            /**
             * Format: int32
             * @description 折扣值，满减填抵扣金额；打折填折扣值：80标示打8折
             * @default
             */
            discountValue: number;
            /**
             * Format: int32
             * @description 最大优惠金额
             * @default
             */
            maxDiscountAmount: number;
            /**
             * Format: int32
             * @description 有效天数
             * @default
             */
            termDays: number;
            /**
             * Format: date-time
             * @description 使用有效期结束时间
             * @default
             */
            termEndTime: string;
            /**
             * @description 是否可以领取
             * @default false
             */
            available: boolean;
            /**
             * @description 是否可以使用
             * @default false
             */
            received: boolean;
        };
        /**
         * @description 分页结果
         * @default null
         */
        PageDTOCouponVO: {
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
            list: components["schemas"]["CouponVO"][];
        };
        /**
         * @description 优惠券详细数据
         * @default null
         */
        CouponDetailVO: {
            /**
             * @description 优惠券id，新增不需要添加，更新必填
             * @default
             */
            id: string;
            /**
             * @description 优惠券名称
             * @default
             */
            name: string;
            /**
             * @description 优惠券使用范围，key范围类型(0-没有限制，1-限定分类，2-限定课程)，值是范围ID
             * @default
             */
            scopes: components["schemas"]["CouponScopeVO"][];
            /**
             * Format: int32
             * @description 优惠券类型，1：每满减，2：折扣，3：无门槛，4：普通满减
             * @default
             * @enum {integer}
             */
            discountType: 1 | 2 | 3 | 4;
            /**
             * Format: int32
             * @description 折扣门槛，0代表无门槛
             * @default
             */
            thresholdAmount: number;
            /**
             * Format: int32
             * @description 折扣值，满减填抵扣金额；打折填折扣值：80标示打8折
             * @default
             */
            discountValue: number;
            /**
             * Format: int32
             * @description 最大优惠金额
             * @default
             */
            maxDiscountAmount: number;
            /**
             * Format: date-time
             * @description 发放开始时间
             * @default
             */
            issueBeginTime: string;
            /**
             * Format: date-time
             * @description 发放结束时间
             * @default
             */
            issueEndTime: string;
            /**
             * Format: int32
             * @description 有效天数
             * @default
             */
            termDays: number;
            /**
             * Format: date-time
             * @description 使用有效期开始时间
             * @default
             */
            termBeginTime: string;
            /**
             * Format: date-time
             * @description 使用有效期结束时间
             * @default
             */
            termEndTime: string;
            /**
             * Format: int32
             * @description 优惠券总量，如果为0代表无上限
             * @default
             */
            totalNum: number;
            /**
             * Format: int32
             * @description 每人领取的上限
             * @default
             */
            userLimit: number;
            /**
             * Format: int32
             * @description 获取方式1：手动领取，2：指定发放（通过兑换码兑换）
             * @default
             * @enum {integer}
             */
            obtainWay: 1 | 2;
        };
        /**
         * @description 优惠券使用范围
         * @default null
         */
        CouponScopeVO: {
            /**
             * @description 范围id集合
             * @default
             */
            id: string;
            /**
             * @description 范围名称集合
             * @default
             */
            name: string;
        };
        /**
         * @description 优惠券查询参数
         * @default null
         */
        CouponQuery: {
            /** Format: int32 */
            pageNo?: number;
            /** Format: int32 */
            pageSize?: number;
            isAsc?: boolean;
            sortBy?: string;
            /**
             * Format: int32
             * @description 优惠券折扣类型：1：每满减，2：折扣，3：无门槛，4：满减
             * @default
             */
            type: number;
            /**
             * Format: int32
             * @description 优惠券状态，1：待发放，2：发放中，3：已结束, 4：取消/终止
             * @default
             */
            status: number;
            /**
             * @description 优惠券名称
             * @default
             */
            name: string;
        };
        /**
         * @description 优惠券分页数据
         * @default null
         */
        CouponPageVO: {
            /**
             * @description 优惠券id，新增不需要添加，更新必填
             * @default
             */
            id: string;
            /**
             * @description 优惠券名称
             * @default
             */
            name: string;
            /**
             * @description 是否限定使用范围
             * @default false
             */
            specific: boolean;
            /**
             * Format: int32
             * @description 优惠券类型，1：每满减，2：折扣，3：无门槛，4：普通满减
             * @default
             * @enum {integer}
             */
            discountType: 1 | 2 | 3 | 4;
            /**
             * Format: int32
             * @description 折扣门槛，0代表无门槛
             * @default
             */
            thresholdAmount: number;
            /**
             * Format: int32
             * @description 折扣值，满减填抵扣金额；打折填折扣值：80标示打8折
             * @default
             */
            discountValue: number;
            /**
             * Format: int32
             * @description 最大优惠金额
             * @default
             */
            maxDiscountAmount: number;
            /**
             * Format: int32
             * @description 获取方式1：手动领取，2：指定发放（通过兑换码兑换）
             * @default
             * @enum {integer}
             */
            obtainWay: 1 | 2;
            /**
             * Format: int32
             * @description 已使用
             * @default
             */
            usedNum: number;
            /**
             * Format: int32
             * @description 已发放数量
             * @default
             */
            issueNum: number;
            /**
             * Format: int32
             * @description 优惠券总量
             * @default
             */
            totalNum: number;
            /**
             * Format: date-time
             * @description 优惠券创建时间
             * @default
             */
            createTime: string;
            /**
             * Format: date-time
             * @description 发放开始时间
             * @default
             */
            issueBeginTime: string;
            /**
             * Format: date-time
             * @description 发放结束时间
             * @default
             */
            issueEndTime: string;
            /**
             * Format: int32
             * @description 有效天数
             * @default
             */
            termDays: number;
            /**
             * Format: date-time
             * @description 使用有效期开始时间
             * @default
             */
            termBeginTime: string;
            /**
             * Format: date-time
             * @description 使用有效期结束时间
             * @default
             */
            termEndTime: string;
            /**
             * Format: int32
             * @description 状态
             * @default
             * @enum {integer}
             */
            status: 1 | 2 | 3 | 4 | 5;
        };
        /**
         * @description 分页结果
         * @default null
         */
        PageDTOCouponPageVO: {
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
            list: components["schemas"]["CouponPageVO"][];
        };
        /**
         * @description 兑换码查询参数
         * @default null
         */
        CodeQuery: {
            /** Format: int32 */
            pageNo?: number;
            /** Format: int32 */
            pageSize?: number;
            isAsc?: boolean;
            sortBy?: string;
            /**
             * @description 兑换码对应的优惠券id
             * @default
             */
            couponId: string;
            /**
             * Format: int32
             * @description 兑换码状态，1：未兑换，2：已兑换
             * @default
             */
            status: number;
        };
        /**
         * @description 兑换码实体
         * @default null
         */
        ExchangeCodeVO: {
            /**
             * Format: int32
             * @description 兑换码id
             * @default
             */
            id: number;
            /**
             * @description 兑换码
             * @default
             */
            code: string;
        };
        /**
         * @description 分页结果
         * @default null
         */
        PageDTOExchangeCodeVO: {
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
            list: components["schemas"]["ExchangeCodeVO"][];
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
    writeOffCoupon: {
        parameters: {
            query: {
                /** @description 用户优惠券id集合 */
                couponIds: string;
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
                        data: null;
                    };
                };
            };
        };
    };
    refundCoupon: {
        parameters: {
            query: {
                /** @description 用户优惠券id集合 */
                couponIds: string;
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
                        data: null;
                    };
                };
            };
        };
    };
    queryCouponById: {
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
                        data: components["schemas"]["CouponDetailVO"];
                    };
                };
            };
        };
    };
    updateCouponById: {
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
                "application/json": components["schemas"]["CouponFormDTO"];
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
    deleteCouponById: {
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
    pauseIssueCouponById: {
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
    beginIssue: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CouponIssueFormDTO"];
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
    receiveCoupon: {
        parameters: {
            query?: never;
            header: {
                "Idempotency-Key": string;
            };
            path: {
                couponId: string;
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
    exchangeCoupon: {
        parameters: {
            query?: never;
            header: {
                "Idempotency-Key": string;
            };
            path: {
                code: string;
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
    queryDiscountDetailByOrder: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["OrderCouponDTO"];
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
                        data: components["schemas"]["CouponDiscountDTO"];
                    };
                };
            };
        };
    };
    findDiscountSolution: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["OrderCourseDTO"][];
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
                        data: components["schemas"]["CouponDiscountDTO"][];
                    };
                };
            };
        };
    };
    saveCoupon: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CouponFormDTO"];
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
    claim: {
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
    exchange: {
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
                "application/json": components["schemas"]["Request"];
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
    queryDiscountRules: {
        parameters: {
            query: {
                /** @description 用户优惠券id集合 */
                couponIds: string;
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
                        data: string[];
                    };
                };
            };
        };
    };
    queryMyCouponPage: {
        parameters: {
            query: {
                query: components["schemas"]["UserCouponQuery"];
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
                        data: components["schemas"]["PageDTOCouponVO"];
                    };
                };
            };
        };
    };
    queryCouponByPage: {
        parameters: {
            query: {
                query: components["schemas"]["CouponQuery"];
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
                        data: components["schemas"]["PageDTOCouponPageVO"];
                    };
                };
            };
        };
    };
    queryIssuingCoupons: {
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
                        data: components["schemas"]["CouponVO"][];
                    };
                };
            };
        };
    };
    queryExchangeCodeByPage: {
        parameters: {
            query: {
                query: components["schemas"]["CodeQuery"];
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
                        data: components["schemas"]["PageDTOExchangeCodeVO"];
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
