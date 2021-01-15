# 自定义简易mvc框架

## 验证方式

- @Security注解定义权限过滤

  权限定义：

  **"zhangsan","lisi"**只能访问**getUserName**

  **"wangwu"**可以访问到所有的接口

  验证方式：

  1.`http://localhost:8080/demo/user?username=zhangsan`

  返回：[zhangsan]success from:[/demo/user]handler

  2.`http://localhost:8080/demo/user?username=lisi`

  返回：[lisi]success from:[/demo/user]handler

  3.`http://localhost:8080/demo/user?username=wangwu`

  返回：[wangwu]success from:[/demo/user]handler

  4.`http://localhost:8080/demo/user2?username=lisi`

  返回：user: [lisi]No permission

  5.`http://localhost:8080/demo/user?username=wangwu`

  返回：[wangwu]success from:[/demo/user]handler

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

## 解题思路

- 配置一个过滤器MySecurityFilter

- init()方法配置@Security注解的扫描路径

  ```java
  public class MySecurityFilter implements Filter {
      Map<String, List<String>> handlerSecurity = new HashMap<>(15);
  
      @Override
      public void init(FilterConfig filterConfig) throws ServletException {
          //获取需要扫描的包
          String packageScanPath = filterConfig.getInitParameter("filterPackageScan");
          //递归组装handlerSecurity
          doScan(packageScanPath);
      }
  }
  ```

  在**web.xml**中配置上这个拦截器

  ```xml
  <filter>
    <filter-name>mySecurity</filter-name>
    <filter-class>com.dee.custom.mvcframework.servlet.filter.MySecurityFilter</filter-class>
    <init-param>
      <param-name>filterPackageScan</param-name>
        <!--配置包扫描路径-->
      <param-value>com.dee.custom.demo.controller</param-value>
    </init-param>
  </filter>
  
  <filter-mapping>
    <filter-name>mySecurity</filter-name>
    <url-pattern>/*</url-pattern>
  </filter-mapping>
  ```

  然后维护一套uri与user关系的map

  `Map<String, List<String>> handlerSecurity = new HashMap<>(15);`

  让**servlet**容器每次启动的时候，去执行Filter的**init()**加载这个关系，也就是执行`doScan()`方法

  ```java
  private void doScan(String packageScanPath) {
      //获取根路径位置
      String root = this.getClass().getClassLoader().getResource("").getPath()+packageScanPath.replaceAll("\\.","/");
      File file = new File(root);
      for (File listFile : file.listFiles()) {
          if(listFile.isDirectory()){
              String path = packageScanPath+"."+file.getName();
              doScan(path);
          }else{
              String path = packageScanPath+"."+listFile.getName();
              path = path.replace(".class","");
              try {
                  String[] classSecurityUser = null;
                  String classMapping = null;
  
                  Class<?> aClass = Class.forName(path);
                  //判断类上有没有@Security注解
                  if (aClass.isAnnotationPresent(MySecurity.class)) {
                      classSecurityUser = aClass.getAnnotation(MySecurity.class).value();
                  }
                  //判断类上，有没有@MyRequestMapping注解
                  if(aClass.isAnnotationPresent(MyRequestMapping.class)){
                      classMapping = aClass.getAnnotation(MyRequestMapping.class).value();
                  }
  
                  //处理method
                  for (Method method : aClass.getMethods()) {
                      //判断有没有classSecurity，有的话，所有的methodMapping构建的时候都要加上权限
                      if (!method.isAnnotationPresent(MyRequestMapping.class)) {
                          continue;
                      }
                      String mapping = HandlerMappingUtil.filterNullMapping(classMapping,method.getAnnotation(MyRequestMapping.class).value());
                      //如果有权限,则找出权限
                      String[] value = null;
                      if (method.isAnnotationPresent(MySecurity.class)) {
                          value = method.getAnnotation(MySecurity.class).value();
                      }
                      String[] strings = ArrayUtils.addAll(classSecurityUser, value);
                      //如果handler上没有配权限，则不需要添加
                      if(ArrayUtils.isNotEmpty(strings)){
                          handlerSecurity.put(mapping,Arrays.asList(strings));
                      }
                  }
              } catch (ClassNotFoundException e) {
                  e.printStackTrace();
              }
          }
      }
  }
  ```

  在`scan()`方法中实现逻辑：class上如果有@Security注解，则class下所有的handler都能访问，如果method上有注解，则只能访问对应的方法，两者取并集

  在实现**MyHandler**这个关系类的时候，加入了一个工具类**HandlerMappingUtil**

  ```java
  public class HandlerMappingUtil {
      /**
       * 判断是不是/开头
       *
       * @param path
       */
      public static String buildFistPath(String path) {
          if (!path.startsWith("/")) {
              path = "/" + path;
          }
          return path;
      }
      /**
       * 组装mapping
       * @param mapping
       * @return
       */
      public static String filterNullMapping(String... mapping){
          StringBuilder sb = new StringBuilder();
          Arrays.stream(mapping)
                  .filter(StringUtils::isNoneEmpty)
                  .forEach(s->sb.append(buildFistPath(s)));
          return sb.toString();
      }
  }
  ```

  把uri中不是以"/"开头的转成"/"开头

- 最后在Filter的**doFilter()**方法中，获取入参**username**和**uri**，判断uri上有没有这个用户，有就继续执行，没有则输出没有权限

  ```java
  public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
      String[] attributeNames = servletRequest.getParameterValues("username");
      /**
       * 强转一下
       */
      HttpServletRequest httpServletRequest = (HttpServletRequest)servletRequest;
      HttpServletResponse httpServletResponse = (HttpServletResponse)servletResponse;
      String requestURI = httpServletRequest.getRequestURI();
      //判断有没有权限，有就放行，没有则输出没有权限
      if(isClear(requestURI,attributeNames)){
          filterChain.doFilter(servletRequest,servletResponse);
      }else{
          httpServletResponse.getWriter().write("user: ["+StringUtils.join(attributeNames)+"]No permission");
      }
  }
  ```

## 解题总结

- 为什么要用Filter

  其实在解题的时候，uri和user的关系可以维护在**MyDispatcherServlet**这个servlet里面，然后在`doPost()`方法中，invoke方法之前，去匹配uri和user，然后控制要不要执行invoke

  ```java
  myHandler.getMethod().invoke(myHandler.getController(), args);
  ```

  但是考虑到，这样做的话，权限和请求就耦合在一起了，虽然自定义一个Filter会麻烦一点，要重新扫一次包，但是做到了解耦，把权限过滤和原本的代码分开，综合考虑还是选择了Filter

- 代码中，循环的部分用了stream，增加了循环次数，浪费了性能

  为什么要用stream去写呢，主要是想多使用一下stream，练习一下，增加熟练度

- 代码中出现别名

  ```java
  if (req.getClass().getSimpleName().equals("RequestFacade")) {
      httpServletRequest = "HttpServletRequest";
  } else {
      httpServletRequest = req.getClass().getSimpleName();
  }
  if (resp.getClass().getSimpleName().equals("ResponseFacade")) {
      httpServletResponse = "HttpServletResponse";
  } else {
      httpServletResponse = resp.getClass().getSimpleName();
  }
  ```

  代码中出现了**RequestFacade**和**ResponseFacade**转成**HttpServletRequest**和**HttpServletResponse**的代码，在请求进来的时候，接口中进来的是**RequestFacade**和**ResponseFacade**，查了一下，是**HttpServletRequest**和**HttpServletResponse**的包装，暂时没找到原因，查了一下，是tomcat对他们的包装，暂时没有解决方案，就先转了一下