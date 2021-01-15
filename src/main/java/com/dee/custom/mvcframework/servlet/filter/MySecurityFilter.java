package com.dee.custom.mvcframework.servlet.filter;

import com.dee.custom.mvcframework.annocation.MyRequestMapping;
import com.dee.custom.mvcframework.annocation.MySecurity;
import com.dee.custom.mvcframework.util.HandlerMappingUtil;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MySecurityFilter implements Filter {
    Map<String, List<String>> handlerSecurity = new HashMap<>(15);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        //获取需要扫描的包
        String packageScanPath = filterConfig.getInitParameter("filterPackageScan");
        //递归组装handlerSecurity
        doScan(packageScanPath);
    }

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

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        String[] attributeNames = servletRequest.getParameterValues("username");
        /**
         * 强转一下
         */
        HttpServletRequest httpServletRequest = (HttpServletRequest)servletRequest;
        HttpServletResponse httpServletResponse = (HttpServletResponse)servletResponse;
        String requestURI = httpServletRequest.getRequestURI();
        if(isClear(requestURI,attributeNames)){
            filterChain.doFilter(servletRequest,servletResponse);
        }else{
            httpServletResponse.getWriter().write("user: ["+StringUtils.join(attributeNames)+"]No permission");
        }
    }

    @Override
    public void destroy() {

    }

    private boolean isClear(String mapping,String[] attributeNames){
        if (!handlerSecurity.containsKey(mapping)) {
            return true;
        }
        for (String s : attributeNames) {
            if (handlerSecurity.get(mapping).contains(s)) {
                return true;
            }
        }
        return false;
    }

}
