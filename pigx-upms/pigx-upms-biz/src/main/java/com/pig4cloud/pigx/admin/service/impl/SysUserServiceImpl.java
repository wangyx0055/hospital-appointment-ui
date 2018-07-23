/*
 *
 *      Copyright (c) 2018-2025, lengleng All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice,
 *  this list of conditions and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright
 *  notice, this list of conditions and the following disclaimer in the
 *  documentation and/or other materials provided with the distribution.
 *  Neither the name of the pig4cloud.com developer nor the names of its
 *  contributors may be used to endorse or promote products derived from
 *  this software without specific prior written permission.
 *  Author: lengleng (wangiegie@gmail.com)
 *
 */

package com.pig4cloud.pigx.admin.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.plugins.Page;
import com.baomidou.mybatisplus.service.impl.ServiceImpl;
import com.pig4cloud.pigx.admin.api.dto.Query;
import com.pig4cloud.pigx.admin.api.dto.UserDTO;
import com.pig4cloud.pigx.admin.api.dto.UserInfo;
import com.pig4cloud.pigx.admin.api.entity.SysRole;
import com.pig4cloud.pigx.admin.api.entity.SysUser;
import com.pig4cloud.pigx.admin.api.entity.SysUserRole;
import com.pig4cloud.pigx.admin.api.vo.MenuVO;
import com.pig4cloud.pigx.admin.api.vo.UserVO;
import com.pig4cloud.pigx.admin.mapper.SysUserMapper;
import com.pig4cloud.pigx.admin.service.SysMenuService;
import com.pig4cloud.pigx.admin.service.SysRoleService;
import com.pig4cloud.pigx.admin.service.SysUserRoleService;
import com.pig4cloud.pigx.admin.service.SysUserService;
import com.pig4cloud.pigx.common.core.util.R;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * @author lengleng
 * @date 2017/10/31
 */
@Slf4j
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {
	private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder();
	@Autowired
	private SysMenuService sysMenuService;
	@Autowired
	private SysUserMapper sysUserMapper;
	@Autowired
	private SysRoleService sysRoleService;
	@Autowired
	private SysUserRoleService sysUserRoleService;

	/**
	 * 通过用户名查用户的全部信息
	 *
	 * @param username 用户名
	 * @return
	 */
	@Override
	@Cacheable(value = "user_details", key = "#username")
	public UserInfo findUserInfo(String username) {
		SysUser condition = new SysUser();
		condition.setUsername(username);
		SysUser sysUser = this.selectOne(new EntityWrapper<>(condition));
		if (sysUser == null) {
			return null;
		}

		UserInfo userInfo = new UserInfo();
		userInfo.setSysUser(sysUser);
		//设置角色列表
		List<SysRole> roleList = sysRoleService.findRolesByUserId(sysUser.getUserId());
		List<String> roleCodes = new ArrayList<>();
		if (CollUtil.isNotEmpty(roleList)) {
			roleList.forEach(sysRole -> roleCodes.add(sysRole.getRoleCode()));
		}
		userInfo.setRoles(ArrayUtil.toArray(roleCodes, String.class));

		//设置权限列表（menu.permission）
		Set<MenuVO> menuVoSet = new HashSet<>();
		for (String role : roleCodes) {
			List<MenuVO> menuVos = sysMenuService.findMenuByRoleCode(role);
			menuVoSet.addAll(menuVos);
		}
		Set<String> permissions = new HashSet<>();
		for (MenuVO menuVo : menuVoSet) {
			if (StringUtils.isNotEmpty(menuVo.getPermission())) {
				String permission = menuVo.getPermission();
				permissions.add(permission);
			}
		}
		userInfo.setPermissions(ArrayUtil.toArray(permissions, String.class));
		return userInfo;
	}

	@Override
	public Page selectWithRolePage(Query query) {
		Object username = query.getCondition().get("username");
		query.setRecords(sysUserMapper.selectUserVoPage(query, username));
		return query;
	}

	/**
	 * 通过ID查询用户信息
	 *
	 * @param id 用户ID
	 * @return 用户信息
	 */
	@Override
	public UserVO selectUserVoById(Integer id) {
		return sysUserMapper.selectUserVoById(id);
	}

	/**
	 * 删除用户
	 *
	 * @param sysUser 用户
	 * @return Boolean
	 */
	@Override
	@CacheEvict(value = "user_details", key = "#sysUser.username")
	public Boolean deleteUserById(SysUser sysUser) {
		sysUserRoleService.deleteByUserId(sysUser.getUserId());
		this.deleteById(sysUser.getUserId());
		return Boolean.TRUE;
	}

	@Override
	@CacheEvict(value = "user_details", key = "#username")
	public R<Boolean> updateUserInfo(UserDTO userDto, String username) {
		UserVO userVO = sysUserMapper.selectUserVoByUsername(username);
		SysUser sysUser = new SysUser();
		if (StrUtil.isNotBlank(userDto.getPassword())
			&& StrUtil.isNotBlank(userDto.getNewpassword1())) {
			if (ENCODER.matches(userDto.getPassword(), userVO.getPassword())) {
				sysUser.setPassword(ENCODER.encode(userDto.getNewpassword1()));
			} else {
				log.warn("原密码错误，修改密码失败:{}", username);
				return new R<>(Boolean.FALSE, "原密码错误，修改失败");
			}
		}
		sysUser.setPhone(userDto.getPhone());
		sysUser.setUserId(userVO.getUserId());
		sysUser.setAvatar(userDto.getAvatar());
		return new R<>(this.updateById(sysUser));
	}

	@Override
	@CacheEvict(value = "user_details", key = "#username")
	public Boolean updateUser(UserDTO userDto, String username) {
		SysUser sysUser = new SysUser();
		BeanUtils.copyProperties(userDto, sysUser);
		sysUser.setUpdateTime(new Date());
		this.updateById(sysUser);

		SysUserRole condition = new SysUserRole();
		condition.setUserId(userDto.getUserId());
		sysUserRoleService.delete(new EntityWrapper<>(condition));
		userDto.getRole().forEach(roleId -> {
			SysUserRole userRole = new SysUserRole();
			userRole.setUserId(sysUser.getUserId());
			userRole.setRoleId(roleId);
			userRole.insert();
		});
		return Boolean.TRUE;
	}
}
