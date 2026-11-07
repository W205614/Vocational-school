export interface paths {
    "/api/v2/admin/provider-payments/anomalies": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询渠道异常，最多100条 */
        get: {
            parameters: {
                query?: {
                    status?: "OPEN" | "RESOLVED";
                };
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description 统一响应data中返回原始snake_case异常行数组 */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description 需要管理员权限 */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/provider-payments/reconciliations": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询持久化渠道核对任务，最多100条 */
        get: {
            parameters: {
                query?: {
                    status?: "PENDING" | "SENDING" | "DONE" | "DEAD";
                };
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description 统一响应data中返回原始snake_case任务行数组 */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description 需要管理员权限 */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/provider-payments/anomalies/{number}/{reason}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** 以version进行CAS记录异常处理依据 */
        put: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    number: components["parameters"]["PaymentNumber"];
                    reason: "WINDOW_EXHAUSTED" | "CHANNEL_UNAVAILABLE" | "ORDER_NUMBER_MISMATCH" | "AMOUNT_MISMATCH" | "INVALID_SUCCESS_TIME" | "UNKNOWN_STATUS";
                };
                cookie?: never;
            };
            requestBody: {
                content: {
                    "application/json": components["schemas"]["Resolution"];
                };
            };
            responses: {
                /** @description 处理依据已记录 */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description 需要管理员权限 */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description 异常已更新，必须重新查询核对 */
                409: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/provider-payments/reconciliations/{number}/replay": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 以version进行CAS重试DEAD任务并记录依据 */
        post: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    number: components["parameters"]["PaymentNumber"];
                };
                cookie?: never;
            };
            requestBody: {
                content: {
                    "application/json": components["schemas"]["Resolution"];
                };
            };
            responses: {
                /** @description 任务重新进入PENDING，重核窗口为7天 */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description 需要管理员权限 */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description 任务已更新或状态不是DEAD */
                409: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
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
        Resolution: {
            /** Format: int64 */
            version: number;
            /** @description 人工核对渠道账单的依据，不接受空白 */
            resolution: string;
        };
    };
    responses: never;
    parameters: {
        PaymentNumber: string;
    };
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export type operations = Record<string, never>;
