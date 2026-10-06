export interface paths {
    "/api/v2/admin/auth/roles/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 根据id查询角色 */
        get: operations["queryRoleById"];
        /** 修改角色信息 */
        put: operations["updateRole"];
        post?: never;
        /** 删除角色信息 */
        delete: operations["deleteRole"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/auth/privileges/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** 修改权限 */
        put: operations["updatePrivilege"];
        post?: never;
        /** 删除权限 */
        delete: operations["removePrivilegeById"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/auth/menus/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 根据id查询菜单 */
        get: operations["getMenuById"];
        /** 更新菜单 */
        put: operations["updateMenu"];
        post?: never;
        /** 根据id删除菜单 */
        delete: operations["deleteMenu"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/auth/roles": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询员工角色列表 */
        get: operations["listStaffRoles"];
        put?: never;
        /** 新增角色 */
        post: operations["saveRole"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/auth/privileges": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 分页查询所有权限 */
        get: operations["listAllPrivileges"];
        put?: never;
        /** 新增权限 */
        post: operations["savePrivilege"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/auth/privileges/role/{roleId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["roleIds"];
        put?: never;
        /** 绑定角色与API权限 */
        post: operations["bindRolePrivileges"];
        /** 解除角色的API权限 */
        delete: operations["deleteRolePrivileges"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/auth/menus": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询菜单，按照多级菜单组成树结构 */
        get: operations["listMenuTree"];
        put?: never;
        /** 新增菜单 */
        post: operations["saveMenu"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/auth/menus/role/{roleId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 绑定角色与菜单权限 */
        post: operations["bindRoleMenus"];
        /** 解除角色的菜单权限 */
        delete: operations["deleteRoleMenus"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/operation-failures/auth/{id}/replay": {
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
    "/api/v2/admin/events/auth/{id}/replay": {
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
    "/api/v2/admin/consumer-failures/auth/{id}/replay": {
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
    "/api/v2/auth/accounts/logout": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 退出登录 */
        post: operations["logout"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/auth/accounts/login": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 登录并获取token */
        post: operations["loginByPw"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/auth/accounts/admin/login": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 管理端登录并获取token */
        post: operations["adminLoginByPw"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/auth/roles/list": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询员工角色列表 */
        get: operations["listAllRoles"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/auth/privileges/roles/{roleId}/{menuId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询菜单下的权限列表，某个角色的权限 */
        get: operations["listPrivilegeByRoleId"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/auth/privileges/options/{menuId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询菜单下的所有权限，作为下拉选框菜单 */
        get: operations["listAllPrivilegesOptionsByMenuId"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/auth/menus/parent/{pid}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 根据父菜单id查询子菜单 */
        get: operations["listMenusByParent"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/admin/auth/menus/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询我的菜单，按照多级菜单组成树结构 */
        get: operations["listMenuTreeByUser"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/operations/auth/{id}": {
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
    "/api/v2/admin/operation-failures/auth": {
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
    "/api/v2/admin/events/auth/failures": {
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
    "/api/v2/admin/consumer-failures/auth": {
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
    "/api/v2/auth/accounts/refresh": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 刷新token */
        get: operations["refreshToken"];
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
         * @description 角色实体
         * @default null
         */
        RoleDTO: {
            /**
             * @description 主键
             * @default
             * @example 1
             */
            id: string;
            /**
             * @description 角色代号
             * @default
             * @example admin
             */
            code: string;
            /**
             * @description 角色名称
             * @default
             * @example 教师
             */
            name: string;
        };
        /**
         * @description API权限
         * @default null
         */
        PrivilegeDTO: {
            /**
             * @description 权限id
             * @default
             * @example 1
             */
            id: string;
            /**
             * @description 权限所属菜单id
             * @default
             * @example 1
             */
            menuId: string;
            /**
             * @description 权限说明
             * @default
             * @example 新增员工
             */
            intro: string;
            /**
             * @description API请求方式
             * @default
             * @example GET
             */
            method: string;
            /**
             * @description API请求路径
             * @default
             * @example /account/staff
             */
            uri: string;
            /**
             * @description 是否是内部权限
             * @default false
             */
            internal: boolean;
        };
        /**
         * @description 菜单表单实体
         * @default null
         */
        MenuDTO: {
            /**
             * @description 菜单id
             * @default
             * @example 1
             */
            id: string;
            /**
             * @description 父菜单id
             * @default
             * @example 0
             */
            parentId: string;
            /**
             * @description 菜单文本
             * @default
             * @example 系统管理
             */
            label: string;
            /**
             * @description 菜单路径
             * @default
             * @example /sys/index
             */
            path: string;
            /**
             * @description 菜单图标
             * @default
             * @example el-icon-sys
             */
            icon: string;
            /**
             * Format: int32
             * @description 菜单顺序
             * @default
             * @example 1
             */
            priority: number;
        };
        /**
         * @description 登录表单实体
         * @default null
         */
        LoginFormDTO: {
            /**
             * Format: int32
             * @description 登录方式：1-密码登录; 2-验证码登录
             * @default
             * @example 1
             */
            type: number;
            /**
             * @description 用户名
             * @default
             * @example jack
             */
            username: string;
            /**
             * @description 手机号
             * @default
             * @example 13800010001
             */
            cellPhone: string;
            /**
             * @description 密码
             * @default
             * @example 123
             */
            password: string;
            /**
             * @description 7天免密登录
             * @default false
             * @example true
             */
            rememberMe: boolean;
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
         * @description 分页结果
         * @default null
         */
        PageDTOPrivilegeDTO: {
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
            list: components["schemas"]["PrivilegeDTO"][];
        };
        /**
         * @description API权限选项实体
         * @default null
         */
        PrivilegeOptionVO: {
            /**
             * @description 权限id
             * @default
             * @example 1
             */
            id: string;
            /**
             * @description 权限说明
             * @default
             * @example 新增员工
             */
            intro: string;
            /**
             * @description 是否选中
             * @default false
             * @example true
             */
            checked: boolean;
        };
        /**
         * @description 菜单选项实体
         * @default null
         */
        MenuOptionVO: {
            /**
             * @description 菜单id
             * @default
             * @example 1
             */
            id: string;
            /**
             * @description 父菜单id
             * @default
             * @example 0
             */
            parentId: string;
            /**
             * @description 菜单文本
             * @default
             * @example 系统管理
             */
            label: string;
            /**
             * @description 菜单图标
             * @default
             * @example el-icon-sys
             */
            icon: string;
            /**
             * @description 是否有子菜单
             * @default false
             * @example false
             */
            hasChildren: boolean;
            /**
             * Format: int32
             * @description 菜单顺序
             * @default
             * @example 1
             */
            priority: number;
            /**
             * @description 子菜单集合
             * @default
             */
            subMenus: components["schemas"]["MenuOptionVO"][];
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
    queryRoleById: {
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
                        data: components["schemas"]["RoleDTO"];
                    };
                };
            };
        };
    };
    updateRole: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /**
                 * @description 角色id
                 * @example 1
                 */
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RoleDTO"];
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
    deleteRole: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /**
                 * @description 角色id
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
    updatePrivilege: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /**
                 * @description 要修改的权限id
                 * @example 1
                 */
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PrivilegeDTO"];
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
                        data: components["schemas"]["PrivilegeDTO"];
                    };
                };
            };
        };
    };
    removePrivilegeById: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /**
                 * @description 要删除的权限id
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
    getMenuById: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /**
                 * @description 菜单id
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
                        data: components["schemas"]["MenuOptionVO"];
                    };
                };
            };
        };
    };
    updateMenu: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /**
                 * @description 菜单id
                 * @example 1
                 */
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["MenuDTO"];
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
    deleteMenu: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /**
                 * @description 菜单id
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
    listStaffRoles: {
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
                        data: components["schemas"]["RoleDTO"][];
                    };
                };
            };
        };
    };
    saveRole: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RoleDTO"];
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
                        data: components["schemas"]["RoleDTO"];
                    };
                };
            };
        };
    };
    listAllPrivileges: {
        parameters: {
            query: {
                pageQuery: components["schemas"]["PageQuery"];
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
                        data: components["schemas"]["PageDTOPrivilegeDTO"];
                    };
                };
            };
        };
    };
    savePrivilege: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PrivilegeDTO"];
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
                        data: components["schemas"]["PrivilegeDTO"];
                    };
                };
            };
        };
    };
    roleIds: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                roleId: string;
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
                        data: string[];
                    };
                };
            };
        };
    };
    bindRolePrivileges: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /**
                 * @description 角色id
                 * @example 1
                 */
                roleId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": string;
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
    deleteRolePrivileges: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /**
                 * @description 角色id
                 * @example 1
                 */
                roleId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": string;
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
    listMenuTree: {
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
                        data: components["schemas"]["MenuOptionVO"][];
                    };
                };
            };
        };
    };
    saveMenu: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["MenuDTO"];
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
    bindRoleMenus: {
        parameters: {
            query: {
                /** @description 菜单id集合 */
                menuIds: string;
            };
            header?: never;
            path: {
                /**
                 * @description 角色id
                 * @example 1
                 */
                roleId: string;
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
    deleteRoleMenus: {
        parameters: {
            query: {
                /** @description 菜单id集合 */
                menuIds: string;
            };
            header?: never;
            path: {
                /**
                 * @description 角色id
                 * @example 1
                 */
                roleId: string;
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
    logout: {
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
                        data: null;
                    };
                };
            };
        };
    };
    loginByPw: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["LoginFormDTO"];
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
    adminLoginByPw: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["LoginFormDTO"];
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
    listAllRoles: {
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
                        data: components["schemas"]["RoleDTO"][];
                    };
                };
            };
        };
    };
    listPrivilegeByRoleId: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /**
                 * @description 角色id
                 * @example 1
                 */
                roleId: string;
                /**
                 * @description 菜单id
                 * @example 1
                 */
                menuId: string;
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
                        data: components["schemas"]["PrivilegeOptionVO"][];
                    };
                };
            };
        };
    };
    listAllPrivilegesOptionsByMenuId: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /**
                 * @description 菜单id
                 * @example 1
                 */
                menuId: string;
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
                        data: components["schemas"]["PrivilegeOptionVO"][];
                    };
                };
            };
        };
    };
    listMenusByParent: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /**
                 * @description 父菜单id
                 * @example 0
                 */
                pid: string;
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
                        data: components["schemas"]["MenuOptionVO"][];
                    };
                };
            };
        };
    };
    listMenuTreeByUser: {
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
                        data: components["schemas"]["MenuOptionVO"][];
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
    refreshToken: {
        parameters: {
            query?: {
                audience?: string;
            };
            header?: never;
            path?: never;
            cookie?: {
                refresh?: string;
                "admin-refresh"?: string;
            };
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
}
