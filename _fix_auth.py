content = open(r'src\main\java\com\zhixue\controller\AuthController.java', 'r', encoding='utf-8').read()

old = '    public static Map<String, Object> safeUser(User u) {'
new = '''    @Autowired private com.zhixue.repository.UserRepository userRepo;

    @PutMapping("/profile")
    public ApiResponse<Map<String, Object>> updateProfile(@RequestHeader(value = "Authorization", required = false) String token,
                                                          @RequestBody Map<String, String> body) {
        if (token == null) return ApiResponse.fail("未登录");
        Long uid = com.zhixue.util.SessionStore.getUserId(token.replace("Bearer ", ""));
        User u = userRepo.findById(uid).orElse(null);
        if (u == null) return ApiResponse.fail("用户不存在");
        if (body.containsKey("name")) u.setName(body.get("name"));
        if (body.containsKey("college")) u.setCollege(body.get("college"));
        if (body.containsKey("major")) u.setMajor(body.get("major"));
        if (body.containsKey("classNo")) u.setClassNo(body.get("classNo"));
        if (body.containsKey("phone")) u.setPhone(body.get("phone"));
        if (body.containsKey("avatar")) u.setAvatar(body.get("avatar"));
        userRepo.save(u);
        return ApiResponse.ok("更新成功", safeUser(u));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@RequestHeader(value = "Authorization", required = false) String token,
                                            @RequestBody Map<String, String> body) {
        if (token == null) return ApiResponse.fail("未登录");
        Long uid = com.zhixue.util.SessionStore.getUserId(token.replace("Bearer ", ""));
        User u = userRepo.findById(uid).orElse(null);
        if (u == null) return ApiResponse.fail("用户不存在");
        String oldPwd = body.get("oldPassword");
        String newPwd = body.get("newPassword");
        if (oldPwd == null || !u.getPassword().equals(oldPwd)) return ApiResponse.fail("原密码错误");
        if (newPwd == null || newPwd.length() < 4) return ApiResponse.fail("新密码至少4位");
        u.setPassword(newPwd);
        userRepo.save(u);
        return ApiResponse.ok("密码修改成功", null);
    }

    public static Map<String, Object> safeUser(User u) {'''
content = content.replace(old, new)

old2 = '''        m.put("phone", u.getPhone());
        return m;
    }'''
new2 = '''        m.put("phone", u.getPhone());
        m.put("avatar", u.getAvatar());
        return m;
    }'''
content = content.replace(old2, new2)

open(r'src\main\java\com\zhixue\controller\AuthController.java', 'w', encoding='utf-8').write(content)
print('AuthController updated')
