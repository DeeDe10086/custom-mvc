# 自定义简易mvc框架

- @Security注解定义权限过滤

  验证方式：

  1.`http://localhost:8080/demo/user?username=zhangsan`

  返回：[zhangsan]success from:[/demo/user]handler

  2.`http://localhost:8080/demo/user?username=lisi`

  返回：[lisi]success from:[/demo/user]handler

  3.`http://localhost:8080/demo/user?username=wangwu`

  返回：[wangwu]success from:[/demo/user]handler

  4.`http://localhost:8080/demo/user2?username=lisi`

  5.`http://localhost:8080/demo/user?username=wangwu`

- 从注解的位置可以看出

  ```java
  @MyController
  @MyRequestMapping("demo")
  @MySecurity({"wangwu"})
  public class UserController {
  
      @MyAutoWired
      UserService userService;
  
      @MyRequestMapping("user")
      @MySecurity({"zhangsan","lisi"})
      public String getUserName(HttpServletRequest request, HttpServletResponse response,String username){
          //......
          return userService.getName(username);
      }
  
      @MyRequestMapping("user2")
      public String getUserName2(HttpServletRequest request, HttpServletResponse response,String username){
          //......
          return userService.getName(username);
      }
  }
  ```

  **"zhangsan","lisi"**只能访问**getUserName**

  **"wangwu"**可以访问到所有的接口

