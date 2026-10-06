export interface paths {
    "/api/v2/services/trade/refund-apply/cancel": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** 取消退款申请 */
        put: operations["cancelRefundApply"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/trade/refund-apply/approval": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** 审批退款申请 */
        put: operations["approveRefundApply"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/trade/orders/{id}/cancel": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** 取消订单接口 */
        put: operations["cancelOrder"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/orders/{id}/cancel": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["cancel"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/payment-conflicts/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["resolve"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/trade/refund-apply": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 退款申请 */
        post: operations["applyRefund"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/trade/pay/order": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 支付申请,返回支付二维码url */
        post: operations["applyPayOrder"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/trade/orders/placeOrder": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 下单接口 */
        post: operations["placeOrder"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/trade/orders/freeCourse/{courseId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 免费课立刻报名接口 */
        post: operations["enrolledFreeCourse"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/trade/carts": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 获取购物车中的课程 */
        get: operations["getMyCarts"];
        put?: never;
        /** 添加课程到购物车 */
        post: operations["addCourse2Cart"];
        /** 批量删除购物车条目 */
        delete: operations["deleteCartById"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/orders": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list"];
        put?: never;
        post: operations["create"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/orders/free-courses/{courseId}": {
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
    "/api/v2/admin/operation-failures/trade/{id}/replay": {
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
    "/api/v2/admin/events/trade/{id}/replay": {
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
    "/api/v2/admin/consumer-failures/trade/{id}/replay": {
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
    "/api/v2/admin/compensations/{kind}/{id}/replay": {
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
    "/api/v2/services/trade/refund-apply/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 根据id查询退款详情 */
        get: operations["queryRefundDetailById"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/trade/refund-apply/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 分页查询退款申请 */
        get: operations["queryRefundApplyByPage"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/trade/refund-apply/next": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询下一个待审批的退款申请 */
        get: operations["nextRefundApplyToApprove"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/trade/refund-apply/detail/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 根据子订单id查询退款详情 */
        get: operations["queryRefundDetailByDetailId"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/trade/pay/channels": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 获取支付渠道列表接口 */
        get: operations["queryPayChannels"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/trade/orders/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 根据id查询订单详细信息 */
        get: operations["queryOrderById"];
        put?: never;
        post?: never;
        /** 删除订单接口 */
        delete: operations["deleteOrder"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/trade/orders/{id}/status": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询订单支付状态 */
        get: operations["queryOrderStatus"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/trade/orders/prePlaceOrder": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 预下单接口，生成订单id，确认订单可用优惠券信息 */
        get: operations["prePlaceOrder"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/trade/orders/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 分页查询我的订单 */
        get: operations["queryMyOrderPage"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/trade/order-details/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 根据订单明细id获取详细信息 */
        get: operations["queryOrdersDetailProgress"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/trade/order-details/purchaseInfo": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["getPurchaseInfoOfCourse"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/trade/order-details/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 分页查询订单明细 */
        get: operations["queryDetailForPage"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/trade/order-details/enrollNum": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 统计课程报名人数 */
        get: operations["countEnrollNumOfCourse"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/trade/order-details/enrollCourse": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 统计学生报名课程数量 */
        get: operations["countEnrollCourseOfStudent"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/trade/order-details/course/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 校验课程是否购买，是否过期 */
        get: operations["checkCourseOrderInfo"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/orders/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["detail"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/orders/{id}/status": {
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
    "/api/v2/orders/confirmation": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["confirm"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/operations/trade/{id}": {
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
    "/api/v2/admin/payment-conflicts": {
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
    "/api/v2/admin/operation-failures/trade": {
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
    "/api/v2/admin/events/trade/failures": {
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
    "/api/v2/admin/dashboard": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["dashboard"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/consumer-failures/trade": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list_3"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/compensations/{kind}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list_4"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/trade/carts/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /** 删除指定的购物车条目 */
        delete: operations["deleteCartById_1"];
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
         * @description 退款取消
         * @default null
         */
        RefundCancelDTO: {
            /**
             * @description 退款申请id，订单明细id和退款申请id二选一
             * @default
             */
            id: string;
            /**
             * @description 订单明细id，订单明细id和退款申请id二选一
             * @default
             */
            orderDetailId: string;
        };
        View: {
            operationId?: string;
            status?: string;
            result?: unknown;
            errorCode?: string;
            errorMessage?: string;
        };
        /**
         * @description 退款审批模型
         * @default null
         */
        ApproveFormDTO: {
            /**
             * @description 退款id
             * @default
             */
            id: string;
            /**
             * Format: int32
             * @description 审批类型，1：同意，2：拒绝
             * @default
             */
            approveType: number;
            /**
             * @description 审批意见
             * @default
             */
            approveOpinion: string;
            /**
             * @description 备注
             * @default
             */
            remark: string;
        };
        Resolution: {
            version?: string;
            resolution?: string;
        };
        /**
         * @description 退款申请数据
         * @default null
         */
        RefundFormDTO: {
            /**
             * @description 订单明细id
             * @default
             */
            orderDetailId: string;
            /**
             * @description 退款原因
             * @default
             */
            refundReason: string;
            /**
             * @description 问题说明
             * @default
             */
            questionDesc: string;
        };
        /**
         * @description 支付申请信息
         * @default null
         */
        PayApplyFormDTO: {
            /**
             * @description 订单id
             * @default
             */
            orderId: string;
            /**
             * @description 支付渠道码，wxPay,aliPay
             * @default
             */
            payChannelCode: string;
        };
        /**
         * @description 下单模型
         * @default null
         */
        PlaceOrderDTO: {
            /**
             * @description 要购买的课程id列表，可以只买单个课程
             * @default
             */
            courseIds: string[];
            /**
             * @description 该订单使用的优惠券id列表，可以为空
             * @default
             */
            couponIds: string[];
            /**
             * @description 订单id
             * @default
             */
            orderId: string;
        };
        /**
         * @description 课程加入购物车
         * @default null
         */
        CartsAddDTO: {
            /**
             * @description 要加入购物车的课程id
             * @default
             */
            courseId: string;
        };
        /**
         * @description 退款申请详细信息
         * @default null
         */
        RefundApplyVO: {
            /**
             * @description 退款id
             * @default
             */
            id: string;
            /**
             * @description 子订单id
             * @default
             */
            orderDetailId: string;
            /**
             * @description 订单id
             * @default
             */
            orderId: string;
            /**
             * @description 支付流水单号
             * @default
             */
            payOrderNo: string;
            /**
             * @description 支付方式
             * @default
             */
            payChannel: string;
            /**
             * @description 退款方式
             * @default
             */
            refundChannel: string;
            /**
             * @description 退款流水单号
             * @default
             */
            refundOrderNo: string;
            /**
             * @description 申请退款原因
             * @default
             */
            refundReason: string;
            /**
             * @description 申请退款说明
             * @default
             */
            questionDesc: string;
            /**
             * @description 学员昵称
             * @default
             */
            studentName: string;
            /**
             * @description 手机号
             * @default
             */
            mobile: string;
            /**
             * @description 退款申请人，格式：角色-名字
             * @default
             */
            refundProposerName: string;
            /**
             * Format: date-time
             * @description 订单时间
             * @default
             */
            orderTime: string;
            /**
             * Format: date-time
             * @description 支付时间
             * @default
             */
            paySuccessTime: string;
            /**
             * Format: date-time
             * @description 退款申请时间
             * @default
             */
            createTime: string;
            /**
             * Format: date-time
             * @description 退款审批时间
             * @default
             */
            approveTime: string;
            /**
             * @description 状态描述
             * @default
             */
            message: string;
            /**
             * @description 审批意见
             * @default
             */
            approveOpinion: string;
            /**
             * @description 审批意见
             * @default
             */
            remark: string;
            /**
             * @description 课程名称
             * @default
             */
            name: string;
            /**
             * Format: int32
             * @description 课程价格
             * @default
             */
            price: number;
            /**
             * Format: int32
             * @description 实付金额
             * @default
             */
            realPayAmount: number;
            /**
             * @description 优惠券规则
             * @default
             */
            couponDesc: string;
            /**
             * Format: int32
             * @description 优惠总金额
             * @default
             */
            discountAmount: number;
            /**
             * Format: int32
             * @description 退款状态：1：待审批，2：取消退款，3：同意退款，4：拒绝退款，5：退款成功，6：退款失败
             * @default
             */
            status: number;
            /**
             * @description 退款失败原因
             * @default
             */
            failedReason: string;
        };
        /**
         * @description 退款申请分页参数
         * @default null
         */
        RefundApplyPageQuery: {
            /** Format: int32 */
            pageNo?: number;
            /** Format: int32 */
            pageSize?: number;
            isAsc?: boolean;
            sortBy?: string;
            /**
             * @description 退款id
             * @default
             */
            id: string;
            /**
             * Format: int32
             * @description 退款状态，1：待审批，2：取消退款，3：同意退款，4：拒绝退款，5：退款成功，6：退款失败
             * @default
             */
            refundStatus: number;
            /**
             * @description 订单明细id
             * @default
             */
            orderDetailId: string;
            /**
             * @description 订单id
             * @default
             */
            orderId: string;
            /**
             * @description 学员手机号
             * @default
             */
            mobile: string;
            /**
             * Format: date-time
             * @description 申请开始时间
             * @default
             */
            applyStartTime: string;
            /**
             * Format: date-time
             * @description 申请结束时间
             * @default
             */
            applyEndTime: string;
        };
        /**
         * @description 分页结果
         * @default null
         */
        PageDTORefundApplyPageVO: {
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
            list: components["schemas"]["RefundApplyPageVO"][];
        };
        /**
         * @description 退款信息
         * @default null
         */
        RefundApplyPageVO: {
            /**
             * @description 退款id
             * @default
             */
            id: string;
            /**
             * @description 订单明细id
             * @default
             */
            orderDetailId: string;
            /**
             * @description 订单id
             * @default
             */
            orderId: string;
            /**
             * Format: int32
             * @description 退款金额
             * @default
             */
            refundAmount: number;
            /**
             * @description 申请人
             * @default
             */
            proposerName: string;
            /**
             * @description 申请人手机号
             * @default
             */
            proposerMobile: string;
            /**
             * Format: int32
             * @description 退款申请状态
             * @default
             */
            status: number;
            /**
             * @description 退款申请状态描述
             * @default
             */
            refundStatusDesc: string;
            /**
             * Format: date-time
             * @description 退款申请时间
             * @default
             */
            createTime: string;
            /**
             * @description 审批人
             * @default
             */
            approverName: string;
            /**
             * @description 审批时间
             * @default
             */
            approveTime: string;
            /**
             * Format: date-time
             * @description 退款成功时间
             * @default
             */
            refundSuccessTime: string;
        };
        /**
         * @description 支付渠道信息
         * @default null
         */
        PayChannelVO: {
            /**
             * @description 支付渠道id
             * @default
             */
            id: string;
            /**
             * @description 支付渠道名称
             * @default
             */
            name: string;
            /**
             * @description 支付渠道编码，唯一标示
             * @default
             */
            channelCode: string;
            /**
             * Format: int32
             * @description 渠道优先级，数字越小优先级越高
             * @default
             */
            channelPriority: number;
            /**
             * @description 渠道图标
             * @default
             */
            channelIcon: string;
        };
        /**
         * @description 订单条目中的课程信息
         * @default null
         */
        OrderDetailVO: {
            /**
             * @description 订单条目id
             * @default
             */
            id: string;
            /**
             * @description 总订单id
             * @default
             */
            orderId: string;
            /**
             * @description 课程id
             * @default
             */
            courseId: string;
            /**
             * @description 课程名称
             * @default
             */
            name: string;
            /**
             * @description 封面
             * @default
             */
            coverUrl: string;
            /**
             * Format: int32
             * @description 课程价格
             * @default
             */
            price: number;
            /**
             * Format: int32
             * @description 实付金额
             * @default
             */
            realPayAmount: number;
            /**
             * Format: int32
             * @description 退款状态
             * @default
             */
            refundStatus: number;
            /**
             * @description 优惠券规则
             * @default
             */
            couponDesc: string;
            /**
             * @description 是否可以退款
             * @default false
             */
            canRefund: boolean;
        };
        OrderProgressNodeVO: {
            /**
             * @description 订单进度节点名称
             * @default
             */
            name: string;
            /**
             * Format: date-time
             * @description 订单进度节点名称对应的时间
             * @default
             */
            time: string;
        };
        /**
         * @description 订单详细信息
         * @default null
         */
        OrderVO: {
            /**
             * @description 订单id
             * @default
             */
            id: string;
            /**
             * Format: date-time
             * @description 订单创建时间
             * @default
             */
            createTime: string;
            /**
             * Format: int32
             * @description 订单实付金额
             * @default
             */
            realAmount: number;
            /**
             * Format: int32
             * @description 订单明细金额
             * @default
             */
            totalAmount: number;
            /**
             * @description 优惠券规则,可以有多个优惠券规则
             * @default
             */
            couponDesc: string;
            /**
             * Format: double
             * @description 优惠总金额
             * @default
             */
            discountAmount: number;
            /**
             * Format: int32
             * @description 订单状态，1：待支付，2：已支付，3：已关闭，4：已完成，5：已报名，6：已申请退款
             * @default
             */
            status: number;
            /**
             * @description 订单状态描述
             * @default
             */
            statusDesc: string;
            /**
             * @description 订单状态描述
             * @default
             */
            message: string;
            /**
             * @description 订单进度明细
             * @default
             */
            progressNodes: components["schemas"]["OrderProgressNodeVO"][];
            /**
             * @description 订单中课程明细
             * @default
             */
            details: components["schemas"]["OrderDetailVO"][];
        };
        /**
         * @description 下单响应信息
         * @default null
         */
        PlaceOrderResultVO: {
            /**
             * @description 订单号
             * @default
             */
            orderId: string;
            /**
             * Format: int32
             * @description 支付金额
             * @default
             */
            payAmount: number;
            /**
             * Format: date-time
             * @description 待支付的订单，超时时间
             * @default
             */
            payOutTime: string;
            /**
             * Format: int32
             * @description 订单状态，1：待支付，2：已支付，3：已关闭，4：已完成，5：已报名, 6:申请退款
             * @default
             */
            status: number;
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
        /**
         * @description 订单确认页信息
         * @default null
         */
        OrderConfirmVO: {
            /**
             * @description 订单id
             * @default
             */
            orderId: string;
            /**
             * Format: int32
             * @description 订单总金额
             * @default
             */
            totalAmount: number;
            /**
             * @description 优惠折扣方案
             * @default
             */
            discounts: components["schemas"]["CouponDiscountDTO"][];
            /**
             * @description 订单中包含的课程
             * @default
             */
            courses: components["schemas"]["OrderCourseVO"][];
        };
        /**
         * @description 订单中课程信息
         * @default null
         */
        OrderCourseVO: {
            /**
             * @description 课程id
             * @default
             */
            id: string;
            /**
             * @description 课程名称
             * @default
             */
            name: string;
            /**
             * @description 课程封面url
             * @default
             */
            coverUrl: string;
            /**
             * Format: int32
             * @description 课程价格，单位元
             * @default
             */
            price: number;
        };
        /**
         * @description 订单分页查询条件
         * @default null
         */
        OrderPageQuery: {
            /** Format: int32 */
            pageNo?: number;
            /** Format: int32 */
            pageSize?: number;
            isAsc?: boolean;
            sortBy?: string;
            /**
             * Format: int32
             * @description 订单状态
             * @default
             */
            status: number;
        };
        /**
         * @description 订单分页信息
         * @default null
         */
        OrderPageVO: {
            /**
             * @description 订单id
             * @default
             */
            id: string;
            /**
             * Format: date-time
             * @description 订单创建时间
             * @default
             */
            createTime: string;
            /**
             * Format: int32
             * @description 订单实付金额
             * @default
             */
            realAmount: number;
            /**
             * Format: int32
             * @description 订单明细金额
             * @default
             */
            totalAmount: number;
            /**
             * Format: int32
             * @description 订单状态1：待支付，2：已支付，3：已关闭，4：已完成，5：已报名
             * @default
             */
            status: number;
            /**
             * @description 订单状态描述
             * @default
             */
            statusDesc: string;
            /**
             * @description 订单中课程明细
             * @default
             */
            details: components["schemas"]["OrderDetailVO"][];
        };
        /**
         * @description 分页结果
         * @default null
         */
        PageDTOOrderPageVO: {
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
            list: components["schemas"]["OrderPageVO"][];
        };
        /**
         * @description 管理端订单条目详细信息
         * @default null
         */
        OrderDetailAdminVO: {
            /**
             * @description 订单条目id
             * @default
             */
            id: string;
            /**
             * @description 订单id
             * @default
             */
            orderId: string;
            /**
             * @description 退款id
             * @default
             */
            refundApplyId: string;
            /**
             * @description 支付流水单号
             * @default
             */
            payOrderNo: string;
            /**
             * @description 退款流水单号
             * @default
             */
            refundOrderNo: string;
            /**
             * @description 学员昵称
             * @default
             */
            studentName: string;
            /**
             * @description 手机号
             * @default
             */
            mobile: string;
            /**
             * @description 退款申请人，格式：角色-名字
             * @default
             */
            refundProposerName: string;
            /**
             * @description 支付方式
             * @default
             */
            payChannel: string;
            /**
             * @description 退款方式
             * @default
             */
            refundChannel: string;
            /**
             * @description 退款失败原因
             * @default
             */
            failedReason: string;
            /**
             * @description 申请退款原因
             * @default
             */
            refundReason: string;
            /**
             * @description 申请退款描述
             * @default
             */
            refundMessage: string;
            /**
             * @description 审批意见
             * @default
             */
            remark: string;
            /**
             * Format: int32
             * @description 订单状态，1：待支付，2：已支付，3：已关闭，4：已完成，5：已报名
             * @default
             */
            status: number;
            /**
             * Format: int32
             * @description 退款状态，1：待审批，2：取消退款，3：同意退款，4：拒绝退款，5：退款成功，6：退款失败
             * @default
             */
            refundStatus: number;
            /**
             * @description 状态描述
             * @default
             */
            message: string;
            /**
             * @description 订单节点进度节点列表
             * @default
             */
            nodes: components["schemas"]["OrderProgressNodeVO"][];
            /**
             * @description 课程名称
             * @default
             */
            name: string;
            /**
             * Format: int32
             * @description 课程价格
             * @default
             */
            price: number;
            /**
             * Format: int32
             * @description 实付金额
             * @default
             */
            realPayAmount: number;
            /**
             * @description 优惠券规则
             * @default
             */
            couponDesc: string;
            /**
             * Format: int32
             * @description 优惠总金额
             * @default
             */
            discountAmount: number;
            /**
             * Format: date-time
             * @description 学习有效期
             * @default
             */
            studyValidTime: string;
            /**
             * @description 是否可以退款
             * @default false
             */
            canRefund: boolean;
        };
        /**
         * @description 课程购买信息
         * @default null
         */
        CoursePurchaseInfoDTO: {
            /**
             * Format: int32
             * @description 报名人数
             * @default
             */
            enrollNum: number;
            /**
             * Format: int32
             * @description 退款人数
             * @default
             */
            refundNum: number;
            /**
             * Format: int32
             * @description 实付总金额
             * @default
             */
            realPayAmount: number;
        };
        /**
         * @description 订单明细查询条件
         * @default null
         */
        OrderDetailPageQuery: {
            /** Format: int32 */
            pageNo?: number;
            /** Format: int32 */
            pageSize?: number;
            isAsc?: boolean;
            sortBy?: string;
            /**
             * @description 订单明细id
             * @default
             */
            id: string;
            /**
             * Format: int32
             * @description 订单状态：1：待支付，2：已支付，3：已关闭，4：已完成，5：已报名
             * @default
             */
            status: number;
            /**
             * Format: int32
             * @description 退款状态：1：待审批，2：取消退款，3：同意退款，4：拒绝退款，5：退款成功，6：退款失败
             * @default
             */
            refundStatus: number;
            /**
             * @description 支付方式:wxPay:微信，aliPay：支付宝
             * @default
             */
            payChannel: string;
            /**
             * @description 手机号
             * @default
             */
            mobile: string;
            /**
             * Format: date-time
             * @description 下单开始时间
             * @default
             */
            orderStartTime: string;
            /**
             * Format: date-time
             * @description 下单结束时间
             * @default
             */
            orderEndTime: string;
        };
        /**
         * @description 订单明细分页结果
         * @default null
         */
        OrderDetailPageVO: {
            /**
             * @description 订单明细id
             * @default
             */
            id: string;
            /**
             * @description 订单id
             * @default
             */
            orderId: string;
            /**
             * @description 学员姓名
             * @default
             */
            name: string;
            /**
             * @description 手机号
             * @default
             */
            mobile: string;
            /**
             * Format: int32
             * @description 订单金额，也就是课程原价
             * @default
             */
            price: number;
            /**
             * Format: int32
             * @description 实付金额
             * @default
             */
            realPayAmount: number;
            /**
             * Format: int32
             * @description 订单状态1：待支付，2：已支付，3：已关闭，4：已完成，5：已报名
             * @default
             */
            status: number;
            /**
             * @description 订单状态描述
             * @default
             */
            statusDesc: string;
            /**
             * Format: int32
             * @description 退款状态1：待支付，2：已支付，3：已关闭，4：已完成，5：已报名，0：表示没有退款状态
             * @default
             */
            refundStatus: number;
            /**
             * @description 退款状态描述
             * @default
             */
            refundStatusDesc: string;
            /**
             * Format: date-time
             * @description 订单时间
             * @default
             */
            createTime: string;
            /**
             * @description 支付方式
             * @default
             */
            payChannel: string;
        };
        /**
         * @description 分页结果
         * @default null
         */
        PageDTOOrderDetailPageVO: {
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
            list: components["schemas"]["OrderDetailPageVO"][];
        };
        /**
         * @description 购物车条目信息
         * @default null
         */
        CartVO: {
            /**
             * @description 购物车中条目id
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
             * @description 课程封面url
             * @default
             */
            coverUrl: string;
            /**
             * Format: int32
             * @description 加入购物车时的课程价格，单位元
             * @default
             */
            price: number;
            /**
             * Format: int32
             * @description 现在的课程价格，单位元
             * @default
             */
            nowPrice: number;
            /**
             * @description 课程是否已经过期
             * @default false
             */
            expired: boolean;
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
    cancelRefundApply: {
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
                "application/json": components["schemas"]["RefundCancelDTO"];
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
    approveRefundApply: {
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
                "application/json": components["schemas"]["ApproveFormDTO"];
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
    cancelOrder: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 要取消订单的id */
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
    cancel: {
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
    resolve: {
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
                "application/json": components["schemas"]["Resolution"];
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
    applyRefund: {
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
                "application/json": components["schemas"]["RefundFormDTO"];
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
    applyPayOrder: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PayApplyFormDTO"];
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
    placeOrder: {
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
                "application/json": components["schemas"]["PlaceOrderDTO"];
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
    enrolledFreeCourse: {
        parameters: {
            query?: never;
            header: {
                "Idempotency-Key": string;
            };
            path: {
                /** @description 免费课程id */
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
                        data: Record<string, never>;
                    };
                };
            };
        };
    };
    getMyCarts: {
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
                        data: components["schemas"]["CartVO"][];
                    };
                };
            };
        };
    };
    addCourse2Cart: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CartsAddDTO"];
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
    deleteCartById: {
        parameters: {
            query: {
                /** @description 购物车条目id集合 */
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
                        data: null;
                    };
                };
            };
        };
    };
    list: {
        parameters: {
            query: {
                query: components["schemas"]["OrderPageQuery"];
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
                "application/json": components["schemas"]["PlaceOrderDTO"];
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
    enroll: {
        parameters: {
            query?: never;
            header: {
                "Idempotency-Key": string;
            };
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
    replay_3: {
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
    queryRefundDetailById: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 退款id */
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
                        data: components["schemas"]["RefundApplyVO"];
                    };
                };
            };
        };
    };
    queryRefundApplyByPage: {
        parameters: {
            query: {
                pageQuery: components["schemas"]["RefundApplyPageQuery"];
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
                        data: components["schemas"]["PageDTORefundApplyPageVO"];
                    };
                };
            };
        };
    };
    nextRefundApplyToApprove: {
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
                        data: components["schemas"]["RefundApplyVO"];
                    };
                };
            };
        };
    };
    queryRefundDetailByDetailId: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 子订单id */
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
                        data: components["schemas"]["RefundApplyVO"];
                    };
                };
            };
        };
    };
    queryPayChannels: {
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
                        data: components["schemas"]["PayChannelVO"][];
                    };
                };
            };
        };
    };
    queryOrderById: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 订单id */
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
                        data: components["schemas"]["OrderVO"];
                    };
                };
            };
        };
    };
    deleteOrder: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 要删除的订单id */
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
    queryOrderStatus: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 订单id */
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
                        data: components["schemas"]["PlaceOrderResultVO"];
                    };
                };
            };
        };
    };
    prePlaceOrder: {
        parameters: {
            query: {
                courseIds: string[];
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
                        data: components["schemas"]["OrderConfirmVO"];
                    };
                };
            };
        };
    };
    queryMyOrderPage: {
        parameters: {
            query: {
                pageQuery: components["schemas"]["OrderPageQuery"];
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
                        data: components["schemas"]["PageDTOOrderPageVO"];
                    };
                };
            };
        };
    };
    queryOrdersDetailProgress: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 订单明细id */
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
                        data: components["schemas"]["OrderDetailAdminVO"];
                    };
                };
            };
        };
    };
    getPurchaseInfoOfCourse: {
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
                        data: components["schemas"]["CoursePurchaseInfoDTO"];
                    };
                };
            };
        };
    };
    queryDetailForPage: {
        parameters: {
            query: {
                pageQuery: components["schemas"]["OrderDetailPageQuery"];
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
                        data: components["schemas"]["PageDTOOrderDetailPageVO"];
                    };
                };
            };
        };
    };
    countEnrollNumOfCourse: {
        parameters: {
            query: {
                courseIdList: string[];
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
    countEnrollCourseOfStudent: {
        parameters: {
            query: {
                studentIds: string[];
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
    checkCourseOrderInfo: {
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
                        data: boolean;
                    };
                };
            };
        };
    };
    detail: {
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
                        data: Record<string, never>;
                    };
                };
            };
        };
    };
    status: {
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
                        data: Record<string, never>;
                    };
                };
            };
        };
    };
    confirm: {
        parameters: {
            query: {
                courseIds: string[];
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
    list_1: {
        parameters: {
            query?: {
                status?: string;
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
    dashboard: {
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
    list_3: {
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
    list_4: {
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
                        data: {
                            [key: string]: unknown;
                        }[];
                    };
                };
            };
        };
    };
    deleteCartById_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 购物车条目id */
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
}
