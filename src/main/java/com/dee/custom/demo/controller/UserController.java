package com.dee.custom.demo.controller;

import com.dee.custom.mvcframework.annocation.MyAutoWired;
import com.dee.custom.mvcframework.annocation.MyController;
import com.dee.custom.mvcframework.annocation.MyRequestMapping;
import com.dee.custom.demo.service.UserService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@MyController
@MyRequestMapping("demo")
public class UserController {

    @MyAutoWired
    UserService userService;

    @MyRequestMapping("user")
    public String getUserName(HttpServletRequest request, HttpServletResponse response,String name){
        return userService.getName(name);
    }
}
