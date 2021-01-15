package com.dee.custom.demo.controller;

import com.dee.custom.mvcframework.annocation.MyAutoWired;
import com.dee.custom.mvcframework.annocation.MyController;
import com.dee.custom.mvcframework.annocation.MyRequestMapping;
import com.dee.custom.demo.service.UserService;
import com.dee.custom.mvcframework.annocation.MySecurity;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@MyController
@MyRequestMapping("demo")
@MySecurity({"wangwu"})
public class UserController {

    @MyAutoWired
    UserService userService;

    @MyRequestMapping("user")
    @MySecurity({"zhangsan","lisi"})
    public String getUserName(HttpServletRequest request, HttpServletResponse response,String username){
        try {
            response.getWriter().write("["+username+"]success from:["+request.getRequestURI()+"]handler");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return userService.getName(username);
    }

    @MyRequestMapping("user2")
    public String getUserName2(HttpServletRequest request, HttpServletResponse response,String username){
        try {
            response.getWriter().write("["+username+"]success from:["+request.getRequestURI()+"]handler");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return userService.getName(username);
    }
}
