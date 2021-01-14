package com.dee.custom.mvcframework.servlet;

import com.dee.custom.mvcframework.annocation.MyAutoWired;
import com.dee.custom.mvcframework.annocation.MyController;
import com.dee.custom.mvcframework.annocation.MyRequestMapping;
import com.dee.custom.mvcframework.annocation.MyService;
import com.dee.custom.demo.dto.MyHandler;
import com.dee.custom.mvcframework.util.HandlerMappingUtil;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MyDispatcherServlet extends HttpServlet {

    private Properties properties = new Properties();

    private final List<String> packagePaths = new ArrayList<>();

    private final Map<String, Object> beans = new HashMap<>();

    private List<MyHandler> handlers = new ArrayList<>();

    @Override
    public void init(ServletConfig config) throws ServletException {
        System.out.println("初始化过滤器");
        //加载配置文件
        String contextConfigLocation = config.getInitParameter("contextConfigLocation");
        doLoadConfig(contextConfigLocation);
        //扫描相关的类，扫描注解
        doScan(properties.getProperty("scanPackage"));
        //初始化bean对象
        doInstance();
        //实现依赖注入
        doAutoWired();
        //构建一个handlerMapping处理器映射器，讲配置好的url和Method建立映射关系
        initHandlerMapping();
        //等待请求进入，处理请求
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        MyHandler myHandler = getMyHandler(req);

        if (myHandler == null) {
            resp.getWriter().write("404 not found");
            return;
        }

        //获取前端的入参
        Map<String, String[]> parameterMap = req.getParameterMap();
        //需要调用方法的参数
        Object[] args = new Object[myHandler.getParamIndexMapping().size()];
        //当前获取到的handler存的params位置
        Map<String, Integer> paramIndexMapping = myHandler.getParamIndexMapping();

        //遍历parameterMap所存的参数
        for (Map.Entry<String, String[]> stringEntry : parameterMap.entrySet()) {
            if (!paramIndexMapping.containsKey(stringEntry.getKey())) {
                continue;
            }
            args[paramIndexMapping.get(stringEntry.getKey())] = StringUtils.join(stringEntry.getValue());
        }
        //把HttpServletRequest，HttpServletResponse放入args
        String httpServletRequest = "";
        String httpServletResponse = "";
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

        if (paramIndexMapping.containsKey(httpServletRequest)) {
            args[paramIndexMapping.get(httpServletRequest)] = req;
        }
        if (paramIndexMapping.containsKey(httpServletResponse)) {
            args[paramIndexMapping.get(httpServletResponse)] = resp;
        }
        try {
            myHandler.getMethod().invoke(myHandler.getController(), args);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private void doLoadConfig(String contextConfigLocation) {
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            properties.load(resourceAsStream);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void doScan(String scanPackage) {
        //获取包的全路径
        String path = Thread.currentThread().getContextClassLoader().getResource("").getPath() + scanPackage.replaceAll("\\.", "/");
        //通过File处理文件
        File file = new File(path);
        //把收集到的所有包，放到packagePath里面
        List<String> collect = Arrays.stream(file.listFiles())
                .map(s -> {
                    String packagePath = null;
                    if (s.isDirectory()) {
                        packagePath = scanPackage + "." + s.getName();
                        doScan(packagePath);
                        packagePath = "";
                    } else if (s.getName().endsWith(".class")) {
                        packagePath = scanPackage + "." + s.getName().replaceAll(".class", "");
                    }
                    return packagePath;
                })
                .filter(s -> StringUtils.isNoneEmpty(s))
                .collect(Collectors.toList());
        packagePaths.addAll(collect);
    }

    private void doInstance() {
        if (packagePaths.size() == 0) {
            return;
        }

        /**
         * 首先实例化类
         */
        try {
            for (String packagePath : packagePaths) {
                Class<?> sourceClass = Class.forName(packagePath);
                String classSimpleName = sourceClass.getSimpleName();

                //区分类型，看下是@controller注解还是@service注解
                if (sourceClass.isAnnotationPresent(MyController.class)) {
                    Object targetObject = sourceClass.newInstance();
                    //首字母小写
                    String s = lowerFirst(classSimpleName);
                    beans.put(s, targetObject);
                } else if (sourceClass.isAnnotationPresent(MyService.class)) {
                    String alias;
                    Object targetObject = sourceClass.newInstance();
                    //判断service注解是否有value，有value的话，用value作为bean的名称
                    MyService annotation = sourceClass.getAnnotation(MyService.class);
                    //判断是否有传别名，有的话用别名，没有的话用首字母大写
                    if (StringUtils.isNoneEmpty(annotation.value())) {
                        alias = annotation.value().trim();
                    } else {
                        alias = lowerFirst(classSimpleName);
                    }
                    beans.put(alias, targetObject);

                    //判断类是否有继承接口，如果有，则按照接口名再存一份bean
                    Class<?>[] interfaces = sourceClass.getInterfaces();
                    if (interfaces.length == 0) {
                        return;
                    }
                    for (Class<?> anInterface : interfaces) {
                        String name = anInterface.getName();
                        beans.put(name, targetObject);
                    }
                } else {
                    continue;
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

    }

    private void doAutoWired() {
        if (beans.isEmpty()) {
            return;
        }
        //遍历beans
        for (Map.Entry<String, Object> stringObjectEntry : beans.entrySet()) {
            //找到带有@MyAutoWired注解的属性
            Object value = stringObjectEntry.getValue();
            Arrays.stream(value.getClass().getDeclaredFields())
                    .filter(s -> s.isAnnotationPresent(MyAutoWired.class))
                    //从beans里面获取属bean然后赋值给属性
                    .forEach(field -> {
                        try {
                            //开启赋值
                            field.setAccessible(true);
                            field.set(value, beans.get(field.getType().getName()));
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    });
            ;

        }

    }

    private void initHandlerMapping() {
        if (beans.isEmpty()) {
            return;
        }
        //判断类上有没有MyController注解
        List<MyHandler> myHandlers = beans.entrySet().stream()
                .filter(s -> s.getValue().getClass().isAnnotationPresent(MyController.class))
                .map(s -> {
                    //判断类上面有没有@MyRequestMapping注解
                    StringBuilder sb = new StringBuilder();
                    String rootPath = null;
                    if (s.getValue().getClass().isAnnotationPresent(MyRequestMapping.class)) {
                        Optional<String> myRequestMapping = Optional.of(s.getValue().getClass().getAnnotation(MyRequestMapping.class).value());
                        rootPath = myRequestMapping.orElse("");
                    }
                    //判断方法上有没有@RequestMapping注解
                    String finalRootPath = HandlerMappingUtil.buildFistPath(rootPath);
                    List<MyHandler> collect = Arrays.stream(s.getValue().getClass().getMethods())
                            .filter(method -> method.isAnnotationPresent(MyRequestMapping.class))
                            .map(method -> {
                                Parameter[] parameters = method.getParameters();
                                String methodPath = HandlerMappingUtil.buildFistPath(method.getAnnotation(MyRequestMapping.class).value());
                                Map<String, Integer> map = new HashMap<>(parameters.length);
                                for (int i = 0; i < parameters.length; i++) {
                                    if (parameters[i].getType() == HttpServletRequest.class || parameters[i].getType() == HttpServletResponse.class) {
                                        map.put(parameters[i].getType().getSimpleName(), i);
                                    } else {
                                        map.put(parameters[i].getName(), i);
                                    }
                                }
                                MyHandler myHandler = new MyHandler(s.getValue(), method, Pattern.compile(sb.append(finalRootPath).append(methodPath).toString()), map);
                                return myHandler;
                            }).collect(Collectors.toList());
                    return collect;
                })
                .flatMap(List::stream)
                .collect(Collectors.toList());
        handlers.addAll(myHandlers);
    }

    /**
     * 获取自定义handler
     *
     * @param req
     */
    private MyHandler getMyHandler(HttpServletRequest req) {
        if (handlers.isEmpty()) {
            return null;
        }
        Optional<MyHandler> first = handlers.stream()
                .filter(s -> s.getPattern().matcher(req.getRequestURI()).matches())
                .findFirst();
        return first.orElse(null);
    }

    /**
     * 首字母小写
     *
     * @param str
     */
    private static String lowerFirst(String str) {
        if (str.isEmpty()) {
            return "";
        }
        char[] chars = str.toCharArray();
        if (chars[0] >= 'A' && chars[0] <= 'Z') {
            chars[0] += 32;
        }
        return String.valueOf(chars);
    }

    /**
     * 构建invoke入参
     *
     * @param paramIndexMapping
     * @return
     */
    private Object[] getArgs(Map<String, Integer> paramIndexMapping) {
        Object[] args = new Object[paramIndexMapping.size()];
        return args;
    }


}
