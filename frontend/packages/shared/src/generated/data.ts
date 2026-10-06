export interface paths {
    "/api/v2/services/data/data/top10/set": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** 设置top10数据 */
        put: operations["setTop10Data"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/data/data/today/set": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** 设置线上数据 */
        put: operations["set"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/data/data/board/set": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** 看板数据设置 */
        put: operations["setBoardData"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/data/data/top10": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** top10数据获取 */
        get: operations["getTop10Data"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/data/data/today": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 获取今日数据 */
        get: operations["get"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/services/data/data/board": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 看板数据获取 */
        get: operations["boardData"];
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
        Top10DataSetDTO: {
            /** Format: int32 */
            version?: number;
            data?: components["schemas"]["Top10DataSetUnitDTO"][];
        };
        Top10DataSetUnitDTO: {
            category: string;
            name: string;
            /** Format: int32 */
            newStuNum: number;
            /** Format: double */
            orderAmount: number;
        };
        TodayDataDTO: {
            /** Format: int32 */
            version?: number;
            /** Format: double */
            visits?: number;
            /** Format: double */
            orderAmount?: number;
            /** Format: int32 */
            orderNum?: number;
            /** Format: int32 */
            stuNewNum?: number;
        };
        BoardDataSetDTO: {
            /** Format: int32 */
            version: number;
            /** Format: int32 */
            type: number;
            data: number[];
        };
        CourseInfo: {
            category?: string;
            name?: string;
            /** Format: int32 */
            newStuNum?: number;
            /** Format: double */
            orderAmount?: number;
        };
        Top10DataVO: {
            hot?: components["schemas"]["CourseInfo"][];
            hotSales?: components["schemas"]["CourseInfo"][];
        };
        TodayDataVO: {
            /**
             * Format: double
             * @description 访问量，万次单位
             * @default
             */
            visits: number;
            /**
             * Format: double
             * @description 今日订单金额,万元单位
             * @default
             */
            orderAmount: number;
            /**
             * Format: int32
             * @description 今日订单笔数
             * @default
             */
            orderNum: number;
            /**
             * Format: int32
             * @description 今日新增学员数
             * @default
             */
            stuNewNum: number;
        };
        AxisVO: {
            type?: string;
            /** Format: double */
            max?: number;
            /** Format: double */
            min?: number;
            /** Format: double */
            average?: number;
            data?: unknown[];
            /** Format: double */
            interval?: number;
        };
        EchartsVO: {
            series?: components["schemas"]["SerierVO"][];
            xaxis?: components["schemas"]["AxisVO"][];
            yaxis?: components["schemas"]["AxisVO"][];
        };
        SerierVO: {
            name?: string;
            type?: string;
            data?: unknown[];
            max?: string;
            min?: string;
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
    setTop10Data: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Top10DataSetDTO"];
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
    set: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["TodayDataDTO"];
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
    setBoardData: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["BoardDataSetDTO"];
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
    getTop10Data: {
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
                        data: components["schemas"]["Top10DataVO"];
                    };
                };
            };
        };
    };
    get: {
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
                        data: components["schemas"]["TodayDataVO"];
                    };
                };
            };
        };
    };
    boardData: {
        parameters: {
            query: {
                types: number[];
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
                        data: components["schemas"]["EchartsVO"];
                    };
                };
            };
        };
    };
}
