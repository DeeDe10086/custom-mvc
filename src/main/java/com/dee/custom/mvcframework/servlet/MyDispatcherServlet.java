package com.dee.custom.mvcframework.servlet;

import com.dee.custom.mvcframework.annocation.MyAutoWired;
import com.dee.custom.mvcframework.annocation.MyController;
import com.dee.custom.mvcframework.annocation.MyRequestMapping;
import com.dee.custom.mvcframework.annocation.MyService;
import com.dee.custom.mvcframework.dto.MyHandler;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MyDispatcherServlet extends HttpServlet {

    private Properties properties;

    private final List<String> packagePaths = new ArrayList<>();

    private final Map<String, Object> beans = new HashMap<>();

    private List<MyHandler> handlers = new ArrayList<>();

    @Override
    public void init(ServletConfig config) throws ServletException {
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
        super.doGet(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doPost(req, resp);
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
                        packagePath = path + "." + s.getName();
                        doScan(packagePath);
                    } else if (s.getName().endsWith(".class")) {
                        packagePath = path + "." + s.getName().replaceAll(".class", "");
                    }
                    return packagePath;
                })
                .filter(s -> !s.isEmpty())
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
                Object targetObject = sourceClass.newInstance();

                //区分类型，看下是@controller注解还是@service注解
                if (sourceClass.isAnnotationPresent(MyController.class)) {
                    //首字母小写
                    String s = lowerFirst(classSimpleName);
                    beans.put(s, targetObject);
                } else if (sourceClass.isAnnotationPresent(MyService.class)) {
                    //判断service注解是否有value，有value的话，用value作为bean的名称
                    MyService annotation = sourceClass.getAnnotation(MyService.class);
                    Optional<String> alias = Optional.ofNullable(annotation.value().trim());
                    //判断是否有传别名，有的话用别名，没有的话用首字母大写
                    beans.put(alias.orElseGet(() -> lowerFirst(classSimpleName)), targetObject);

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
            Arrays.stream(value.getClass().getFields())
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
                    String finalRootPath = rootPath;
                    List<MyHandler> collect = Arrays.stream(s.getValue().getClass().getMethods())
                            .filter(method -> method.isAnnotationPresent(MyRequestMapping.class))
                            .map(method -> {
                                Parameter[] parameters = method.getParameters();
                                String methodPath = method.getAnnotation(MyRequestMapping.class).value();
                                Map<String, Integer> map = new HashMap<>(parameters.length);
                                for (int i = 0; i < parameters.length; i++) {
                                    map.put(parameters[0].getName(),i);
                                }
                                MyHandler myHandler = new MyHandler(s.getValue(),method,Pattern.compile(sb.append(finalRootPath).append(methodPath).toString()),map);
                                return myHandler;
                            }).collect(Collectors.toList());
                    return collect;
                })
                .flatMap(List::stream)
                .collect(Collectors.toList());
        handlers.addAll(myHandlers);
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

}
