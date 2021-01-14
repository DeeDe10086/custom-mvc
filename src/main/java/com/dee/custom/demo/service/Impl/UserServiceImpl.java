package com.dee.custom.demo.service.Impl;

import com.dee.custom.mvcframework.annocation.MyService;
import com.dee.custom.demo.service.UserService;

@MyService
public class UserServiceImpl implements UserService {
    @Override
    public String getName(String name) {
        System.out.println(name);
        return name;
    }
}
