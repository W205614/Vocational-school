package com.tianji.common.utils;

public class UserContext {
    private static final ThreadLocal<Integer> CALL_DEPTH = new ThreadLocal<>();
    public static int getCallDepth(){Integer depth=CALL_DEPTH.get();return depth==null?0:depth;}
    public static void setCallDepth(int depth){if(depth<0 || depth>4)throw new IllegalArgumentException("Invalid internal call depth");CALL_DEPTH.set(depth);}
    private static final ThreadLocal<String> SESSION = new ThreadLocal<>();
    public static void setSession(String id){SESSION.set(id);}
    public static String getSession(){return SESSION.get();}
    private static final ThreadLocal<Long> TL = new ThreadLocal<>();
    private static final ThreadLocal<Long> ROLE = new ThreadLocal<>();
    public static void setRole(Long role) { ROLE.set(role); }
    public static Long getRole() { return ROLE.get(); }
    public static long requireUser() {
        Long user=TL.get();
        if(user==null) throw new com.tianji.common.exceptions.UnauthorizedException("请先登录");
        return user;
    }
    public static void requireAdmin() {
        requireUser();
        if(!Long.valueOf(1).equals(ROLE.get())) throw new com.tianji.common.exceptions.ForbiddenException("需要管理员权限");
    }

    /**
     * 保存用户信息
     * @param userId 用户id
     */
    public static void setUser(Long userId){
        TL.set(userId);
    }

    /**
     * 获取用户
     * @return 用户id
     */
    public static Long getUser(){
        return TL.get();
    }

    /**
     * 移除用户信息
     */
    public static void removeUser(){
        TL.remove();
        ROLE.remove();
        SESSION.remove();
        CALL_DEPTH.remove();
    }
}
