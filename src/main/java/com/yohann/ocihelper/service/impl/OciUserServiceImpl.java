package com.yohann.ocihelper.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yohann.ocihelper.bean.entity.OciUser;
import com.yohann.ocihelper.service.IOciUserService;
import org.springframework.stereotype.Service;
import com.yohann.ocihelper.mapper.OciUserMapper;


/**
* @author Administrator
* @description 针对表【oci_user】的数据库操作Service实现
* @createDate 2024-11-12 16:44:39
*/
@Service
public class OciUserServiceImpl extends ServiceImpl<OciUserMapper, OciUser>
    implements IOciUserService {

}




