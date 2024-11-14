# oci-helper

> 一个基于 Oracle OCI SDK 开发的WEB端可视化甲骨文云助手，目前实现的功能有：支持添加多个租户配置、查询租户实例信息、根据多个CIDR网段更换实例公共IP、多租户枪机等功能。

## 核心功能

1. 同时管理多个租户配置信息。
2. 根据多个CIDR网段更换实例公共IP，遇到请求频繁等异常会直接忽略，不影响下一次执行，直至更换到指定IP段的IP。
3. 多租户同时开机，后台一直运行，直至开机成功。

## 如何部署

推荐docker部署，出于安全考虑建议使用Nginx反向代理配置HTTPS访问。

### 1. 新建目录
创建密钥文件存放目录，新增oci配置时只需输入密钥文件名称即可，默认会加上这个目录全路径。
```bash
mkdir -p /app/oci-helper/keys
```

### 2.  拷贝文件

复制`application.yml`、`oci-helper.db`到`/app/oci-helper`目录下，并修改`application.yml`部分配置。

### 3. docker拉取镜像直接运行

需提前安装docker环境，镜像大小约170MB，支持arm64、amd64架构。

```bash
docker run -d --name oci-helper --restart=always \
-p 8818:8818 \
-v /app/oci-helper/application.yml:/app/oci-helper/application.yml \
-v /app/oci-helper/oci-helper.db:/app/oci-helper/oci-helper.db \
-v /app/oci-helper/keys:/app/oci-helper/keys \
yohannfan/oci-helper:latest
```

## 页面展示

![image.png](https://pic1.58cdn.com.cn/nowater/webim/big/n_v26a2f3e2cd0ea4ac787723191f4f32f36.png)

![image.png](https://pic4.58cdn.com.cn/nowater/webim/big/n_v290443ddeb885445399561ab6eb1d7a09.png)

![image.png](https://pic1.58cdn.com.cn/nowater/webim/big/n_v2543323ea3d274c2ca435e2b5dcc3074f.png)

![image.png](https://pic3.58cdn.com.cn/nowater/webim/big/n_v2e3c93ccfcbd6442b8093d11fec370ee1.png)

![image.png](https://pic7.58cdn.com.cn/nowater/webim/big/n_v2a47b5866e28344e695b25a84f568ba05.png)
