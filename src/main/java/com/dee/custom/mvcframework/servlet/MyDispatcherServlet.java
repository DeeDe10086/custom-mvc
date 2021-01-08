package com.dee.custom.mvcframework.servlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class MyDispatcherServlet extends HttpServlet {

    @Override
    public void init() throws ServletException {
        //加载配置文件
        //扫描相关的类，扫描注解
        //初始化bean对象
        //实现依赖注入
        //构建一个handlerMapping处理器映射器，讲配置好的url和Method建立映射关系
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

}
