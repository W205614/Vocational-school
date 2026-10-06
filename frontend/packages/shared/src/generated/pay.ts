export interface paths {
    "/api/v2/simulator/payments/{business}/confirm": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["confirm"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/operation-failures/pay/{id}/replay": {
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
    "/api/v2/admin/events/pay/{id}/replay": {
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
    "/api/v2/admin/consumer-failures/pay/{id}/replay": {
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
    "/api/v2/operations/pay/{id}": {
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
    "/api/v2/admin/operation-failures/pay": {
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
    "/api/v2/admin/events/pay/failures": {
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
    "/api/v2/admin/consumer-failures/pay": {
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
         * @description 支付渠道信息
         * @default null
         */
        PayChannelDTO: {
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
            /**
             * Format: int32
             * @description 支付渠道状态，1：使用中，2：停用。新增时默认为1
             * @default
             */
            status: number;
        };
        /**
         * @description 退款请求参数
         * @default null
         */
        RefundApplyDTO: {
            /**
             * @description 支付时传入的业务订单id
             * @default
             */
            bizOrderNo: string;
            /**
             * @description 本次要退款的业务订单id，因为有拆单，这里是子订单id
             * @default
             */
            bizRefundOrderNo: string;
            /**
             * Format: int32
             * @description 子订单的退款金额,单位为分
             * @default
             */
            refundAmount: number;
        };
        /**
         * @description 支付结果
         * @default null
         */
        RefundResultDTO: {
            /**
             * Format: int32
             * @description 退款状态，1：退款中，2：退款失败，3：退款成功
             * @default
             */
            status: number;
            /**
             * @description 支付失败原因
             * @default
             */
            msg: string;
            /**
             * @description 业务端支付订单号
             * @default
             */
            bizPayOrderId: string;
            /**
             * @description 业务端退款订单号
             * @default
             */
            bizRefundOrderId: string;
            /**
             * @description 支付流水交易单号
             * @default
             */
            payOrderNo: string;
            /**
             * @description 退款交易单号
             * @default
             */
            refundOrderNo: string;
            /**
             * @description 支付渠道
             * @default
             */
            payChannel: string;
            /**
             * @description 退款渠道
             * @default
             */
            refundChannel: string;
        };
        /**
         * @description 支付申请参数
         * @default null
         */
        PayApplyDTO: {
            /**
             * @description 业务订单号
             * @default
             */
            bizOrderNo: string;
            /**
             * @description 下单用户id
             * @default
             */
            bizUserId: string;
            /**
             * Format: int32
             * @description 支付金额，以分为单位
             * @default
             */
            amount: number;
            /**
             * @description 支付渠道编码，例如：aliPay
             * @default
             */
            payChannelCode: string;
            /**
             * Format: int32
             * @description 支付方式: 1-h5；2-小程序；3-公众号；4-扫码
             * @default
             */
            payType: number;
            /**
             * @description 订单中的商品信息
             * @default
             */
            orderInfo: string;
        };
        /**
         * @description 支付结果
         * @default null
         */
        PayResultDTO: {
            /**
             * Format: int32
             * @description 支付结果，1：支付中，2：支付失败，3：支付成功
             * @default
             */
            status: number;
            /**
             * @description 支付失败原因
             * @default
             */
            msg: string;
            /**
             * @description 业务订单号
             * @default
             */
            bizOrderId: string;
            /**
             * @description 业务订单号
             * @default
             */
            payOrderNo: string;
            /**
             * @description 支付渠道
             * @default
             */
            payChannel: string;
            /**
             * Format: date-time
             * @description 支付成功时间
             * @default
             */
            successTime: string;
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
    confirm: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                business: string;
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
